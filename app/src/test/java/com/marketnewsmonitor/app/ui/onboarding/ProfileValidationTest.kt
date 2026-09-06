package com.marketnewsmonitor.app.ui.onboarding

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileValidationTest {

    @Test
    fun `accepts a plausible email address`() {
        assertTrue(isValidProfileEmail("shreejit@example.com"))
    }

    @Test
    fun `trims surrounding whitespace before validating`() {
        assertTrue(isValidProfileEmail("  shreejit@example.com  "))
    }

    @Test
    fun `rejects blank input`() {
        assertFalse(isValidProfileEmail(""))
    }

    @Test
    fun `rejects an address missing a domain`() {
        assertFalse(isValidProfileEmail("shreejit@"))
    }

    @Test
    fun `rejects an address missing the at sign`() {
        assertFalse(isValidProfileEmail("shreejit.example.com"))
    }

    @Test
    fun `rejects an address with a space`() {
        assertFalse(isValidProfileEmail("shreejit @example.com"))
    }
}
