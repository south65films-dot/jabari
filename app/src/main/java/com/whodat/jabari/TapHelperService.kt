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

    // Google Translate's live-listening button.
    private val transcribeTargets = listOf("transcribe", "listen")

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString()

        // 1) Just after the wake word: tap Claude's voice button.
        if (Wake.isFresh() && pkg == Config.CLAUDE_PACKAGE) {
            tapIn(Config.CLAUDE_PACKAGE, targets) { Wake.tapped = true }
            return
        }

        // 2) Just after "Interpret Jabari": tap Translate's Transcribe button.
        if (Wake.isTranslateFresh() && pkg == Config.TRANSLATE_PACKAGE) {
            tapIn(Config.TRANSLATE_PACKAGE, transcribeTargets) { Wake.translateTapped = true }
            return
        }

        // 3) Left Translate after using it -> tell Jabari to resume listening.
        //    Only the package name is read here, never another app's content.
        if (Wake.inTranslate && pkg != null &&
            pkg != Config.TRANSLATE_PACKAGE && pkg != packageName) {
            Wake.inTranslate = false
            ListenerService.resumeExternal()
        }
    }

    private fun tapIn(pkg: String, words: List<String>, onTapped: () -> Unit) {
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != pkg) return
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        collect(root, nodes)
        saveScreenDump(nodes)
        for (word in words) {
            val match = nodes.firstOrNull { label(it).contains(word) } ?: continue
            if (click(match)) { onTapped(); Log.i(TAG, "Tapped \"${label(match)}\""); return }
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
