package me.d3sox.pdfwallet

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.d3sox.pdfwallet.pdfrenderer.PdfRendererBuilder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PdfRendererTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext

    @Test
    fun pageThatCantBeRendered_isNull() = runTest {
        val file = File(context.filesDir, "test-renderer.pdf")
        instrumentation.context.assets.open("qr.pdf").use { input -> file.outputStream().use { input.copyTo(it) } }
        val renderer = PdfRendererBuilder.create(context, fileName = file.name, renderContext = Dispatchers.IO)
        try {
            renderer.renderPage(pageIndex = 0, highResolution = false).shouldNotBeNull()
            // the document has one page, so opening the second one throws
            renderer.renderPage(pageIndex = 1, highResolution = false).shouldBeNull()
        } finally {
            renderer.close()
            file.delete()
        }
    }

    @Test
    fun fileThatCantBeOpened_hasNoPages() = runTest {
        val renderer = PdfRendererBuilder.create(context, fileName = "missing.pdf", renderContext = Dispatchers.IO)
        try {
            renderer.renderPage(pageIndex = 0, highResolution = false).shouldBeNull()
        } finally {
            renderer.close()
        }
    }
}
