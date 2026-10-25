package com.duoopen.overlay

import android.content.Context
import android.view.WindowManager
import com.duoopen.shell.ShizukuBridge
import com.duoopen.shell.WallpaperAngleFeed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted

/** Full edition: the system-wide fold via [FoldOverlayService], plus optional Shizuku helpers. */
object OverlayFeature {
    const val AVAILABLE = true
    const val SHIZUKU_AVAILABLE = true

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Called once per process (app and service share it). */
    fun initProcess(context: Context) {
        runCatching {
            org.lsposed.hiddenapibypass.HiddenApiBypass.addHiddenApiExemptions("Landroid/view/ViewRootImpl;")
        }
        ShizukuBridge.init(context)
    }

    fun isEnabled(context: Context): Boolean = FoldOverlayService.isEnabled(context)

    /** Whether the system will blur behind our windows (the "live blur" engine). */
    fun liveBlurSupported(context: Context): Boolean =
        runCatching { context.getSystemService(WindowManager::class.java)?.isCrossWindowBlurEnabled == true }
            .getOrDefault(false)

    /** Replays the effect over the current screen; false if the service isn't connected. */
    fun playDemo(): Boolean {
        val service = FoldOverlayService.instance ?: return false
        service.playDemo()
        return true
    }

    // ---- Shizuku mode --------------------------------------------------------

    /** Human-readable Shizuku state, live. */
    val shizukuStatus: StateFlow<String> =
        ShizukuBridge.state.map { it.summary }.stateIn(scope, SharingStarted.Eagerly, ShizukuBridge.state.value.summary)

    fun shizukuReady(): Boolean = ShizukuBridge.ready
    fun shizukuInstalled(): Boolean = ShizukuBridge.installed
    fun shizukuNeedsPermission(): Boolean = ShizukuBridge.state.value is ShizukuBridge.State.NeedsPermission
    fun requestShizuku() = ShizukuBridge.requestPermission()
    fun refreshShizuku() = ShizukuBridge.refresh()

    /** Whether Samsung's fold-reactive wallpaper (the continuous-angle source) is the home wallpaper. */
    fun foldWallpaperActive(context: Context): Boolean = WallpaperAngleFeed.foldWallpaperActive(context)

    /** Status of the continuous-angle reader, from the running service. */
    fun angleFeedStatus(): String = FoldOverlayService.instance?.angleFeedStatus() ?: "Service not running"

    // ---- Keep apps on when folded ------------------------------------------

    const val STAY_AWAKE_ON_FOLD = "stay_awake_on_fold_key"
    private const val FOLD_LOCK_ORIGINAL = "foldLockOriginal"
    private val FOLD_LOCK_VALUES = setOf("sleep_on_fold_key", "selective_stay_awake_key", STAY_AWAKE_ON_FOLD)

    /** Samsung's current fold-lock value ("null" when never set); null when Shizuku isn't ready. */
    fun foldLockValue(): String? =
        ShizukuBridge.foldLock("get")?.takeIf { it.getBoolean("ok") }?.getString("value")

    /**
     * On: remember Samsung's value, then keep the screen and app awake on
     * folding so the app carries on on the cover screen. Off: put the
     * remembered value back. False if Shizuku refused.
     */
    fun setKeepAppsOnFold(context: Context, on: Boolean): Boolean {
        val prefs = context.getSharedPreferences("duo_open", Context.MODE_PRIVATE)
        val result = if (on) {
            if (!prefs.contains(FOLD_LOCK_ORIGINAL)) {
                val original = foldLockValue() ?: return false
                prefs.edit().putString(FOLD_LOCK_ORIGINAL, original).commit()
            }
            ShizukuBridge.foldLock("put", STAY_AWAKE_ON_FOLD)
        } else {
            val original = prefs.getString(FOLD_LOCK_ORIGINAL, null) ?: return true
            if (original in FOLD_LOCK_VALUES) ShizukuBridge.foldLock("put", original) else ShizukuBridge.foldLock("delete")
        }
        val ok = result?.getBoolean("ok") == true
        if (ok && !on) prefs.edit().remove(FOLD_LOCK_ORIGINAL).apply()
        return ok
    }
}
