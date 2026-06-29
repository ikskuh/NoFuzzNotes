package com.nofuzznotes.ui

import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.nofuzznotes.MainActivity
import org.hamcrest.CoreMatchers.allOf
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugExceptionReporterTest {
    // Verify thrown exceptions produce copyable detail because debugging needs the message and stack path.
    @Test
    fun thrownExceptionCanBeReportedInCopyableDialog() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                try {
                    throw IllegalStateException("debug failure")
                } catch (throwable: Throwable) {
                    DebugExceptionReporter.report(activity, throwable)
                }
            }

            onView(withText("Exception")).inRoot(isDialog()).check(matches(isDisplayed()))
            onView(withText(org.hamcrest.Matchers.containsString("IllegalStateException: debug failure"))).inRoot(isDialog()).check(matches(isDisplayed()))
            onView(withText(org.hamcrest.Matchers.containsString("DebugExceptionReporterTest.thrownExceptionCanBeReportedInCopyableDialog"))).inRoot(isDialog()).check(matches(isDisplayed()))
            onView(allOf(isAssignableFrom(TextView::class.java), withText(org.hamcrest.Matchers.containsString("debug failure"))))
                .inRoot(isDialog())
                .check { view, _ -> assertTrue((view as TextView).isTextSelectable) }
        }
    }
}
