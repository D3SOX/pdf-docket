package com.michaeltroger.gruenerpass.pdfdecryptor

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

    @Test
    fun isPdfPasswordProtected_detectsProtectedPdf() = runTest {
        decryptor.isPdfPasswordProtected(copyOf("password.pdf")) shouldBe true
        decryptor.isPdfPasswordProtected(copyOf("demo.pdf")) shouldBe false
    }

    @Test
    fun decrypt_replacesFileWithUnprotectedCopy() = runTest {
        val file = copyOf("password.pdf")

        decryptor.decrypt("test", file)

        decryptor.isPdfPasswordProtected(file) shouldBe false
        tempFolder.root.list()!!.toList() shouldBe listOf("password.pdf")
    }
}
