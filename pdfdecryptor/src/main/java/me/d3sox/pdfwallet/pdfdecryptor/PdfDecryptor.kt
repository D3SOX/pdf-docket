package me.d3sox.pdfwallet.pdfdecryptor

import me.d3sox.pdfwallet.coroutines.dispatcher.di.IoDispatcher
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

public interface PdfDecryptor {
    /** Returns false if the password is wrong. */
    @Throws(Exception::class)
    public suspend fun decrypt(password: String, file: File): Boolean
}

internal class PdfDecryptorImpl @Inject constructor(
    @IoDispatcher private val dispatcher: CoroutineDispatcher
): PdfDecryptor {

    @Suppress("SwallowedException")
    @Throws(Exception::class)
    override suspend fun decrypt(
        password: String,
        file: File,
    ): Boolean = withContext(dispatcher) {
        // PDFBox reads lazily from the file, so saving into it directly could corrupt the document
        val decrypted = File.createTempFile("decrypted", ".pdf", file.absoluteFile.parentFile)
        try {
            val document = try {
                PDDocument.load(file, password)
            } catch (e: InvalidPasswordException) {
                return@withContext false
            }
            document.use {
                it.isAllSecurityToBeRemoved = true
                decrypted.outputStream().use(it::save)
            }
            check(decrypted.renameTo(file)) { "Couldn't replace ${file.name} with its decrypted copy" }
            true
        } finally {
            decrypted.delete()
        }
    }
}
