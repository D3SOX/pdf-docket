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
    /** Deletes the file a document was imported from, returns whether it worked. */
    public suspend fun deleteSourceFile(uri: Uri): Boolean
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
        val pendingFile = pendingFile.value ?: return PdfImportResult.NoFileToImport
        return if (password == null) {
            open(pendingFile)
        } else {
            importPasswordProtectedPdf(pendingFile, password)
        }
    }

    override suspend fun deleteSourceFile(uri: Uri): Boolean = fileRepo.deleteSource(uri)

    @Suppress("TooGenericExceptionCaught")
    private suspend fun importPasswordProtectedPdf(pendingFile: PendingCertificate, password: String): PdfImportResult {
        return try {
            val file = fileRepo.getFile(pendingFile.fileName)
            if (!pdfDecryptor.decrypt(password = password, file = file)) return PdfImportResult.PasswordRequired
            // the renderer can't open a document that is still protected
            open(pendingFile).takeIf { it != PdfImportResult.PasswordRequired } ?: run {
                deletePendingFile()
                PdfImportResult.ParsingError
            }
        } catch (e: Exception) {
            // e.g. a full disk, which isn't the password's fault
            failed(e)
        } catch (e: OutOfMemoryError) {
            // PDFBox keeps the whole document in memory
            failed(e)
        }
    }

    /**
     * Opens the pending file with Android's renderer, which shows the documents later. Only protected PDFs need PDFBox,
     * which can't read some PDFs that the renderer shows fine.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun open(pendingFile: PendingCertificate): PdfImportResult {
        val renderer = PdfRendererBuilder.create(app, fileName = pendingFile.fileName, renderContext = Dispatchers.IO)
        return try {
            renderer.loadFile()
            _pendingFile.value = null
            PdfImportResult.Success(pendingFile)
        } catch (e: SecurityException) {
            // the renderer's way of saying that the document needs a password
            PdfImportResult.PasswordRequired
        } catch (e: Exception) {
            failed(e)
        } catch (e: OutOfMemoryError) {
            failed(e)
        } finally {
            renderer.close()
        }
    }

    private fun failed(e: Throwable): PdfImportResult {
        logger.logError(e.toString())
        deletePendingFile()
        return PdfImportResult.ParsingError
    }
}

public sealed class PdfImportResult {
    public data class Success(val pendingCertificate: PendingCertificate) : PdfImportResult()
    public data object PasswordRequired : PdfImportResult()
    public data object ParsingError : PdfImportResult()
    public data object NoFileToImport : PdfImportResult()
}
