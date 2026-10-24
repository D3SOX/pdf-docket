package me.d3sox.pdfwallet

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import me.d3sox.pdfwallet.coroutines.dispatcher.di.CoroutineDispatchersModule
import me.d3sox.pdfwallet.db.di.DatabaseModule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@HiltAndroidTest
@UninstallModules(DatabaseModule::class, CoroutineDispatchersModule::class)
@RunWith(RobolectricTestRunner::class)
// hiding overlay windows needs a permission that Robolectric doesn't grant on Android 12+
@Config(application = HiltTestApplication::class, sdk = [30])
class MainActivityRobolectricTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun openApp_showsEmptyState() {
        val context = composeRule.activity
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(androidx.compose.ui.test.hasText(context.getString(R.string.import_pdf)))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(context.getString(R.string.empty_state_title)).assertIsDisplayed()
    }

    @Test
    fun launcher_hasOneEntryPointingToMainActivity() {
        val context = composeRule.activity
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(context.packageName)

        val launcher = context.packageManager.queryIntentActivities(intent, 0)
            .map { it.activityInfo }
            .single { it.name == MainActivity::class.java.name || it.targetActivity == MainActivity::class.java.name }

        launcher.targetActivity shouldBe MainActivity::class.java.name
    }
}
