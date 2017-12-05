package com.omarea.vboot.dialogs

import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Message
import android.view.LayoutInflater
import android.widget.ProgressBar
import android.widget.TextView
import com.omarea.shell.AsynSuShellUnit
import com.omarea.vboot.R
import java.io.File
import java.util.*

/**
 * Created by helloklf on 2017/12/04.
 */

class dialog_app_options(private var context: Context, private var apps: ArrayList<HashMap<String, Any>>, private var handler: Handler) {
    var allowPigz = false
    var backupPath = "/sdcard/Android/apps/"

    fun selectUserAppOptions() {
        val alert = AlertDialog.Builder(context).setTitle("请选择操作")
                .setCancelable(true)
                .setItems(
                        arrayOf("备份（带数据）",
                                "卸载",
                                "卸载（保留数据）",
                                "清空数据",
                                "清除缓存",
                                "冻结",
                                "解冻"), { dialog, which ->
                    when (which) {
                        0 -> backupAll()
                        1 -> uninstallAll()
                        2 -> uninstallKeepDataAll()
                        3 -> clearAll()
                        4 -> trimCachesAll()
                        5 -> disableAll()
                        6 -> enableAll()
                    }
                })
                .show()
    }

    fun selectSystemAppOptions() {
        val alert = AlertDialog.Builder(context).setTitle("请选择操作")
                .setCancelable(true)
                .setItems(
                        arrayOf("删除",
                                "清空数据",
                                "清除缓存",
                                "冻结",
                                "解冻",
                                "禁用+隐藏"), { dialog, which ->
                    when (which) {
                        0 -> deleteAll()
                        1 -> clearAll()
                        2 -> trimCachesAll()
                        3 -> disableAll()
                        4 -> enableAll()
                        5 -> hideAll()
                    }
                })
                .show()
    }

    fun selectBackupOptions() {
        val alert = AlertDialog.Builder(context).setTitle("请选择操作")
                .setCancelable(true)
                .setItems(
                        arrayOf("删除备份",
                                "还原",
                                "还原(应用)",
                                "还原(数据)"), { dialog, which ->
                    when (which) {
                        0 -> deleteAll()
                        1 -> restoreAll(true, true)
                        2 -> restoreAll(true, false)
                        3 -> restoreAll(false, true)
                    }
                })
                .show()
    }

    private fun execShell(sb: StringBuilder) {
        val layoutInflater = LayoutInflater.from(context)
        val dialog = layoutInflater.inflate(R.layout.dialog_app_options, null)
        val textView = (dialog.findViewById(R.id.dialog_app_details_pkgname) as TextView)
        val progressBar = (dialog.findViewById(R.id.dialog_app_details_progress) as ProgressBar)
        textView.setText("正在获取权限")
        val alert = AlertDialog.Builder(context).setView(dialog).setCancelable(false).create()

        val h = object : Handler() {
            override fun handleMessage(msg: Message) {
                super.handleMessage(msg)
                if (msg.obj != null){
                    if (msg.what == 0) {
                        textView.setText("正在执行操作...")
                    } else {
                        val obj = msg.obj.toString()
                        if (obj == "[operation completed]") {
                            progressBar.progress = 100
                            textView.setText("操作完成！")
                            handler.postDelayed({
                                alert.dismiss()
                                alert.hide()
                            }, 2000)
                            handler.handleMessage(handler.obtainMessage(2))
                        } else if (Regex("^\\[.*\\]\$").matches(obj)) {
                            progressBar.progress = msg.what
                            val txt = obj
                                    .replace("install", "安装：")
                                    .replace("restore", "还原：")
                                    .replace("uninstall", "卸载：")
                                    .replace("hide", "隐藏：")
                                    .replace("delete", "删除：")
                                    .replace("disable", "禁用：")
                                    .replace("enable", "启用：")
                                    .replace("trim caches", "清除缓存：")
                                    .replace("clear", "清除数据：")
                            textView.setText(txt)
                        }
                    }
                }
            }
        }
        alert.show()
        AsynSuShellUnit(h).exec(sb.toString())
    }

    /**
     * 检查是否可用pigz
     */
    private fun checkPigz() {
        if (File("/system/xbin/pigz").exists() || File("/system/bin/pigz").exists()) {
            allowPigz = true
        }
    }

    /**
     * 备份选中的应用
     */
    private fun backupAll(apk:Boolean = true, data:Boolean = true) {
        checkPigz()

        var sb = StringBuilder()
        sb.append("mkdir -p $backupPath;")

        for (item in apps) {
            val packageName = item.get("packageName").toString()
            val path = item.get("path").toString()

            sb.append("echo '[backup $packageName]';")
            sb.append("cd /data/data/$packageName;")
            if (apk)
                sb.append("cp $path $backupPath$packageName.apk;")
            if (data) {
                if (allowPigz)
                    sb.append("tar cf - * --exclude cache --exclude lib | pigz > $backupPath$packageName.tar.gz;")
                else
                    sb.append("tar -czf $backupPath$packageName.tar.gz * --exclude cache --exclude lib;")
            }
        }

        sb.append("echo '[operation completed]';")
        execShell(sb)
    }

    /**
     * 还原选中的应用
     */
    private fun restoreAll(apk:Boolean = true, data:Boolean = true) {
        var sb = StringBuilder()
        for (item in apps) {
            val packageName = item.get("packageName").toString()
            if (apk && File("$backupPath$packageName.apk").exists()) {
                sb.append("echo '[install $packageName]';")

                sb.append("pm install $backupPath$packageName.apk;")
            }
            if (data && File("$backupPath$packageName.tar.gz").exists()) {
                sb.append("echo '[restore $packageName]';")

                sb.append("pm clear $packageName;")
                sb.append("cd /data/data/$packageName;")
                sb.append("tar -xzf $backupPath$packageName.tar.gz;")
                sb.append("chown -R --reference=/data/data/$packageName *;")
            }
        }
        sb.append("echo '[operation completed]';")
        execShell(sb)
    }

    /**
     * 禁用所选的应用
     */
    private fun disableAll() {
        var sb = StringBuilder()
        for (item in apps) {
            val packageName = item.get("packageName").toString()
            sb.append("echo '[disable $packageName]';")

            sb.append("pm disable $packageName;")
        }

        sb.append("echo '[operation completed]';")
        execShell(sb)
    }

    /**
     * 启用所选的应用
     */
    private fun enableAll() {
        var sb = StringBuilder()
        for (item in apps) {
            val packageName = item.get("packageName").toString()
            sb.append("echo '[enable $packageName]';")

            sb.append("pm enable $packageName;")
        }

        sb.append("echo '[operation completed]';")
        execShell(sb)
    }

    /**
     * 隐藏所选的应用
     */
    private fun hideAll() {
        var sb = StringBuilder()
        for (item in apps) {
            val packageName = item.get("packageName").toString()
            sb.append("echo '[hide $packageName]';")

            sb.append("pm hide $packageName;")
        }

        sb.append("echo '[operation completed]';")
        execShell(sb)
    }

    /**
     * 删除选中的应用
     */
    private fun deleteAll () {
        var sb = StringBuilder()
        for (item in apps) {
            val packageName = item.get("packageName").toString()
            sb.append("echo '[delete $packageName]';")

            val dir = item.get("dir").toString()
            sb.append("rm -rf $dir;")
        }

        sb.append("echo '[operation completed]';")
        execShell(sb)
    }

    /**
     * 清除数据
     */
    private fun clearAll() {
        var sb = StringBuilder()
        for (item in apps) {
            val packageName = item.get("packageName").toString()
            sb.append("echo '[clear $packageName]';")

            sb.append("pm clear $packageName;")
        }

        sb.append("echo '[operation completed]';")
        execShell(sb)
    }

    /**
     * 清除缓存
     */
    private fun trimCachesAll() {
        var sb = StringBuilder()
        for (item in apps) {
            val packageName = item.get("packageName").toString()
            sb.append("echo '[trim caches $packageName]';")

            sb.append("pm trim-caches $packageName;")
        }

        sb.append("echo '[operation completed]';")
        execShell(sb)
    }

    /**
     * 卸载选中
     */
    private fun uninstallAll() {
        var sb = StringBuilder()
        for (item in apps) {
            val packageName = item.get("packageName").toString()
            sb.append("echo '[uninstall $packageName]';")

            sb.append("pm uninstall $packageName;")
        }

        sb.append("echo '[operation completed]';")
        execShell(sb)
    }

    /**
     * 卸载且保留数据
     */
    private fun uninstallKeepDataAll() {
        var sb = StringBuilder()
        for (item in apps) {
            val packageName = item.get("packageName").toString()
            sb.append("echo '[uninstall $packageName]';")

            sb.append("pm uninstall -k $packageName;")
        }

        sb.append("echo '[operation completed]';")
        execShell(sb)
    }
}
