package com.marketnewsmonitor.app.ui.onboarding

private val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

/** Pure so it's unit-testable without Robolectric; kept out of the Composable file it gates. */
fun isValidProfileEmail(email: String): Boolean = EMAIL_PATTERN.matches(email.trim())
