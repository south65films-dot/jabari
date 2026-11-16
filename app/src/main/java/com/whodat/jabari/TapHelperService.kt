package com.whodat.jabari

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File

/**
 * Right after the wake word, finds Claude's voice button and taps it.
 * Does nothing at any other time, and only sees the Claude app.
 */
class TapHelperService : AccessibilityService() {

    // Labels to look for, best match first. Adjust if a Claude update renames the button.
    private val targets = listOf("voice mode", "start voice", "voice", "talk", "microphone")

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!Wake.isFresh()) return
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != Config.CLAUDE_PACKAGE) return

        val nodes = mutableListOf<AccessibilityNodeInfo>()
        collect(root, nodes)
        saveScreenDump(nodes)

        for (word in targets) {
            val match = nodes.firstOrNull { label(it).contains(word) } ?: continue
            if (click(match)) {
                Wake.tapped = true
                Log.i(TAG, "Tapped \"${label(match)}\"")
                return
            }
        }
    }

    private fun label(n: AccessibilityNodeInfo): String =
        ((n.contentDescription ?: "").toString() + " " + (n.text ?: "").toString()).lowercase()

    private fun collect(n: AccessibilityNodeInfo?, out: MutableList<AccessibilityNodeInfo>) {
        if (n == null) return
        out.add(n)
        for (i in 0 until n.childCount) collect(n.getChild(i), out)
    }

    private fun click(n: AccessibilityNodeInfo): Boolean {
        var cur: AccessibilityNodeInfo? = n
        while (cur != null) {
            if (cur.isClickable) return cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            cur = cur.parent
        }
        return false
    }

    /** Saves the button labels on screen so they can be checked if no tap happens. */
    private fun saveScreenDump(nodes: List<AccessibilityNodeInfo>) {
        try {
            val lines = nodes.mapNotNull { n ->
                val l = label(n).trim()
                if (l.isEmpty()) null else "${if (n.isClickable) "[tap]" else "     "} $l"
            }
            File(filesDir, "last_screen.txt").writeText(lines.joinToString("\n"))
        } catch (_: Exception) { }
    }

    override fun onInterrupt() {}

    companion object { private const val TAG = "JabariTap" }
}
