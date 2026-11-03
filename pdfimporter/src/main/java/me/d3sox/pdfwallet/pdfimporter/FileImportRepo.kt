package me.d3sox.pdfwallet.pdfimporter

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import me.d3sox.pdfwallet.coroutines.dispatcher.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

internal interface FileImportRepo {
    suspend fun copyToApp(uri: Uri): PendingCertificate
    fun deleteFile(fileName: String)
    suspend fun deleteSource(uri: Uri): Boolean
    fun getFile(fileName: String): File
}

internal class FileImportRepoImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val documentNameRepo: DocumentNameRepo,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) : FileImportRepo {

    override suspend fun copyToApp(uri: Uri): PendingCertificate = withContext(dispatcher) {
        val fileName = "${UUID.randomUUID()}.pdf"
        var copied = false
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(getFile(fileName)).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            copied = true
        } finally {
            // e.g. a cloud provider aborted the download or the disk is full
            if (!copied) getFile(fileName).delete()
        }
        val docName = documentNameRepo.getDocumentName(uri).removeSuffix(".pdf")
        return@withContext PendingCertificate(fileName = fileName, documentName = docName)
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun deleteFile(fileName: String) {
        GlobalScope.launch(dispatcher) {
            if (getFile(fileName).exists()) {
                getFile(fileName).delete()
            }
        }
    }

    override suspend fun deleteSource(uri: Uri): Boolean = withContext(dispatcher) {
        // a document shared back to this app is the app's own copy
        if (uri.scheme != ContentResolver.SCHEME_CONTENT || uri.authority == "${context.packageName}.pdffileprovider") {
            return@withContext false
        }
        // e.g. the app that shared the file only granted read access
        runCatching {
            if (DocumentsContract.isDocumentUri(context, uri)) {
                DocumentsContract.deleteDocument(context.contentResolver, uri)
            } else {
                context.contentResolver.delete(uri, null, null) > 0
            }
        }.getOrDefault(false)
    }

    override fun getFile(fileName: String): File {
        return File(context.filesDir, fileName)
    }
}
