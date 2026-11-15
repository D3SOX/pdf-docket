package me.d3sox.pdfwallet.pdfdecryptor

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PdfDecryptorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val decryptor = PdfDecryptorImpl(Dispatchers.Unconfined)

    private fun copyOf(name: String): File =
        File("../testdata_generic/$name").copyTo(tempFolder.root.resolve(name))

    @Suppress("SwallowedException")
    private fun File.isPasswordProtected(): Boolean = try {
        PDDocument.load(this).close()
        false
    } catch (e: InvalidPasswordException) {
        true
    }

    @Test
    fun decrypt_replacesFileWithUnprotectedCopy() = runTest {
        val file = copyOf("password.pdf")

        decryptor.decrypt("test", file) shouldBe true

        file.isPasswordProtected() shouldBe false
        tempFolder.root.list()!!.toList() shouldBe listOf("password.pdf")
    }

    @Test
    fun decrypt_keepsFileOnWrongPassword() = runTest {
        val file = copyOf("password.pdf")

        decryptor.decrypt("wrong", file) shouldBe false

        file.isPasswordProtected() shouldBe true
        tempFolder.root.list()!!.toList() shouldBe listOf("password.pdf")
    }
}
