package com.neurotracee.netcheck.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import com.neurotracee.netcheck.BuildConfig
import com.neurotracee.netcheck.model.RunResult
import java.io.File

/**
 * 报告落盘与分享。
 * 文件写到应用私有目录 files/reports（不需要任何存储权限），
 * 再通过 FileProvider 交给系统分享面板 / 浏览器。
 */
class ReportStore(private val context: Context) {

    fun dir(): File = File(context.filesDir, "reports").apply { if (!exists()) mkdirs() }

    fun saveHtml(run: RunResult, stamp: String = ReportBuilder.fileStamp(run.startedAtMillis)): File {
        val file = File(dir(), "netcheck-$stamp.html")
        val html = ReportBuilder.html(run, BuildConfig.VERSION_NAME)
        file.writeText(html, Charsets.UTF_8)
        runCatching { File(dir(), "latest.html").writeText(html, Charsets.UTF_8) }
        return file
    }

    fun saveCsv(run: RunResult, stamp: String = ReportBuilder.fileStamp(run.startedAtMillis)): File {
        val file = File(dir(), "netcheck-$stamp.csv")
        // 带 BOM：Excel 打开 CSV 才认得出 UTF-8，中文列名不会变乱码（与桌面端 Export-Csv 行为一致）
        file.outputStream().use { out ->
            out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            out.write(ReportBuilder.csv(run).toByteArray(Charsets.UTF_8))
        }
        return file
    }

    fun uriOf(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /**
     * 写进用户在设置里选的文件夹（SAF 授权的目录树）。
     * 不用额外的 documentfile 依赖，直接走 DocumentsContract；失败返回 null，由调用方回退到分享面板。
     */
    fun saveToTree(treeUri: Uri, displayName: String, mime: String, bytes: ByteArray): Uri? {
        return runCatching {
            val docUri = DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri)
            )
            val child = DocumentsContract.createDocument(context.contentResolver, docUri, mime, displayName)
                ?: return null
            context.contentResolver.openOutputStream(child)?.use { it.write(bytes) }
            child
        }.getOrNull()
    }

    fun shareIntent(file: File, mime: String, title: String): Intent {
        val uri = uriOf(file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "分享报告").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /** 用系统里能打开 HTML 的应用（一般是浏览器）看报告。 */
    fun openIntent(file: File): Intent? {
        val uri = uriOf(file)
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "text/html")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return if (view.resolveActivity(context.packageManager) != null) view else null
    }
}
