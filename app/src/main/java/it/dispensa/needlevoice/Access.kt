package it.dispensa.needlevoice

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/** Occhi e mani di Needle dentro qualsiasi app (attivabile in Impostazioni, Accessibilità). */
class NeedleAccess : AccessibilityService() {
    companion object {
        @Volatile var inst: NeedleAccess? = null
        val on get() = inst != null
    }
    private class El(val node: AccessibilityNodeInfo, val label: String, val kind: String, val r: Rect, val clickable: Boolean, val editable: Boolean)
    private var last = listOf<El>()

    override fun onServiceConnected() { inst = this }
    override fun onUnbind(i: android.content.Intent?): Boolean { inst = null; return super.onUnbind(i) }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun pkg(): String = rootInActiveWindow?.packageName?.toString().orEmpty()

    /** Elenco numerato degli elementi visibili, da passare al modello. */
    fun snapshot(max: Int = 70): String {
        val root = rootInActiveWindow ?: return "(schermo non leggibile)"
        val out = ArrayList<El>()
        fun walk(n: AccessibilityNodeInfo?, d: Int) {
            if (n == null || out.size > 220 || d > 30) return
            if (n.isVisibleToUser) {
                val label = listOf(n.text?.toString(), n.contentDescription?.toString(), n.hintText?.toString(), n.viewIdResourceName?.substringAfter('/')?.replace('_', ' ')).firstOrNull { !it.isNullOrBlank() }.orEmpty().take(60)
                val clickable = n.isClickable || n.isCheckable; val ed = n.isEditable
                if (label.isNotBlank() || clickable || ed) { val r = Rect(); n.getBoundsInScreen(r); out.add(El(n, label, n.className?.toString()?.substringAfterLast('.').orEmpty(), r, clickable, ed)) }
            }
            for (i in 0 until n.childCount) walk(n.getChild(i), d + 1)
        }
        walk(root, 0)
        last = out
        val sb = StringBuilder("App: ${root.packageName}\n")
        out.take(max).forEachIndexed { i, e -> sb.append("[$i] ${e.kind}").append(if (e.editable) " (campo testo)" else if (e.clickable) " (tocca)" else "").append(": ").append(e.label.ifBlank { "-" }).append('\n') }
        return sb.toString()
    }

    private fun find(id: Int?, text: String?): El? {
        if (last.isEmpty()) snapshot()
        if (id != null && id in last.indices) return last[id]
        if (!text.isNullOrBlank()) return last.filter { it.label.isNotBlank() }.map { it to Fuzzy.sim(text, it.label) }.filter { it.second >= 0.6 }.maxByOrNull { it.second + if (it.first.clickable) 0.05 else 0.0 }?.first
        return null
    }

    fun click(id: Int?, text: String?): Boolean {
        val e = find(id, text) ?: return false
        var n: AccessibilityNodeInfo? = e.node
        while (n != null && !n.isClickable) n = n.parent
        if (n != null && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        return tap(e.r.centerX().toFloat(), e.r.centerY().toFloat())
    }

    fun tap(x: Float, y: Float): Boolean {
        val p = Path().apply { moveTo(x, y) }
        return dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p, 0, 60)).build(), null, null)
    }

    fun type(text: String, id: Int?): Boolean {
        val target = (if (id != null) find(id, null)?.node else null) ?: rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: last.firstOrNull { it.editable }?.node ?: return false
        target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) })
    }

    fun enter(): Boolean {
        val f = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: last.firstOrNull { it.editable }?.node
        if (f != null && android.os.Build.VERSION.SDK_INT >= 30 && f.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)) return true
        return false
    }

    private fun scrollable(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (n == null) return null
        if (n.isScrollable) return n
        for (i in 0 until n.childCount) scrollable(n.getChild(i))?.let { return it }
        return null
    }

    fun scroll(down: Boolean): Boolean {
        val s = scrollable(rootInActiveWindow) ?: return false
        return s.performAction(if (down) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
    }

    fun back() = performGlobalAction(GLOBAL_ACTION_BACK)
    fun home() = performGlobalAction(GLOBAL_ACTION_HOME)
    fun recents() = performGlobalAction(GLOBAL_ACTION_RECENTS)
    fun notifications() = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
}
