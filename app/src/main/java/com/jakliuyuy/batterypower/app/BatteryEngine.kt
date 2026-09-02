package com.jakliuyuy.batterypower.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.jakliuyuy.batterypower.core.battery.BatteryRepository
import com.jakliuyuy.batterypower.core.battery.ReaderFlags
import com.jakliuyuy.batterypower.core.config.ConfigStore
import com.jakliuyuy.batterypower.core.log.BLog
import com.jakliuyuy.batterypower.core.model.BatterySnapshot
import com.jakliuyuy.batterypower.core.model.DeviceProfile
import com.jakliuyuy.batterypower.core.model.ProbeResult
import com.jakliuyuy.batterypower.core.root.PersistentSuShell
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Shared battery sampling engine for the app process (spec sections 72, 90,
 * 135.3, 154).
 *
 * Threading:
 *  - one single-threaded worker owns the su shell and sysfs reads
 *  - listeners are invoked on the main thread with an immutable snapshot
 *  - sampling interval is decoupled from each view's display refresh interval
 */
class BatteryEngine private constructor(private val appContext: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<(BatterySnapshot) -> Unit>()

    private val worker: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "bpo-battery").apply { isDaemon = true }
    }
    private val probeExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "bpo-probe").apply { isDaemon = true }
    }

    private val shell = PersistentSuShell("Root")
    private val profile = DeviceProfile.defaultProfile()

    private var repository: BatteryRepository? = null

    @Volatile
    private var snapshot: BatterySnapshot = BatterySnapshot.empty()

    private var sampleFuture: ScheduledFuture<*>? = null
    private var refCount = 0
    private var sampleIntervalMs = 1000L
    private val closed = AtomicBoolean(false)

    // ------------------------------------------------------------ lifecycle

    @Synchronized
    fun acquire() {
        refCount++
        if (refCount == 1) startSampling()
    }

    @Synchronized
    fun release() {
        refCount = (refCount - 1).coerceAtLeast(0)
        if (refCount == 0) stopSampling()
    }

    fun lastSnapshot(): BatterySnapshot = snapshot

    fun addListener(listener: (BatterySnapshot) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (BatterySnapshot) -> Unit) {
        listeners.remove(listener)
    }

    fun refreshSampleInterval() {
        val config = ConfigStore.get(appContext).get()
        val fastest = minOf(
            config.overlay.refreshMs,
            config.statusBar.refreshMs
        )
        sampleIntervalMs = when {
            fastest <= 500L -> 500L
            fastest <= 1000L -> 1000L
            else -> 1000L
        }
        if (refCount > 0) {
            synchronized(this) {
                stopSamplingLocked()
                startSamplingLocked()
            }
        }
    }

    /** One-shot sample on the worker thread, delivered on the main thread. */
    fun sampleOnce(callback: ((BatterySnapshot) -> Unit)? = null) {
        if (closed.get()) return
        probeExecutor.execute {
            val result = safeSample()
            mainHandler.post {
                callback?.invoke(result)
            }
        }
    }

    fun probeAsync(callback: (ProbeResult) -> Unit) {
        if (closed.get()) return
        probeExecutor.execute {
            val result = try {
                getRepository().probe()
            } catch (t: Throwable) {
                BLog.throttledError("Battery", "probe", "probe failed: ${t.message}")
                ProbeResult(ok = false, message = t.message)
            }
            mainHandler.post { callback(result) }
        }
    }

    fun shellAlive(): Boolean = shell.isAlive()

    /** Runs an arbitrary root command on the worker thread (e.g. restarting SystemUI). */
    fun runRootCommandAsync(command: String, callback: (PersistentSuShell.Result) -> Unit) {
        if (closed.get()) return
        probeExecutor.execute {
            val result = try {
                shell.execute(command, 5_000L)
            } catch (t: Throwable) {
                BLog.throttledError("Root", "exec", "root command failed", t)
                PersistentSuShell.Result(
                    false,
                    "",
                    null,
                    com.jakliuyuy.batterypower.core.model.RootError.COMMAND_FAILED
                )
            }
            mainHandler.post { callback(result) }
        }
    }

    fun close() {
        if (closed.compareAndSet(false, true)) {
            synchronized(this) { stopSamplingLocked() }
            shell.close()
            probeExecutor.shutdownNow()
            worker.shutdownNow()
        }
    }

    // -------------------------------------------------------------- internals

    private fun startSampling() {
        startSamplingLocked()
    }

    private fun startSamplingLocked() {
        if (sampleFuture != null) return
        refreshIntervalFromConfig()
        BLog.i("Battery", "sampling started at ${sampleIntervalMs}ms")
        sampleFuture = worker.scheduleWithFixedDelay(
            {
                try {
                    val result = safeSample()
                    mainHandler.post { dispatch(result) }
                } catch (t: Throwable) {
                    BLog.throttledError("Battery", "sample-loop", "sampling failed", t)
                }
            },
            0L,
            sampleIntervalMs,
            TimeUnit.MILLISECONDS
        )
    }

    private fun stopSampling() {
        stopSamplingLocked()
    }

    private fun stopSamplingLocked() {
        sampleFuture?.cancel(false)
        sampleFuture = null
        BLog.i("Battery", "sampling stopped")
    }

    private fun refreshIntervalFromConfig() {
        try {
            val config = ConfigStore.get(appContext).get()
            val fastest = minOf(config.overlay.refreshMs, config.statusBar.refreshMs)
            sampleIntervalMs = if (fastest <= 500L) 500L else 1000L
        } catch (t: Throwable) {
            sampleIntervalMs = 1000L
        }
    }

    private fun safeSample(): BatterySnapshot {
        return try {
            getRepository().sample()
        } catch (t: Throwable) {
            BLog.throttledError("Battery", "sample", "sample failed", t)
            BatterySnapshot.empty()
        }
    }

    @Synchronized
    private fun getRepository(): BatteryRepository {
        repository?.let { return it }
        val config = try {
            ConfigStore.get(appContext).get()
        } catch (t: Throwable) {
            null
        }
        val flags = ReaderFlags(
            enableRootReader = config?.flags?.enableRootReader ?: true,
            enableBatteryManagerFallback = config?.flags?.enableBatteryManagerFallback ?: true
        )
        val repo = BatteryRepository(appContext, shell, profile, flags)
        repository = repo
        return repo
    }

    private fun dispatch(result: BatterySnapshot) {
        snapshot = result
        for (listener in listeners) {
            try {
                listener(result)
            } catch (t: Throwable) {
                BLog.w("Battery", "listener failed: ${t.message}")
            }
        }
    }

    companion object {
        @Volatile
        private var instance: BatteryEngine? = null

        fun get(context: Context): BatteryEngine {
            return instance ?: synchronized(this) {
                instance ?: BatteryEngine(context.applicationContext).also { instance = it }
            }
        }
    }
}
