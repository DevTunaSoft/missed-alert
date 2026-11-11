package com.crewalert.missedalert

/**
 * IT diagnostic entry point, independent of Knox headless launcher configuration.
 *
 * This Activity is exported only to callers holding android.permission.DUMP
 * (the Android shell/system). It has no LAUNCHER intent filter and is not
 * available to ordinary kiosk applications.
 *
 * IT test command:
 * adb shell am start -n com.crewalert.missedalert/.ItDiagnosticsActivity
 */
class ItDiagnosticsActivity : StatusActivity()
