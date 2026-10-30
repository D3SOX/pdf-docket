package me.d3sox.pdfwallet

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.d3sox.pdfwallet.coroutines.dispatcher.di.CoroutineDispatchersModule
import me.d3sox.pdfwallet.db.di.DatabaseModule
import me.d3sox.pdfwallet.pdfimporter.PdfImportResult
import me.d3sox.pdfwallet.pdfimporter.PdfImporter
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject

@HiltAndroidTest
@UninstallModules(DatabaseModule::class, CoroutineDispatchersModule::class)
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class)
class PdfImporterRobolectricTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var importer: PdfImporter
    @Inject @ApplicationContext lateinit var context: Context

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun interruptedCopy_leavesNoFileBehind() = runTest {
        val uri = Uri.parse("content://cloud/ticket.pdf")
        // e.g. a cloud provider that aborts the download
        val interrupted = object : InputStream() {
            private var remaining = 1024

            override fun read(): Int = if (remaining-- > 0) 0 else throw IOException("Connection lost")
        }
        shadowOf(context.contentResolver).registerInputStream(uri, interrupted)

        importer.preparePendingFile(uri) shouldBe PdfImportResult.ParsingError

        context.filesDir.listFiles().orEmpty().filter { it.name.endsWith(".pdf") }.shouldBeEmpty()
    }
}
