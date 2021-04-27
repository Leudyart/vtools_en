package com.omarea.scene_mode

import android.content.Context
import android.util.Log
import com.omarea.Scene
import com.omarea.common.shared.FileWrite
import com.omarea.common.shell.KeepShellPublic
import com.omarea.library.shell.PropsUtils
import com.omarea.store.CpuConfigStorage
import com.omarea.store.SpfConfig
import com.omarea.vtools.R

/**
 * Created by Hello on 2018/06/03.
 */

open class ModeSwitcher {
    private var inited = false

    companion object {
        const val SOURCE_UNKNOWN = "UNKNOWN"
        const val SOURCE_SCENE_ACTIVE = "SOURCE_SCENE_ACTIVE"
        const val SOURCE_SCENE_CONSERVATIVE = "SOURCE_SCENE_CONSERVATIVE"
        const val SOURCE_SCENE_CUSTOM = "SOURCE_SCENE_CUSTOM"
        const val SOURCE_SCENE_IMPORT = "SOURCE_SCENE_IMPORT"
        const val SOURCE_SCENE_ONLINE = "SOURCE_SCENE_ONLINE"
        const val SOURCE_OUTSIDE = "SOURCE_OUTSIDE"
        const val SOURCE_NONE = "SOURCE_NONE"

        fun getCurrentSource(): String {
            if (CpuConfigInstaller().outsideConfigInstalled()) {
                return SOURCE_OUTSIDE
            }
            val config = Scene.context.getSharedPreferences(SpfConfig.GLOBAL_SPF, Context.MODE_PRIVATE).getString(SpfConfig.GLOBAL_SPF_PROFILE_SOURCE, SOURCE_UNKNOWN)
            if (config == SOURCE_SCENE_CUSTOM || CpuConfigInstaller().insideConfigInstalled()) {
                return config!!
            }
            return SOURCE_NONE
        }

        fun getCurrentSourceName(): String {
            val source = getCurrentSource()
            return (when (source) {
                "SOURCE_OUTSIDE" -> {
                    "External Sources"
                }
                "SOURCE_SCENE_CONSERVATIVE" -> {
                    "Scene-Classic"
                }
                "SOURCE_SCENE_ACTIVE" -> {
                    "Scene-Performance"
                }
                "SOURCE_SCENE_CUSTOM" -> {
                    "Custom"
                }
                "SOURCE_SCENE_IMPORT" -> {
                    "File Import"
                }
                "SOURCE_SCENE_ONLINE" -> {
                    "Online Download"
                }
                "SOURCE_NONE" -> {
                    "Undefined"
                }
                else -> {
                    "Unknown"
                }
            })
        }

        // 是否已经完成内置配置文件的自动更新（如果使用的是Scene自带的配置，每次切换调度前，先安装配置）
        private var innerConfigUpdated = false

        const val OUTSIDE_POWER_CFG_PATH = "/data/powercfg.sh"
        const val OUTSIDE_POWER_CFG_BASE = "/data/powercfg-base.sh"

        internal var DEFAULT = "balance"
        internal var POWERSAVE = "powersave"
        internal var PERFORMANCE = "performance"
        internal var FAST = "fast"
        internal var BALANCE = "balance"
        internal var IGONED = "igoned"
        private var INIT = "init"

        internal fun getModName(mode: String): String {
            when (mode) {
                POWERSAVE -> return "Power Save"
                PERFORMANCE -> return "Performance"
                FAST -> return "Speed Mode"
                BALANCE -> return "Balanced"
                IGONED -> return "Maintain status"
                "" -> return "Global Default"
                else -> return "Unknown"
            }
        }

        private var currentPowercfg: String = ""
        private var currentPowercfgApp: String = ""
    }

    internal fun getModIcon(mode: String): Int {
        when (mode) {
            POWERSAVE -> return R.drawable.p1
            BALANCE -> return R.drawable.p2
            PERFORMANCE -> return R.drawable.p3
            FAST -> return R.drawable.p4
            else -> return R.drawable.p3
        }
    }

    internal fun getModImage(mode: String): Int {
        return when (mode) {
            POWERSAVE -> R.drawable.shortcut_p1
            BALANCE -> R.drawable.shortcut_p2
            PERFORMANCE -> R.drawable.shortcut_p3
            FAST -> R.drawable.shortcut_p4
            else -> R.drawable.shortcut_p3
        }
    }

    fun getCurrentPowerMode(): String {
        if (!currentPowercfg.isEmpty()) {
            return currentPowercfg
        }
        return PropsUtils.getProp("vtools.powercfg")
    }

    internal fun getCurrentPowermodeApp(): String {
        if (!currentPowercfgApp.isEmpty()) {
            return currentPowercfgApp
        }
        return PropsUtils.getProp("vtools.powercfg_app")
    }

    internal fun setCurrent(powerCfg: String, app: String): ModeSwitcher {
        setCurrentPowercfg(powerCfg)
        setCurrentPowercfgApp(app)
        return this
    }

    internal fun setCurrentPowercfg(powerCfg: String): ModeSwitcher {
        currentPowercfg = powerCfg
        PropsUtils.setPorp("vtools.powercfg", powerCfg)
        return this
    }

    internal fun setCurrentPowercfgApp(app: String): ModeSwitcher {
        currentPowercfgApp = app
        PropsUtils.setPorp("vtools.powercfg_app", app)
        return this
    }

    private fun keepShellExec(cmd: String) {
        KeepShellPublic.doCmdSync(cmd)
    }

    private var configProvider: String = ""

    // init
    // TODO:看什么时候清空缓存
    internal fun initPowerCfg(): ModeSwitcher {
        if (configProvider.isEmpty()) {
            val installer = CpuConfigInstaller()
            if (installer.outsideConfigInstalled()) {
                configProvider = OUTSIDE_POWER_CFG_PATH
            } else {
                if (!(innerConfigUpdated)) {
                    installer.applyConfigNewVersion(Scene.context)
                    innerConfigUpdated = true
                }
                configProvider = FileWrite.getPrivateFilePath(Scene.context, "powercfg.sh")

                /*
                FileWrite.writePrivateFile(
                        RawText.getRawText(Scene.context, R.raw.general_optimize).toByteArray(Charset.defaultCharset()),
                        "powercfg/general_optimize.sh",
                        Scene.context).run {
                    if (this) {
                        val file = FileWrite.getPrivateFilePath(Scene.context, "powercfg/general_optimize.sh")
                        // keepShellExec("sh $file")
                        keepShellExec("nohup $file >/dev/null 2>&1 &")
                    }
                }
                */
            }
        }

        if (configProvider.isNotEmpty()) {
            keepShellExec("sh $configProvider $INIT")
            setCurrentPowercfg("")
        }

        inited = true
        return this
    }

    // 切换模式
    private fun executeMode(mode: String, packageName: String): ModeSwitcher {
        // TODO: mode == IGONED 的处理
        if (mode != IGONED) {
            val source = getCurrentSource()
            when (source) {
                SOURCE_SCENE_CUSTOM -> {
                    val cpuConfigStorage = CpuConfigStorage(Scene.context)
                    if (cpuConfigStorage.exists(mode)) {
                        cpuConfigStorage.applyCpuConfig(Scene.context, mode)
                        setCurrentPowercfg(mode)
                    } else {
                        Log.e("Scene", "" + mode + "Profile lost!")
                    }
                }
                SOURCE_OUTSIDE -> {
                    if (!inited) {
                        initPowerCfg()
                    }

                    if (configProvider.isNotEmpty()) {
                        keepShellExec(
                            "export top_app=$packageName\n" +
                            "sh $configProvider '$mode' > /dev/null 2>&1"
                        )
                        setCurrentPowercfg(mode)
                    } else {
                        Log.e("Scene", "" + mode + "Profile lost!")
                    }
                }
                else -> {
                    if (!inited) {
                        initPowerCfg()
                    }

                    if (configProvider.isNotEmpty()) {
                        keepShellExec(
                        "export top_app=$packageName\n" +
                            "sh $configProvider '$mode' > /dev/null 2>&1 &"
                        )
                        setCurrentPowercfg(mode)
                    } else {
                        Log.e("Scene", "" + mode + "Profile lost!")
                    }
                }
            }
        }

        return this
    }

    internal fun executePowercfgMode(mode: String, app: String): ModeSwitcher {
        if (app != Scene.thisPackageName) {
            executeMode(mode, app)
            setCurrentPowercfgApp(app)
        } else {
            executeMode(mode, "")
            setCurrentPowercfgApp("")
        }
        return this
    }

    // 是否已经完成指定模式的自定义
    public fun modeReplaced(mode: String): Boolean {
        return CpuConfigStorage(Scene.context).exists(mode)
    }

    // 是否已完成四个模式的配置
    public fun modeConfigCompleted(): Boolean {
        if (CpuConfigInstaller().outsideConfigInstalled()) {
            return true
        } else {
            val source = getCurrentSource()
            when (source) {
                SOURCE_SCENE_CUSTOM -> {
                    return allModeReplaced()
                }
                SOURCE_SCENE_ACTIVE,
                SOURCE_SCENE_CONSERVATIVE,
                SOURCE_SCENE_IMPORT,
                SOURCE_SCENE_ONLINE -> {
                    return CpuConfigInstaller().insideConfigInstalled()
                }
            }
        }
        return false
    }

    // 是否已经完成所有模式的自定义
    public fun allModeReplaced(): Boolean {
        val storage = CpuConfigStorage(Scene.context)

        return storage.exists(POWERSAVE) &&
                storage.exists(BALANCE) &&
                storage.exists(PERFORMANCE) &&
                storage.exists(FAST)
    }

    public fun clearInitedState() {
        inited = false
    }
}
