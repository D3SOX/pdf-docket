package me.d3sox.pdfwallet.documents

import android.content.Context
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.matchers.collections.shouldBeEmpty
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShortcutsTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `a document without a name gets no recent shortcut`() {
        // e.g. imported from a file named ".pdf"
        context.addRecentShortcut("document.pdf", "")

        ShortcutManagerCompat.getDynamicShortcuts(context).shouldBeEmpty()
    }
}
