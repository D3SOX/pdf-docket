package me.d3sox.pdfwallet.documents

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

private const val PDF_MIME_TYPE = "application/pdf"

fun Context.sharePdfs(documents: List<Document>) {
    val uris = documents.map {
        FileProvider.getUriForFile(
            this,
            "$packageName.pdffileprovider",
            File(filesDir, it.id),
            "${it.name}.pdf",
        )
    }
    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.single())
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
    }
    intent.type = PDF_MIME_TYPE
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    startActivity(Intent.createChooser(intent, null))
}
