package me.d3sox.pdfwallet.pdfimporter

import android.content.Context
import android.net.Uri
import me.d3sox.pdfwallet.coroutines.dispatcher.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
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
    fun getFile(fileName: String): File
}

internal class FileImportRepoImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val documentNameRepo: DocumentNameRepo,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) : FileImportRepo {

    override suspend fun copyToApp(uri: Uri): PendingCertificate = withContext(dispatcher) {
        val fileName = "${UUID.randomUUID()}.pdf"
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(getFile(fileName)).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        } catch (e: IOException) {
            // e.g. a cloud provider aborted the download or the disk is full
            getFile(fileName).delete()
            throw e
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

    override fun getFile(fileName: String): File {
        return File(context.filesDir, fileName)
    }
}
