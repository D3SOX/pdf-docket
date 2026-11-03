package me.d3sox.pdfwallet.documents

import android.content.Context
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShortcutsTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun recentLabels() = ShortcutManagerCompat.getDynamicShortcuts(context)
        .sortedBy { it.rank }
        .map { it.shortLabel.toString() }

    @Test
    fun `the most recently shown document comes first`() {
        context.addRecentShortcut("1.pdf", "Ticket")
        context.addRecentShortcut("2.pdf", "Boarding pass")

        recentLabels() shouldContainExactly listOf("Boarding pass", "Ticket")
    }

    @Test
    fun `a document without a name gets no recent shortcut`() {
        // e.g. imported from a file named ".pdf"
        context.addRecentShortcut("1.pdf", "")

        recentLabels().shouldBeEmpty()
    }
}
