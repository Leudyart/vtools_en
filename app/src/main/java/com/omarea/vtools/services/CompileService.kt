package com.omarea.vtools.services

import android.app.IntentService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.support.v4.app.NotificationCompat
import com.omarea.common.shell.KeepShell
import com.omarea.vtools.R
import java.util.*

/**
 * 后台编译应用
 */
class CompileService : IntentService("vtools-compile") {
    companion object {
        var compiling = false
    }

    private var compileCanceled = false
    private var keepShell: KeepShell? = KeepShell(true)
    private lateinit var nm: NotificationManager
    private var compile_method = "speed"

    private fun getAllPackageNames(): ArrayList<String> {
        val packageManager: PackageManager = packageManager
        val packageInfos = packageManager.getInstalledApplications(0)
        val list = ArrayList<String>()/*在数组中存放数据*/
        for (i in packageInfos.indices) {
            list.add(packageInfos[i].packageName)
        }
        list.remove(packageName)
        return list
    }

    private fun updateNotification(title: String, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel("vtool-compile", "后台编译", NotificationManager.IMPORTANCE_LOW))
            nm.notify(990, NotificationCompat.Builder(this, "vtool-compile").setSmallIcon(R.drawable.process).setSubText(title).setContentText(text).build())
        } else {
            nm.notify(990, NotificationCompat.Builder(this).setSmallIcon(R.drawable.process).setSubText(title).setContentText(text).build())
        }
    }

    override fun onHandleIntent(intent: Intent?) {
        nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (compiling) {
            compileCanceled = true
            return
        }

        if (intent != null) {
            if (intent.action == getString(R.string.scene_speed_compile)) {
                compile_method = "speed"
            } else if (intent.action == getString(R.string.scene_everything_compile)) {
                compile_method = "everything"
            }
        }

        compiling = true

        val packageNames = getAllPackageNames()
        val total = packageNames.size
        var current = 0
        for (packageName in packageNames) {
            if (true) {
                updateNotification(getString(R.string.dex2oat_compiling), "[$current/$total] ${packageName}")
                keepShell!!.doCmdSync("cmd package compile -m ${compile_method} ${packageName}")
                current++
            } else {
                break
            }
        }
        keepShell!!.tryExit()
        keepShell = null
        compiling = false

        this.stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()

        if (compileCanceled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nm.cancel(990)
            } else {
                nm.cancel(990)
            }
        } else {
            updateNotification("complete!", getString(R.string.dex2oat_completed))
        }
    }
}
