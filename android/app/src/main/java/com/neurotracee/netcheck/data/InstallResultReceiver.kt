package com.neurotracee.netcheck.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.widget.Toast

/**
 * 系统安装会话（PackageInstaller Session）的结果回调。
 *
 * 走 Session 安装时，系统把「要不要装」的确认界面、以及装完/装失败的状态，都通过这个 PendingIntent 回传。
 * 有了它，失败原因是系统给的原话（STATUS_FAILURE_INVALID、STATUS_FAILURE_CONFLICT 之类），
 * 而不是用户对着「安装包已损坏」猜。
 *
 * 只做提示，不改任何业务状态：真正的成败用户自己能看见。
 */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        when (status) {
            // 系统要用户确认：把确认界面拉起来（这一步不弹，安装会一直挂着）
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        ctx.startActivity(confirm)
                    } catch (e: Exception) {
                        toast(ctx, zh(ctx, "没能打开安装确认界面", "Could not open the install confirmation"))
                    }
                }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                // 装完把留下的安装包删掉，别在文件目录里占着 10MB
                UpdateApk.cleanAfterInstall(ctx)
            }

            else -> {
                val raw = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
                UpdateApk.cleanAfterInstall(ctx)
                val human = when (status) {
                    PackageInstaller.STATUS_FAILURE_ABORTED -> zh(ctx, "安装被取消", "Install cancelled")
                    PackageInstaller.STATUS_FAILURE_BLOCKED -> zh(ctx, "系统拦住了这次安装", "The system blocked this install")
                    PackageInstaller.STATUS_FAILURE_CONFLICT -> zh(ctx, "与已装版本冲突（签名不一致）", "Conflicts with the installed version (signature mismatch)")
                    PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> zh(ctx, "与这台机器不兼容", "Incompatible with this device")
                    PackageInstaller.STATUS_FAILURE_INVALID -> zh(ctx, "安装包无效", "Invalid package")
                    PackageInstaller.STATUS_FAILURE_STORAGE -> zh(ctx, "存储空间不够", "Not enough storage")
                    else -> zh(ctx, "安装没成功", "Install failed")
                }
                val detail = raw.takeIf { it.isNotBlank() }?.let { "（$it）" }.orEmpty()
                toast(ctx, human + detail)
            }
        }
    }

    private fun zh(ctx: Context, z: String, e: String): String = UpdateApk.trPublic(ctx, z, e)

    private fun toast(ctx: Context, msg: String) {
        try {
            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
        } catch (ignored: Exception) {
        }
    }
}
