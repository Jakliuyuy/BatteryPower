package com.jakliuyuy.batterypower.battery

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 常驻 su shell 会话。
 *
 * 避坑 3：不要每 1 秒新起一个 su 进程。KernelSU 会对频繁提权做限流，
 * 实测 2s 超时根本不够（至少 5s），表现为 root 读取失败、悬浮窗恒显示 0.00W。
 * 正确做法：只启动一次 su，之后通过 stdin 写命令、按 marker 从 stdout 切分结果。
 *
 * 命令协议：
 *   写入： <cmd>\n echo <marker>\n
 *   读取： 逐行读到含 marker 的行为止，之前的内容即为输出
 */
class RootHelper {

    private var process: Process? = null
    private var writer: OutputStreamWriter? = null
    private var reader: BufferedReader? = null
    private val started = AtomicBoolean(false)

    /** 是否已成功建立 root 会话 */
    val isAvailable: Boolean get() = started.get() && process?.isAlive == true

    @Synchronized
    fun start(): Boolean {
        if (isAvailable) return true
        return try {
            val p = ProcessBuilder("su")
                .redirectErrorStream(true)
                .start()
            process = p
            writer = OutputStreamWriter(p.outputStream, Charsets.UTF_8)
            reader = BufferedReader(InputStreamReader(p.inputStream, Charsets.UTF_8))
            // 探活：能拿到回显说明 su 授权成功
            val ok = exec("echo ROOT_OK", timeoutMs = 8000)?.contains("ROOT_OK") == true
            started.set(ok)
            if (!ok) close()
            ok
        } catch (e: Exception) {
            log("start 失败: ${e.message}")
            close()
            false
        }
    }

    /**
     * 执行一条命令并返回输出（不含 marker 行）。
     * @return 输出内容；失败或超时返回 null
     */
    @Synchronized
    fun exec(cmd: String, timeoutMs: Long = 8000): String? {
        if (!isAvailable && !start()) return null
        val w = writer ?: return null
        val r = reader ?: return null
        val marker = "KOMARI_DONE_${System.nanoTime()}"
        return try {
            w.write("$cmd\n")
            w.write("echo $marker\n")
            w.flush()

            val sb = StringBuilder()
            while (true) {
                val line = r.readLine() ?: break
                if (line.contains(marker)) break
                if (sb.isNotEmpty()) sb.append('\n')
                sb.append(line)
            }
            sb.toString()
        } catch (e: Exception) {
            log("exec 失败: ${e.message}")
            // shell 可能已死，下次调用会重新 start()
            started.set(false)
            null
        }
    }

    /** 批量读取多个 sysfs 节点，返回 key -> 原始字符串 */
    fun readNodes(paths: Collection<String>): Map<String, String> {
        if (paths.isEmpty()) return emptyMap()
        val marker = "NODE_MARK_${System.nanoTime()}"
        val sb = StringBuilder()
        paths.forEach { path ->
            // 注意用单引号包裹路径，避免特殊字符；节点不存在时输出空串
            sb.append("printf '%s\\n' \"$(cat '$path' 2>/dev/null)\"\n")
        }
        sb.append("echo $marker\n")
        val out = exec(sb.toString().trimEnd()) ?: return emptyMap()
        val lines = out.split('\n')
        val result = HashMap<String, String>(paths.size)
        paths.forEachIndexed { i, path ->
            result[path] = lines.getOrElse(i) { "" }.trim()
        }
        return result
    }

    @Synchronized
    fun close() {
        started.set(false)
        try { writer?.close() } catch (_: Exception) {}
        try { reader?.close() } catch (_: Exception) {}
        try { process?.destroy() } catch (_: Exception) {}
        writer = null
        reader = null
        process = null
    }

    private fun log(msg: String) {
        android.util.Log.d(TAG, msg)
    }

    private companion object {
        const val TAG = "BatteryPower/Root"
    }
}
