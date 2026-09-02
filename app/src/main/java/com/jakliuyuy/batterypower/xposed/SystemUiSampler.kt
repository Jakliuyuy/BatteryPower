package com.jakliuyuy.batterypower.xposed

import android.content.Context
import com.jakliuyuy.batterypower.core.battery.BatteryRepository
import com.jakliuyuy.batterypower.core.battery.ReaderFlags
import com.jakliuyuy.batterypower.core.config.AppConfig
import com.jakliuyuy.batterypower.core.log.BLog
import com.jakliuyuy.batterypower.core.model.BatterySnapshot
import com.jakliuyuy.batterypower.core.model.DeviceProfile
import com.jakliuyuy.batterypower.core.root.PersistentSuShell
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Background sampler for the SystemUI process (spec sections 135.3, 147, 154).
 *
 * One worker thread owns the persistent su shell; the SystemUI main thread only
 * applies an immutable snapshot to the view. Sampling and UI refresh are
 * decoupled so a slow read can never delay the status bar.
 */
class SystemUiSampler(private val context: Context) {

    private val worker: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "bpo-su-sample").apply { isDaemon = true }
    }

    private val shell = PersistentSuShell("SystemUI/Root")
    private val profile = DeviceProfile.defaultProfile()
    private var repository: BatteryRepository? = null

    @Volatile
    private var snapshot: BatterySnapshot = BatterySnapshot.empty()

    @Volatile
    var lastError: String? = null
        private set

    private var future: ScheduledFuture<*>? = null
    private val running = AtomicBoolean(false)

    fun start() {
        if (running.compareAndSet(false, true)) {
            future = worker.scheduleWithFixedDelay({
                try {
                    val result = getRepository().sample()
                    snapshot = result
                    lastError = result.error?.displayName
                } catch (t: Throwable) {
                    BLog.throttledError("SystemUI", "sampler", "sampling failed", t)
                }
            }, 0L, SAMPLE_INTERVAL_MS, TimeUnit.MILLISECONDS)
            BLog.i("SystemUI", "sampler started")
        }
    }

    fun stop() {
        if (running.compareAndSet(true, false)) {
            future?.cancel(false)
            future = null
            worker.execute { shell.close() }
            BLog.i("SystemUI", "sampler stopped")
        }
    }

    fun current(): BatterySnapshot = snapshot

    fun applyConfig(config: AppConfig) {
        val flags = ReaderFlags(
            enableRootReader = config.flags.enableRootReader,
            enableBatteryManagerFallback = config.flags.enableBatteryManagerFallback
        )
        worker.execute {
            try {
                repository = BatteryRepository(context, shell, profile, flags)
            } catch (t: Throwable) {
                BLog.throttledError("SystemUI", "sampler-config", "repository update failed", t)
            }
        }
    }

    private fun getRepository(): BatteryRepository {
        repository?.let { return it }
        val repo = BatteryRepository(
            context,
            shell,
            profile,
            ReaderFlags(enableRootReader = true, enableBatteryManagerFallback = true)
        )
        repository = repo
        return repo
    }

    companion object {
        const val SAMPLE_INTERVAL_MS = 1000L
    }
}
