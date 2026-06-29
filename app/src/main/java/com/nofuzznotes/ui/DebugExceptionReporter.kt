package com.nofuzznotes.ui

import android.app.Activity
import android.app.AlertDialog
import android.widget.ScrollView
import android.widget.TextView
import java.io.PrintWriter
import java.io.StringWriter

object DebugExceptionReporter {
    // Install a last-chance reporter because debug failures should be visible before the process is inspected.
    fun install(activity: Activity) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            report(activity, throwable)
            check(previous !== Thread.getDefaultUncaughtExceptionHandler()) { "Debug exception handler must not delegate to itself" }
            previous?.uncaughtException(thread, throwable)
        }
    }

    // Show the throwable on the UI thread because Android dialogs must be attached from the activity window.
    fun report(activity: Activity, throwable: Throwable) {
        activity.runOnUiThread { showDialog(activity, throwable) }
    }

    // Build a selectable text dialog because stack traces must be copyable for debugging reports.
    private fun showDialog(activity: Activity, throwable: Throwable) {
        val message = throwable.stackTraceText()
        val text = TextView(activity).apply {
            setText(message)
            setTextIsSelectable(true)
            setPadding(32, 24, 32, 24)
        }
        val scroll = ScrollView(activity).apply { addView(text) }
        AlertDialog.Builder(activity)
            .setTitle("Exception")
            .setView(scroll)
            .setPositiveButton("OK", null)
            .show()
    }
}

// Render the full exception because message-only failures hide the code path that caused the bug.
fun Throwable.stackTraceText(): String {
    val writer = StringWriter()
    printStackTrace(PrintWriter(writer))
    return writer.toString()
}
