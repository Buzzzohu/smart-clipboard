package com.smartclipboard.app.suggestion

/** Match the Android service identifier reported in the enabled-services list. */
internal object AccessibilityServiceIdentity {
    fun matches(id: String?, packageName: String, className: String): Boolean {
        if (id == null) return false
        val separator = id.indexOf('/')
        if (separator <= 0 || id.substring(0, separator) != packageName) return false
        val reportedClass = id.substring(separator + 1)
        val expandedClass = if (reportedClass.startsWith('.')) packageName + reportedClass else reportedClass
        return expandedClass == className
    }
}
