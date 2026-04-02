package com.example.a2ui.chat

import android.content.Intent
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * UIAutomator instrumented tests that simulate the two real agent response scenarios using mock
 * data. Each test:
 *   1. Launches MainActivity fresh (clears the task stack).
 *   2. Locates the chat input field and types a message.
 *   3. Taps the Send button.
 *   4. Waits up to [RESPONSE_TIMEOUT_MS] for the mock AI response to appear in the UI.
 *   5. Asserts that key text values from the rendered UI card are visible on screen.
 *   6. Captures and saves a screenshot to external files storage.
 *
 * ────────────────────────────────────────────────────────────────────────
 * REQUIREMENTS
 * ────────────────────────────────────────────────────────────────────────
 * • The app must be built in **debug** mode (the default for instrumented tests).
 *   In debug mode, `BuildConfig.USE_REAL_AGENT = false`, so ChatViewModel routes all
 *   messages through MockChatRepository — no network connection is needed.
 *
 * • To retrieve screenshots after a run:
 *     adb exec-out run-as com.example.a2ui.chat \
 *         cat /data/user/0/com.example.a2ui.chat/files/screenshots/run1_transactions.png \
 *         > run1_transactions.png
 *   Or pull the whole directory:
 *     adb shell "run-as com.example.a2ui.chat ls files/screenshots/"
 *
 * ────────────────────────────────────────────────────────────────────────
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class A2UIChatUiTest {

    // ── Configuration ─────────────────────────────────────────────────────

    companion object {
        private const val TAG = "A2UIChatUiTest"
        private const val APP_PACKAGE = "com.example.a2ui.chat"

        /** Maximum time to wait for the mock AI response to render. */
        private const val RESPONSE_TIMEOUT_MS = 30_000L

        /** Time to wait for the activity to be fully ready after launch. */
        private const val LAUNCH_TIMEOUT_MS = 10_000L
    }

    private lateinit var device: UiDevice

    /**
     * Directory where screenshots are saved.
     * Path: /data/user/0/com.example.a2ui.chat/files/screenshots/
     * Pull with `adb exec-out run-as com.example.a2ui.chat cat files/screenshots/<name>.png > <name>.png`
     */
    private val screenshotDir: File by lazy {
        File(
            InstrumentationRegistry.getInstrumentation().targetContext.filesDir,
            "screenshots"
        )
    }

    // ── Setup ─────────────────────────────────────────────────────────────

    @Before
    fun setUp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        device = UiDevice.getInstance(instrumentation)
        screenshotDir.mkdirs()

        // Launch the app fresh, clearing any previous task.
        val context = instrumentation.targetContext
        val intent = context.packageManager.getLaunchIntentForPackage(APP_PACKAGE)
            ?.apply { addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK) }
            ?: error("Cannot find launch intent for $APP_PACKAGE")
        context.startActivity(intent)

        // Wait until the chat input placeholder is visible — signals the activity is fully ready.
        val ready = device.wait(Until.hasObject(By.text("Chat with Claude")), LAUNCH_TIMEOUT_MS)
        assertTrue("Activity did not launch within ${LAUNCH_TIMEOUT_MS}ms", ready)
        device.waitForIdle()
    }

    // ── Tests ─────────────────────────────────────────────────────────────

    /**
     * Run 1 — "show my last months transactions"
     *
     * Expects the mock to return TransactionHistorySurface (surfaceId=response_run1) with:
     *   • Summary text : "Here are your transactions from March 2026 — 14 transactions total."
     *   • Card title   : "March 2026 Transactions"
     *   • Period       : "Mar 1 – Mar 31, 2026"
     *   • Count        : "14 transactions"
     *   • First row    : "Direct Deposit – Employer Payroll" / "+$4,250.00"
     */
    @Test
    fun run1_showTransactions_rendersUiCard() {
        typeAndSend("show my last months transactions")

        // ── Assert summary text ────────────────────────────────────────
        val summaryVisible = device.wait(
            Until.hasObject(By.textContains("14 transactions total")),
            RESPONSE_TIMEOUT_MS
        )
        assertTrue("Summary text '14 transactions total' did not appear", summaryVisible)

        // ── Assert card header values ──────────────────────────────────
        assertTextVisible("March 2026 Transactions")
        assertTextVisible("Mar 1 – Mar 31, 2026")
        assertTextVisible("14 transactions")

        // ── Assert at least one transaction row ────────────────────────
        assertTextVisible("+\$4,250.00")

        // ── Screenshot ────────────────────────────────────────────────
        takeScreenshot("run1_transactions.png")
    }

    /**
     * Run 2 — "show my account balances"
     *
     * Expects the mock to return AccountBalancesSurface (surfaceId=response_run2) with:
     *   • Summary text   : "Here's a summary of all your accounts…"
     *   • Banking label  : "BANKING"  / total "$24,580.47"
     *   • Checking       : "Premier Checking"  / "$8,214.63"
     *   • Investing label: "INVESTING" / total "$198,342.11"
     *   • Net worth      : "TOTAL NET WORTH"   / "$222,922.58"
     */
    @Test
    fun run2_showAccountBalances_rendersUiCard() {
        typeAndSend("show my account balances")

        // ── Assert summary text ────────────────────────────────────────
        val summaryVisible = device.wait(
            Until.hasObject(By.textContains("summary of all your accounts")),
            RESPONSE_TIMEOUT_MS
        )
        assertTrue("Summary text 'summary of all your accounts' did not appear", summaryVisible)

        // ── Assert section labels ──────────────────────────────────────
        assertTextVisible("BANKING")
        assertTextVisible("INVESTING")
        assertTextVisible("TOTAL NET WORTH")

        // ── Assert key balance values ──────────────────────────────────
        assertTextVisible("\$222,922.58")   // total net worth
        assertTextVisible("Premier Checking")

        // ── Screenshot ────────────────────────────────────────────────
        takeScreenshot("run2_balances.png")
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /**
     * Locates the chat input field, sets [message] as its content, then taps the Send button.
     *
     * Strategy:
     *   1. `By.text("Chat with Claude")` finds the placeholder Text node in the Compose tree.
     *   2. Tapping that node positions a click at the same coordinates as the BasicTextField,
     *      causing Compose to deliver focus to the BasicTextField (it sits on top in the same Box).
     *   3. `By.focused(true)` then finds the now-focused BasicTextField node.
     *   4. `UiObject2.setText()` dispatches `ACTION_SET_TEXT`, which Compose routes to
     *      `SemanticsActions.SetText`, updating the internal `mutableStateOf<String>`.
     *   5. Once text is non-blank, `isSendEnabled` becomes true; we tap the Send IconButton.
     */
    private fun typeAndSend(message: String) {
        // Tap the placeholder hint to focus the BasicTextField.
        val hint = device.wait(Until.findObject(By.text("Chat with Claude")), 5_000)
            ?: error("Placeholder 'Chat with Claude' not found — is the app launched?")
        hint.click()
        device.waitForIdle()
        Thread.sleep(300) // allow Compose focus propagation

        // Find the focused (now-editable) node and set the message text.
        val inputNode = device.findObject(By.focused(true))
            ?: device.findObject(By.text("Chat with Claude")) // fallback before text is set
        inputNode?.setText(message)
            ?: error("Could not locate focused input node for message: \"$message\"")
        device.waitForIdle()

        // Click Send (IconButton whose Icon has contentDescription = "Send").
        val sendBtn = device.wait(Until.findObject(By.desc("Send")), 3_000)
            ?: error("Send button not found after setting message text")
        sendBtn.click()
        device.waitForIdle()

        Log.i(TAG, "typeAndSend: sent \"$message\"")
    }

    /**
     * Asserts that [text] appears anywhere in the current view hierarchy within a short poll
     * window. Fails the test with a descriptive message if absent.
     */
    private fun assertTextVisible(text: String, timeoutMs: Long = 5_000) {
        val found = device.wait(Until.hasObject(By.text(text)), timeoutMs)
        assertTrue("Expected text not visible: \"$text\"", found)
    }

    /**
     * Takes a screenshot via [UiDevice] and saves it to [screenshotDir]/[filename].
     *
     * To pull the file after the test run:
     *   adb exec-out run-as com.example.a2ui.chat cat files/screenshots/<filename> > <filename>
     */
    private fun takeScreenshot(filename: String) {
        val file = File(screenshotDir, filename)
        val success = device.takeScreenshot(file)
        if (success) {
            Log.i(TAG, "Screenshot saved: ${file.absolutePath}")
        } else {
            Log.w(TAG, "Screenshot FAILED for: ${file.absolutePath}")
        }
    }
}
