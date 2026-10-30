package me.d3sox.pdfwallet.pdfimporter

import android.app.Application
import android.net.Uri
import me.d3sox.pdfwallet.logger.Logger
import me.d3sox.pdfwallet.pdfdecryptor.PdfDecryptor
import me.d3sox.pdfwallet.pdfrenderer.PdfRendererBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

public interface PdfImporter {
    public suspend fun preparePendingFile(uri: Uri): PdfImportResult
    public fun deletePendingFile()
    public suspend fun importPendingFile(password: String?): PdfImportResult
}

internal class PdfImporterImpl @Inject constructor(
    private val app: Application,
    private val fileRepo: FileImportRepo,
    private val pdfDecryptor: PdfDecryptor,
    private val logger: Logger,
) : PdfImporter {

    private val _pendingFile: MutableStateFlow<PendingCertificate?> = MutableStateFlow(
        null
    )
    private val pendingFile: StateFlow<PendingCertificate?> = _pendingFile

    @Suppress("TooGenericExceptionCaught")
    override suspend fun preparePendingFile(uri: Uri): PdfImportResult {
        deletePendingFile()
        return try {
            _pendingFile.value = fileRepo.copyToApp(uri)
            logger.logDebug(pendingFile.value)
            PdfImportResult.Success(pendingFile.value!!)
        } catch (e: Exception) {
            logger.logError(e.toString())
            PdfImportResult.ParsingError
        }
    }

    override fun deletePendingFile() {
        pendingFile.value?.let {
            _pendingFile.value = null
            fileRepo.deleteFile(it.fileName)
        }
    }

    override suspend fun importPendingFile(password: String?): PdfImportResult {
        return if (password == null) {
            importRegularPdf()
        } else {
            importPasswordProtectedPdf(password)
        }
    }

    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    private suspend fun importRegularPdf(): PdfImportResult {
        val pendingFile = pendingFile.value ?: return PdfImportResult.NoFileToImport
        try {
            val file = fileRepo.getFile(pendingFile.fileName)
            return if (pdfDecryptor.isPdfPasswordProtected(file)) {
                PdfImportResult.PasswordRequired
            } else {
                if (isValidPdf(pendingFile)) {
                    PdfImportResult.Success(pendingFile)
                } else {
                    deletePendingFile()
                    PdfImportResult.ParsingError
                }
            }
        } catch (e: Throwable) {
            logger.logError(e.toString())
            deletePendingFile()
            return PdfImportResult.ParsingError
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun importPasswordProtectedPdf(password: String): PdfImportResult {
        val pendingFile = pendingFile.value ?: return PdfImportResult.NoFileToImport
        return try {
            val file = fileRepo.getFile(pendingFile.fileName)
            when {
                !pdfDecryptor.decrypt(password = password, file = file) -> PdfImportResult.PasswordRequired
                isValidPdf(pendingFile) -> PdfImportResult.Success(pendingFile)
                else -> {
                    deletePendingFile()
                    PdfImportResult.ParsingError
                }
            }
        } catch (e: Exception) {
            // e.g. a full disk, which isn't the password's fault
            logger.logError(e.toString())
            deletePendingFile()
            PdfImportResult.ParsingError
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun isValidPdf(certificate: PendingCertificate): Boolean {
        val renderer = PdfRendererBuilder.create(
            app,
            fileName = certificate.fileName,
            renderContext = Dispatchers.IO
        )
        return try {
            renderer.loadFile()
            true
        } catch (e: Exception) {
            logger.logError(e.toString())
            fileRepo.deleteFile(certificate.fileName)
            false
        } finally {
            _pendingFile.value = null
            renderer.close()
        }
    }
}

public sealed class PdfImportResult {
    public data class Success(val pendingCertificate: PendingCertificate) : PdfImportResult()
    public data object PasswordRequired : PdfImportResult()
    public data object ParsingError : PdfImportResult()
    public data object NoFileToImport : PdfImportResult()
}
