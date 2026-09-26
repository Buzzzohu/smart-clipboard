package com.smartclipboard.app.suggestion

import org.junit.Assert.*
import org.junit.Test

class AccessibilityServiceIdentityTest {
    private val pkg = "com.smartclipboard.app"
    private val service = "$pkg.suggestion.QqSuggestionService"

    @Test fun enabledServiceAcceptsFullAndShortAndroidComponentNames() {
        assertTrue(AccessibilityServiceIdentity.matches("$pkg/$service", pkg, service))
        assertTrue(AccessibilityServiceIdentity.matches("$pkg/.suggestion.QqSuggestionService", pkg, service))
    }

    @Test fun unrelatedOrMissingServicesNeverCountAsEnabled() {
        listOf(null, "", "$pkg/.OtherService", "other.app/$service", service).forEach {
            assertFalse(AccessibilityServiceIdentity.matches(it, pkg, service))
        }
    }
}
