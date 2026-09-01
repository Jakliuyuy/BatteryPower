package com.jakliuyuy.batterypower.battery

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.jakliuyuy.batterypower.model.BatterySnapshot
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * 悬浮窗用的电池读取调度器（纯线程实现，不引入协程依赖）。
 *
 * 通道优先级：sysfs+root → sysfs 直读 → BatteryManager。
 * root 不可用时把 source 标为 no-root，由 UI 层提示用户，
 * 而不是静默显示 0.00W（避坑 8）。
 */
class BatteryReader(private val context: Context) {

    private val helper = RootHelper()
    private val running = AtomicBoolean(false)
    private var worker: Thread? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    /** 采样回调，已在主线程执行 */
    @Volatile
    var onUpdate: ((BatterySnapshot) -> Unit)? = null

    /** 后台建立 root 会话，不阻塞调用方 */
    fun prepareAsync() {
        thread(name = "bp-root-init") {
            try {
                helper.start()
            } catch (_: Exception) {
            }
        }
    }

    fun isRootAvailable(): Boolean = helper.isAvailable

    /** 同步取一次，用于诊断页与首次渲染 */
    fun readOnce(): BatterySnapshot {
        if (!helper.isAvailable) helper.start()
        if (helper.isAvailable) {
            val s = BatteryProbe.readViaRoot(helper)
            if (s != null) return s
        }
        val direct = BatteryProbe.readDirect()
        if (direct != null) return direct
        return BatteryProbe.readViaBatteryManager(context).copy(
            source = if (helper.isAvailable) "no-root-batterymanager" else "no-root"
        )
    }

    /** 以固定间隔持续采样 */
    fun start(intervalMs: Long) {
        if (running.get()) {
            // 间隔可能变了，重启线程
            stop()
        }
        running.set(true)
        val interval = intervalMs.coerceIn(200L, 10_000L)
        worker = thread(name = "bp-sampler", start = true) {
            while (running.get()) {
                val snapshot = try {
                    readOnce()
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "采样异常: ${e.message}")
                    BatterySnapshot.EMPTY
                }
                if (running.get()) {
                    mainHandler.post { onUpdate?.invoke(snapshot) }
                }
                try {
                    Thread.sleep(interval)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }
    }

    fun stop() {
        running.set(false)
        worker?.interrupt()
        worker = null
    }

    fun destroy() {
        stop()
        helper.close()
    }

    companion object {
        private const val TAG = "BatteryPower/Reader"
    }
}
