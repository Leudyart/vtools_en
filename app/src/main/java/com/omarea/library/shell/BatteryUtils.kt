package com.omarea.library.shell

import android.content.Context
import android.os.Build
import com.omarea.Scene
import com.omarea.common.shared.FileWrite
import com.omarea.common.shell.KeepShellPublic
import com.omarea.common.shell.KernelProrp
import com.omarea.common.shell.RootFile
import com.omarea.model.BatteryStatus

/**
 * Created by Hello on 2017/11/01.
 */

class BatteryUtils {
    companion object {
        private var fastChargeScript = ""
        private var changeLimitRunning = false
        private var isFirstRun = true

        /**
         * 获取电池温度
         */
        // @Deprecated("", ReplaceWith("GlobalStatus"), DeprecationLevel.ERROR)
        public fun getBatteryTemperature(): BatteryStatus {
            val batteryInfo = KeepShellPublic.doCmdSync("dumpsys battery")
            val batteryInfos = batteryInfo.split("\n")

            // 由于部分手机相同名称的参数重复出现，并且值不同，为了避免这种情况，加个额外处理，同名参数只读一次
            var levelReaded = false
            var tempReaded = false
            var statusReaded = false
            val batteryStatus = BatteryStatus()

            for (item in batteryInfos) {
                val info = item.trim()
                val index = info.indexOf(":")
                if (index > Int.MIN_VALUE && index < info.length - 1) {
                    val value = info.substring(info.indexOf(":") + 1).trim()
                    try {
                        if (info.startsWith("status")) {
                            if (!statusReaded) {
                                batteryStatus.statusText = value
                                statusReaded = true
                            } else {
                                continue
                            }
                        } else if (info.startsWith("level")) {
                            if (!levelReaded) {
                                batteryStatus.capacity = value.toInt()
                                levelReaded = true
                            } else continue
                        } else if (info.startsWith("temperature")) {
                            if (!tempReaded) {
                                tempReaded = true
                                batteryStatus.temperature = (value.toFloat() / 10.0)
                            } else continue
                        }
                    } catch (ex: java.lang.Exception) {

                    }
                }
            }
            return batteryStatus
        }
    }

    //获取电池信息
    /*else if (info.startsWith("POWER_SUPPLY_TIME_TO_EMPTY_AVG=")) {
                        stringBuilder.append("Avg depletion = ");
                        int val = Integer.parseInt(info.substring(keyword.length(), info.length()));
                        stringBuilder.append(((val / 3600.0) + "    ").substring(0, 4));
                        stringBuilder.append("Hours");
                    } else if (info.startsWith("POWER_SUPPLY_TIME_TO_FULL_AVG=")) {
                        stringBuilder.append("Avg full = ");
                        int val = Integer.parseInt(info.substring(keyword.length(), info.length()));
                        stringBuilder.append(((val / 3600.0) + "    ").substring(0, 4));
                        stringBuilder.append("Hours");
                    }*/

    private fun str2voltage(str: String): String {
        val value = str.substring(0, if (str.length > 4) 4 else str.length).toDouble()

        return (when {
            value > 3000 -> {
                value / 1000
            }
            value > 300 -> {
                value / 100
            }
            value > 30 -> {
                value / 10
            }
            else -> {
                value
            }
        }).toString() + "v"
    }

    val batteryInfo: String
        get() {
            val bms = "/sys/class/power_supply/bms/uevent"
            val battery = "/sys/class/power_supply/battery/uevent"
            val path = (when {
                RootFile.fileExists(bms) -> {
                    bms
                }
                RootFile.fileExists(battery) -> {
                    battery
                }
                else -> {
                    ""
                }
            })
            if (path.isNotEmpty()) {
                val batteryInfos = KernelProrp.getProp(path)
                val infos = batteryInfos.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                val stringBuilder = StringBuilder()
                var io = ""
                var mahLength = 0
                for (info in infos) {
                    try {
                        if (info.startsWith("POWER_SUPPLY_CHARGE_FULL=")) {
                            val keyword = "POWER_SUPPLY_CHARGE_FULL="
                            stringBuilder.append("Full cap = ")
                            stringBuilder.append(info.substring(keyword.length, keyword.length + 4))
                            if (mahLength == 0) {
                                val value = info.substring(keyword.length, info.length)
                                mahLength = value.length
                            }
                            stringBuilder.append("mAh")
                        } else if (info.startsWith("POWER_SUPPLY_CHARGE_FULL_DESIGN=")) {
                            val keyword = "POWER_SUPPLY_CHARGE_FULL_DESIGN="
                            stringBuilder.append("Designed cap = ")
                            stringBuilder.append(info.substring(keyword.length, keyword.length + 4))
                            stringBuilder.append("mAh")
                            val value = info.substring(keyword.length, info.length)
                            mahLength = value.length
                        } else if (info.startsWith("POWER_SUPPLY_TEMP=")) {
                            val keyword = "POWER_SUPPLY_TEMP="
                            stringBuilder.append("Batt temp = ")
                            val temp = info.substring(keyword.length, info.length)
                            val prefix = if (temp.contains("-")) "-" else ""
                            val tempStr = temp.replace("-", "")
                            stringBuilder.append(prefix)
                            stringBuilder.append(if (tempStr.length >= 3) {
                                tempStr.substring(0, 3).toInt() / 10f
                            } else {
                                tempStr.substring(0, 2).toInt()
                            })
                            stringBuilder.append("°C")
                        } else if (info.startsWith("POWER_SUPPLY_TEMP_WARM=")) {
                            val keyword = "POWER_SUPPLY_TEMP_WARM="
                            stringBuilder.append("Warm temp = ")
                            val value = Integer.parseInt(info.substring(keyword.length, info.length))
                            stringBuilder.append(value / 10)
                            stringBuilder.append("°C")
                        } else if (info.startsWith("POWER_SUPPLY_TEMP_COOL=")) {
                            val keyword = "POWER_SUPPLY_TEMP_COOL="
                            stringBuilder.append("Cool temp = ")
                            val value = Integer.parseInt(info.substring(keyword.length, info.length))
                            stringBuilder.append(value / 10)
                            stringBuilder.append("°C")
                        } else if (info.startsWith("POWER_SUPPLY_VOLTAGE_NOW=")) {
                            val keyword = "POWER_SUPPLY_VOLTAGE_NOW="
                            stringBuilder.append("Curr voltage = ")
                            stringBuilder.append(str2voltage(info.substring(keyword.length, info.length)))
                        } else if (info.startsWith("POWER_SUPPLY_VOLTAGE_MAX_DESIGN=")) {
                            val keyword = "POWER_SUPPLY_VOLTAGE_MAX_DESIGN="
                            stringBuilder.append("Designed voltage = ")
                            stringBuilder.append(str2voltage(info.substring(keyword.length, info.length)))
                        } else if (info.startsWith("POWER_SUPPLY_VOLTAGE_MIN=")) {
                            val keyword = "POWER_SUPPLY_VOLTAGE_MIN="
                            stringBuilder.append("Min voltage = ")
                            stringBuilder.append(str2voltage(info.substring(keyword.length, info.length)))
                        } else if (info.startsWith("POWER_SUPPLY_VOLTAGE_MAX=")) {
                            val keyword = "POWER_SUPPLY_VOLTAGE_MAX="
                            stringBuilder.append("Max voltage = ")
                            stringBuilder.append(str2voltage(info.substring(keyword.length, info.length)))
                        } else if (info.startsWith("POWER_SUPPLY_BATTERY_TYPE=")) {
                            val keyword = "POWER_SUPPLY_BATTERY_TYPE="
                            stringBuilder.append("Batt type = ")
                            stringBuilder.append(info.substring(keyword.length, info.length))
                        } else if (info.startsWith("POWER_SUPPLY_TECHNOLOGY=")) {
                            val keyword = "POWER_SUPPLY_TECHNOLOGY="
                            stringBuilder.append("Technology = ")
                            stringBuilder.append(info.substring(keyword.length, info.length))
                        } else if (info.startsWith("POWER_SUPPLY_CYCLE_COUNT=")) {
                            val keyword = "POWER_SUPPLY_CYCLE_COUNT="
                            stringBuilder.append("Cycle times = ")
                            stringBuilder.append(info.substring(keyword.length, info.length))
                        } else if (info.startsWith("POWER_SUPPLY_CONSTANT_CHARGE_VOLTAGE=")) {
                            val keyword = "POWER_SUPPLY_CONSTANT_CHARGE_VOLTAGE="
                            stringBuilder.append("Charging voltage = ")
                            stringBuilder.append(str2voltage(info.substring(keyword.length, info.length)))
                        } else if (info.startsWith("POWER_SUPPLY_CAPACITY=")) {
                            val keyword = "POWER_SUPPLY_CAPACITY="
                            stringBuilder.append("Batt level = ")
                            stringBuilder.append(info.substring(keyword.length, info.length))
                            stringBuilder.append("%")
                        } else if (info.startsWith("POWER_SUPPLY_MODEL_NAME=")) {
                            val keyword = "POWER_SUPPLY_MODEL_NAME="
                            stringBuilder.append("Module/Model = ")
                            stringBuilder.append(info.substring(keyword.length, info.length))
                        } else if (info.startsWith("POWER_SUPPLY_CHARGE_TYPE=")) {
                            val keyword = "POWER_SUPPLY_CHARGE_TYPE="
                            stringBuilder.append("Charge type = ")
                            stringBuilder.append(info.substring(keyword.length, info.length))
                        } else if (info.startsWith("POWER_SUPPLY_RESISTANCE_NOW=")) {
                            val keyword = "POWER_SUPPLY_RESISTANCE_NOW="
                            stringBuilder.append("Resistance = ")
                            stringBuilder.append(info.substring(keyword.length, info.length))
                        } /* else if (info.startsWith("POWER_SUPPLY_VOLTAGE_AVG=")) {
                            val keyword = "POWER_SUPPLY_VOLTAGE_AVG="
                            stringBuilder.append("Avg voltage = ")
                            stringBuilder.append(str2voltage(info.substring(keyword.length, info.length)))
                        } */ else if (info.startsWith("POWER_SUPPLY_CURRENT_NOW=")) {
                            val keyword = "POWER_SUPPLY_CURRENT_NOW="
                            io = info.substring(keyword.length, info.length)
                            continue
                        } else if (info.startsWith("POWER_SUPPLY_CONSTANT_CHARGE_CURRENT=")) {
                            val keyword = "POWER_SUPPLY_CONSTANT_CHARGE_CURRENT="
                            io = info.substring(keyword.length, info.length)
                            continue
                        } else {
                            continue
                        }
                        stringBuilder.append("\n")
                    } catch (ignored: Exception) {
                        stringBuilder.append("\n")
                    }
                }

                if (io.isNotEmpty() && mahLength != 0) {
                    val `val` = if (mahLength < 5) Integer.parseInt(io) else (Integer.parseInt(io) / Math.pow(10.0, (mahLength - 4).toDouble())).toInt()
                    stringBuilder.insert(0, "Discharge speed = " + `val` + "mA\n")
                }

                return stringBuilder.toString()
            } else {
                return ""
            }
        }

    val usbInfo: String
        get() {
            if (RootFile.fileExists("/sys/class/power_supply/usb/uevent")) {
                val batteryInfos = KernelProrp.getProp("/sys/class/power_supply/usb/uevent")
                val infos = batteryInfos.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                val stringBuilder = StringBuilder()
                var voltage = 0F
                var electricity = 0F
                var pdAuth = false
                for (info in infos) {
                    try {
                        if (info.startsWith("POWER_SUPPLY_VOLTAGE_NOW=")) {
                            val keyword = "POWER_SUPPLY_VOLTAGE_NOW="
                            stringBuilder.append("Current voltage = ")
                            val v = str2voltage(info.substring(keyword.length, info.length))
                            voltage = v.replace("v", "").toFloat()
                            stringBuilder.append(v)
                        } /* else if (info.startsWith("POWER_SUPPLY_VOLTAGE_MAX=")) {
                            val keyword = "POWER_SUPPLY_VOLTAGE_MAX="
                            stringBuilder.append("Max voltage = ")
                            stringBuilder.append(str2voltage(info.substring(keyword.length, info.length)))
                        } else if (info.startsWith("POWER_SUPPLY_VOLTAGE_MAX_DESIGN=")) {
                            val keyword = "POWER_SUPPLY_VOLTAGE_MAX_DESIGN="
                            stringBuilder.append("Max voltage (Design) = ")
                            stringBuilder.append(str2voltage(info.substring(keyword.length, info.length)))
                        } */ else if (info.startsWith("POWER_SUPPLY_CURRENT_MAX=")) {
                            val keyword = "POWER_SUPPLY_CURRENT_MAX="
                            val v = Integer.parseInt(info.substring(keyword.length, info.length)) / 1000 / 1000.0f
                            if (v > 0) {
                                stringBuilder.append("Max current = ")
                                stringBuilder.append(v)
                                stringBuilder.append("A")
                            } else {
                                continue
                            }
                        } else if (info.startsWith("POWER_SUPPLY_PD_VOLTAGE_MAX=")) {
                            val keyword = "POWER_SUPPLY_PD_VOLTAGE_MAX="
                            stringBuilder.append("Max voltage(PD) = ")
                            stringBuilder.append(str2voltage(info.substring(keyword.length, info.length)))
                        } else if (info.startsWith("POWER_SUPPLY_CONNECTOR_TEMP=")) {
                            val keyword = "POWER_SUPPLY_CONNECTOR_TEMP="
                            stringBuilder.append("Interface temp = ")
                            val v = Integer.parseInt(info.substring(keyword.length, info.length))
                            stringBuilder.append((v / 10.0f))
                            stringBuilder.append("°C")
                        } else if (info.startsWith("POWER_SUPPLY_PD_VOLTAGE_MIN=")) {
                            val keyword = "POWER_SUPPLY_PD_VOLTAGE_MIN="
                            stringBuilder.append("Min voltage(PD) = ")
                            stringBuilder.append(str2voltage(info.substring(keyword.length, info.length)))
                        } else if (info.startsWith("POWER_SUPPLY_PD_CURRENT_MAX=")) {
                            val keyword = "POWER_SUPPLY_PD_CURRENT_MAX="
                            val v = Integer.parseInt(info.substring(keyword.length, info.length)) / 1000 / 1000.0f
                            if (v > 0) {
                                stringBuilder.append("Max current(PD) = ")
                                stringBuilder.append(v)
                                stringBuilder.append("A")
                            } else {
                                continue
                            }
                        } else if (info.startsWith("POWER_SUPPLY_INPUT_CURRENT_NOW=")) {
                            val keyword = "POWER_SUPPLY_INPUT_CURRENT_NOW="
                            val v = Integer.parseInt(info.substring(keyword.length, info.length))
                            electricity = v / 1000 / 1000.0f
                            continue
                        } else if (info.startsWith("POWER_SUPPLY_QUICK_CHARGE_TYPE=")) {
                            val keyword = "POWER_SUPPLY_QUICK_CHARGE_TYPE="
                            stringBuilder.append("QC type = ")
                            val type = info.substring(keyword.length, info.length)
                            if (type == "0") {
                                stringBuilder.append("Slow charge")
                            } else {
                                stringBuilder.append("Type")
                                stringBuilder.append(type)
                            }
                        } else if (info.startsWith("POWER_SUPPLY_REAL_TYPE=")) {
                            val keyword = "POWER_SUPPLY_REAL_TYPE="
                            stringBuilder.append("Transmission = ")
                            stringBuilder.append(info.substring(keyword.length, info.length))
                        } else if (info.startsWith("POWER_SUPPLY_HVDCP3_TYPE=")) {
                            val keyword = "POWER_SUPPLY_HVDCP3_TYPE="
                            stringBuilder.append("High-voltage QC = ")
                            val type = info.substring(keyword.length, info.length)
                            if (type == "0") {
                                stringBuilder.append("No")
                            } else {
                                stringBuilder.append("Type")
                                stringBuilder.append(type)
                            }
                        } else if (info.startsWith("POWER_SUPPLY_PD_AUTHENTICATION=")) {
                            val keyword = "POWER_SUPPLY_PD_AUTHENTICATION="
                            stringBuilder.append("PD certification = ")
                            pdAuth = info.substring(keyword.length, info.length) == "1"
                            stringBuilder.append(if (pdAuth) "verified" else "not certified")
                        } else {
                            continue
                        }
                        stringBuilder.append("\n")
                    } catch (ignored: Exception) {
                        stringBuilder.append("\n")
                    }
                }
                if (!pdAuth && voltage > 0 && electricity > 0) {
                    stringBuilder.append("Current curr = ")
                    stringBuilder.append(electricity)
                    stringBuilder.append("A")

                    stringBuilder.append("\nReference power = ")
                    stringBuilder.append((voltage * electricity * 100).toInt() / 100f)
                    stringBuilder.append("W")
                }

                return stringBuilder.toString()
            } else {
                return ""
            }
        }

    //快充是否支持修改充电速度设置
    fun qcSettingSupport(): Boolean {
        return (
            RootFile.itemExists("/sys/class/power_supply/battery/constant_charge_current_max") ||
            (
                // Xiaomi 11Pro/Ultra
                mi11ProSeries &&
                RootFile.itemExists("/sys/class/power_supply/battery/constant_charge_current")
            )
        )
    }

    fun stepChargeSupport(): Boolean {
        return RootFile.itemExists("/sys/class/power_supply/battery/step_charging_enabled")
    }

    fun getStepCharge(): Boolean {
        return KernelProrp.getProp("/sys/class/power_supply/battery/step_charging_enabled") == "1"
    }

    fun setStepCharge(stepCharge: Boolean) {
        KernelProrp.setProp("/sys/class/power_supply/battery/step_charging_enabled", if (stepCharge) "1" else "0")
    }

    // Xiaomi 11Pro/Ultra
    private val mi11ProSeries : Boolean
        get () {
            return (Build.DEVICE == "mars" || Build.DEVICE == "star")
        }

    private var useMainConstant: Boolean? = false // null
    fun getQcLimit(): String {
        if (useMainConstant == null) {
            useMainConstant = RootFile.fileExists("/sys/class/power_supply/main/constant_charge_current_max")
        }

        var limit = if (useMainConstant == true) {
            KernelProrp.getProp("/sys/class/power_supply/main/constant_charge_current_max")
        } else {
            if (mi11ProSeries) {
                KernelProrp.getProp("/sys/class/power_supply/battery/constant_charge_current")
            } else {
                KernelProrp.getProp("/sys/class/power_supply/battery/constant_charge_current_max")
            }
        }
        when {
            limit.length > 3 -> {
                limit = limit.substring(0, limit.length - 3) + "mA"
            }
            limit.isNotEmpty() -> {
                try {
                    if (Integer.parseInt(limit) == 0) {
                        limit = "0"
                    }
                } catch (ignored: Exception) {
                }

            }
            else -> {
                return "?mA"
            }
        }
        return limit
    }

    //快充是否支持电池保护
    fun bpSettingSupport(): Boolean {
        return RootFile.itemExists("/sys/class/power_supply/battery/battery_charging_enabled") ||
                RootFile.itemExists("/sys/class/power_supply/battery/input_suspend") ||
                RootFile.itemExists("/sys/class/qcom-battery/input_suspend")
    }

    // 设置充电速度限制
    fun setChargeInputLimit(limit: Int, context: Context, force: Boolean = false): Boolean {
        if (changeLimitRunning && !force) {
            return false
        } else {
            synchronized(Scene.context) {
                changeLimitRunning = true

                if (fastChargeScript.isEmpty()) {
                    val output = FileWrite.writePrivateShellFile("addin/fast_charge.sh", "addin/fast_charge.sh", context)
                    val output2 = FileWrite.writePrivateShellFile("addin/fast_charge_run_once.sh", "addin/fast_charge_run_once.sh", context)
                    if (output != null && output2 != null) {
                        if (isFirstRun) {
                            KeepShellPublic.getInstance("setChargeInputLimit", true).doCmdSync("sh $output2")
                            isFirstRun = false
                        }

                        fastChargeScript = "sh $output "
                    }
                }

                return if (fastChargeScript.isNotEmpty()) {
                    if (limit > 3000 && !mi11ProSeries) {
                        var current = 3000
                        while (current < (limit - 300) && current < 5000) {
                            if (KeepShellPublic.getInstance("setChargeInputLimit", true).doCmdSync("$fastChargeScript$current 1") == "error") {
                                break
                            }
                            current += 300
                        }
                    }
                    KeepShellPublic.getInstance("setChargeInputLimit", true).doCmdSync("$fastChargeScript$limit 0")
                    changeLimitRunning = false
                    true
                } else {
                    changeLimitRunning = false
                    false
                }
            }
        }
    }

    fun pdSupported(): Boolean {
        return RootFile.fileExists("/sys/class/power_supply/usb/pd_allowed") || RootFile.fileExists("/sys/class/power_supply/usb/pd_active")
    }

    fun pdAllowed(): Boolean {
        return KernelProrp.getProp("/sys/class/power_supply/usb/pd_allowed") == "1"
    }

    fun setAllowed(boolean: Boolean): Boolean {
        val builder = java.lang.StringBuilder()
        builder.append("chmod 777 /sys/class/power_supply/usb/pd_allowed\n")
        builder.append("echo ${if (boolean) "1" else "0"}> /sys/class/power_supply/usb/pd_allowed\n")
        builder.append("chmod 777 /sys/class/power_supply/usb/pd_active\n")
        builder.append("echo 1 > /sys/class/power_supply/usb/pd_active\n")
        return KeepShellPublic.doCmdSync(builder.toString()) != "error"
    }

    fun pdActive(): Boolean {
        return KernelProrp.getProp("/sys/class/power_supply/usb/pd_active") == "1"
    }

    public fun getChargeFull(): Int {
        val value = KernelProrp.getProp("/sys/class/power_supply/bms/charge_full")
        return if (Regex("^[0-9]+").matches(value)) (value.toInt() / 1000) else 0
    }

    public fun setChargeFull(mAh: Int) {
        KernelProrp.setProp("/sys/class/power_supply/bms/charge_full", (mAh * 1000).toString())
    }

    public fun getCapacity(): Int {
        val value = KernelProrp.getProp("/sys/class/power_supply/battery/capacity")
        return if (Regex("^[0-9]+").matches(value)) value.toInt() else 0
    }

    public fun setCapacity(capacity: Int) {
        KernelProrp.setProp("/sys/class/power_supply/battery/capacity", capacity.toString())
    }

    private var kernelCapacitySupported: Boolean? = null

    // 从内核读取可以精确到0.01的电量，但有些内核数值是错的，所以需要和系统反馈的电量(approximate)比对，如果差距太大则认为内核数值无效，不再读取
    public fun getKernelCapacity(approximate: Int): Float {
        if (kernelCapacitySupported == null) {
            kernelCapacitySupported = RootFile.fileExists("/sys/class/power_supply/bms/capacity_raw")
        }
        if (kernelCapacitySupported == true) {
            try {
                val raw = KernelProrp.getProp("/sys/class/power_supply/bms/capacity_raw")
                val capacityValue = raw.toInt()

                val valueMA = if (Math.abs(capacityValue - approximate) > Math.abs((capacityValue / 100f) - approximate)) {
                    capacityValue / 100f
                } else {
                    raw.toFloat()
                }
                // 如果和系统反馈的电量差距超过5%，则认为数值无效，不再读取
                return if (Math.abs(valueMA - approximate) > 5) {
                    kernelCapacitySupported = false
                    -1f
                } else {
                    valueMA
                }
            } catch (ex: java.lang.Exception) {
                kernelCapacitySupported = false
            }
        }
        return -1f
    }
}
