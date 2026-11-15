package me.d3sox.pdfwallet.pdfimporter

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

internal interface DocumentNameRepo {
    /** The file name without the extension, empty if it's unknown */
    suspend fun getDocumentName(uri: Uri): String
}

internal class DocumentNameRepoImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : DocumentNameRepo {

    override suspend fun getDocumentName(uri: Uri): String = withContext(Dispatchers.IO) {
        val fileName = when (uri.scheme) {
            ContentResolver.SCHEME_CONTENT -> displayName(uri)
            else -> uri.path?.let(::File)?.name
        }
        fileName?.removeSuffix(".pdf", ignoreCase = true).orEmpty()
    }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            cursor.takeIf { it.moveToFirst() }?.getString(0)
        }
    }.getOrNull()
}

private fun String.removeSuffix(suffix: String, ignoreCase: Boolean): String =
    if (endsWith(suffix, ignoreCase)) dropLast(suffix.length) else this
