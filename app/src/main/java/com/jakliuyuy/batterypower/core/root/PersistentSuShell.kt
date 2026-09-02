package com.jakliuyuy.batterypower.core.root

import com.jakliuyuy.batterypower.core.log.BLog
import com.jakliuyuy.batterypower.core.model.RootError
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * A single long-lived `su` shell that is reused for every command.
 *
 * Spec sections 55-58, 147:
 *  - never fork a new su per refresh
 *  - command completion is detected with a unique marker, never Thread.sleep()
 *  - every command has a timeout
 *  - broken shells are rebuilt with backoff, never in a tight loop
 *
 * All public methods are blocking: callers MUST invoke them from a worker thread.
 */
class PersistentSuShell(
    private val subTag: String = "Root",
    private val startupTimeoutMs: Long = 5_000L,
    private val commandTimeoutMs: Long = 5_000L
) {

    enum class State {
        DISCONNECTED, CONNECTING, READY, BUSY, TIMEOUT, ERROR, CLOSING, CLOSED
    }

    data class Result(
        val success: Boolean,
        val output: String,
        val exitCode: Int?,
        val error: RootError?
    ) {
        companion object {
            fun failure(error: RootError) = Result(false, "", null, error)
        }
    }

    private val lock = Any()
    private val nonce = AtomicInteger(0)

    @Volatile
    var state: State = State.DISCONNECTED
        private set

    @Volatile
    private var process: Process? = null

    @Volatile
    private var writer: OutputStream? = null

    @Volatile
    private var readerThread: Thread? = null

    @Volatile
    private var errorThread: Thread? = null

    private val lines = LinkedBlockingQueue<String>()

    private var consecutiveFailures = 0
    private var lastStartAttemptMs = 0L
    private var restartBackoffMs = 1_000L

    val consecutiveFailureCount: Int
        get() = synchronized(lock) { consecutiveFailures }

    fun isAlive(): Boolean {
        val p = process
        if (p == null || !p.isAlive) return false
        return state == State.READY || state == State.BUSY
    }

    /** Starts the shell. Safe to call repeatedly. */
    fun start(): Boolean {
        synchronized(lock) {
            if (isAlive()) return true
            val now = System.currentTimeMillis()
            if (now - lastStartAttemptMs < restartBackoffMs) {
                return false
            }
            lastStartAttemptMs = now
            state = State.CONNECTING
            closeInternal()
            lines.clear()

            return try {
                val pb = ProcessBuilder("su")
                    .redirectErrorStream(false)
                val p = pb.start()
                process = p
                writer = p.outputStream

                startReader(p)
                startErrorDrain(p)

                // Verify the shell is usable and that we really are root.
                val marker = nextMarker()
                writeLine("id")
                writeLine("echo $marker$?")
                val timeoutAt = System.currentTimeMillis() + startupTimeoutMs
                var sawMarker = false
                var sawRoot = false
                val sb = StringBuilder()
                while (System.currentTimeMillis() < timeoutAt) {
                    val line = lines.poll(200, TimeUnit.MILLISECONDS) ?: continue
                    if (line.startsWith(marker)) {
                        sawMarker = true
                        break
                    }
                    sb.append(line).append('\n')
                    if (line.contains("uid=0")) sawRoot = true
                }
                if (!sawMarker) {
                    state = State.TIMEOUT
                    consecutiveFailures++
                    bumpBackoff()
                    closeInternal()
                    BLog.throttledError(subTag, "start-timeout", "su shell startup timed out")
                    return false
                }
                if (!sawRoot) {
                    state = State.ERROR
                    consecutiveFailures++
                    bumpBackoff()
                    closeInternal()
                    BLog.throttledError(subTag, "start-denied", "su did not report uid=0: ${sb.trim()}")
                    return false
                }
                state = State.READY
                consecutiveFailures = 0
                restartBackoffMs = 1_000L
                BLog.i(subTag, "su shell ready")
                true
            } catch (t: Throwable) {
                state = State.ERROR
                consecutiveFailures++
                bumpBackoff()
                closeInternal()
                BLog.throttledError(subTag, "start-failed", "su shell start failed: ${t.message}")
                false
            }
        }
    }

    /** Executes [command] and waits for the unique marker. Blocking. */
    fun execute(command: String, timeoutMs: Long = commandTimeoutMs): Result {
        synchronized(lock) {
            if (!isAlive() && !start()) {
                return Result.failure(
                    if (consecutiveFailures > 1) RootError.ROOT_DENIED else RootError.SHELL_START_FAILED
                )
            }
            val p = process
            if (p == null || !p.isAlive) {
                state = State.ERROR
                return Result.failure(RootError.SHELL_START_FAILED)
            }

            state = State.BUSY
            lines.clear()

            val marker = nextMarker()
            return try {
                writeLine(command)
                writeLine("echo \"$marker$?\"")

                val deadline = System.currentTimeMillis() + timeoutMs
                val sb = StringBuilder()
                var exitCode: Int? = null
                while (System.currentTimeMillis() < deadline) {
                    val remaining = deadline - System.currentTimeMillis()
                    if (remaining <= 0) break
                    val line = lines.poll(remaining.coerceAtMost(300), TimeUnit.MILLISECONDS)
                        ?: continue
                    if (line.startsWith(marker)) {
                        exitCode = line.substring(marker.length).trim().toIntOrNull()
                        break
                    }
                    if (sb.isNotEmpty()) sb.append('\n')
                    sb.append(line)
                }

                if (exitCode == null) {
                    // Timeout: the shell may be wedged. Rebuild it.
                    state = State.TIMEOUT
                    consecutiveFailures++
                    bumpBackoff()
                    closeInternal()
                    BLog.throttledError(subTag, "cmd-timeout", "command timed out: ${shorten(command)}")
                    Result.failure(RootError.COMMAND_TIMEOUT)
                } else {
                    state = State.READY
                    consecutiveFailures = 0
                    restartBackoffMs = 1_000L
                    Result(true, sb.toString(), exitCode, null)
                }
            } catch (t: Throwable) {
                state = State.ERROR
                consecutiveFailures++
                bumpBackoff()
                closeInternal()
                BLog.throttledError(subTag, "cmd-failed", "command failed: ${t.message}")
                Result.failure(RootError.BROKEN_PIPE)
            }
        }
    }

    fun restart(): Boolean {
        synchronized(lock) {
            closeInternal()
            restartBackoffMs = 0L
            lastStartAttemptMs = 0L
            return start()
        }
    }

    fun close() {
        synchronized(lock) {
            state = State.CLOSING
            closeInternal()
            state = State.CLOSED
        }
    }

    // ---------------------------------------------------------------- internals

    private fun nextMarker(): String = "KOMARI_DONE_" + nonce.incrementAndGet() + "_"

    private fun writeLine(line: String) {
        val w = writer ?: throw java.io.IOException("shell writer unavailable")
        w.write((line + "\n").toByteArray(StandardCharsets.UTF_8))
        w.flush()
    }

    private fun startReader(p: Process) {
        val t = Thread({
            var reader: BufferedReader? = null
            try {
                reader = BufferedReader(InputStreamReader(p.inputStream, StandardCharsets.UTF_8))
                while (true) {
                    val line = reader.readLine()
                    if (line == null) break
                    try {
                        lines.put(line)
                    } catch (ie: InterruptedException) {
                        break
                    }
                }
            } catch (t: Throwable) {
                BLog.d(subTag, "reader ended: ${t.message}")
            } finally {
                try {
                    reader?.close()
                } catch (ignored: Throwable) {
                }
            }
        }, "bpo-su-reader")
        t.isDaemon = true
        t.start()
        readerThread = t
    }

    private fun startErrorDrain(p: Process) {
        val t = Thread({
            var reader: BufferedReader? = null
            try {
                reader = BufferedReader(InputStreamReader(p.errorStream, StandardCharsets.UTF_8))
                while (true) {
                    val line = reader.readLine() ?: break
                    // Keep stderr out of the command output but record nothing per line
                    // (spec 101: never flood the log at 1 Hz).
                    BLog.v(subTag, "stderr: $line")
                }
            } catch (t: Throwable) {
                // ignore
            } finally {
                try {
                    reader?.close()
                } catch (ignored: Throwable) {
                }
            }
        }, "bpo-su-err")
        t.isDaemon = true
        t.start()
        errorThread = t
    }

    private fun closeInternal() {
        try {
            writer?.apply {
                write("exit\n".toByteArray(StandardCharsets.UTF_8))
                flush()
            }
        } catch (ignored: Throwable) {
        }
        writer = null
        try {
            readerThread?.interrupt()
        } catch (ignored: Throwable) {
        }
        readerThread = null
        try {
            errorThread?.interrupt()
        } catch (ignored: Throwable) {
        }
        errorThread = null
        try {
            val p = process
            if (p != null) {
                try {
                    p.inputStream.close()
                } catch (ignored: Throwable) {
                }
                try {
                    p.errorStream.close()
                } catch (ignored: Throwable) {
                }
                p.destroy()
            }
        } catch (ignored: Throwable) {
        }
        process = null
        lines.clear()
    }

    private fun bumpBackoff() {
        restartBackoffMs = (restartBackoffMs * 2).coerceAtMost(30_000L)
    }

    private fun shorten(cmd: String): String =
        if (cmd.length > 120) cmd.substring(0, 120) + "..." else cmd
}
