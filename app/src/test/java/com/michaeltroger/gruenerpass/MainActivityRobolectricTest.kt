package com.michaeltroger.gruenerpass

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.michaeltroger.gruenerpass.coroutines.dispatcher.di.CoroutineDispatchersModule
import com.michaeltroger.gruenerpass.db.di.DatabaseModule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
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
}
