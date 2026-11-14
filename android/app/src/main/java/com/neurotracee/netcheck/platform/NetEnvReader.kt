package com.neurotracee.netcheck.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.neurotracee.netcheck.model.NetEnv

/**
 * 采集本机网络环境，对应桌面端 Get-NetEnv 的四项检查。
 *
 * Android 与 Windows 能拿到的信息不一样，做了等价替换并写在文档里：
 *  - 系统代理  → ConnectivityManager.getDefaultProxy()
 *  - hosts     → 读 /system/etc/hosts（Android 上通常为空）
 *  - 本机 443  → Android 不允许普通应用枚举端口，改为看「私有 DNS」与「VPN」
 *  - DNS 服务器 → LinkProperties.getDnsServers()
 */
class NetEnvReader(private val context: Context) {

    fun read(): NetEnv {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return NetEnv()

        val proxy = runCatching { cm.defaultProxy }.getOrNull()
        val network = cm.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }
        val props = network?.let { cm.getLinkProperties(it) }

        val vpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        val transport = when {
            vpn -> "VPN"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "移动数据"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "以太网"
            else -> ""
        }

        val dnsServers = props?.dnsServers?.mapNotNull { it.hostAddress } ?: emptyList()

        val privateDns = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val active = props?.isPrivateDnsActive == true
            val name = props?.privateDnsServerName.orEmpty()
            when {
                !active -> "关闭"
                active && name.isEmpty() -> "自动"
                else -> name
            }
        } else {
            ""
        }

        return NetEnv(
            proxyEnabled = proxy != null,
            proxyServer = proxy?.let { "${it.host}:${it.port}" }.orEmpty(),
            vpnActive = vpn,
            privateDns = privateDns,
            dnsServers = dnsServers,
            hostsHits = countHostsHits(),
            networkName = transport
        )
    }

    /** 与桌面端同款规则：只看指向本机（127.0.0.1 / 0.0.0.0 / ::1）的行。 */
    private fun countHostsHits(): Int {
        val file = java.io.File("/system/etc/hosts")
        if (!file.canRead()) return 0
        return runCatching {
            file.readLines().count { line ->
                val t = line.trim()
                t.isNotEmpty() && !t.startsWith("#") &&
                    Regex("^(127\\.0\\.0\\.1|0\\.0\\.0\\.0|::1)\\s+\\S+").containsMatchIn(t)
            }
        }.getOrDefault(0)
    }
}
