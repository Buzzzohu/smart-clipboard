package com.smartclipboard.app.suggestion

/** Explicit allowlist: input from every other application is ignored. */
internal enum class SuggestionApp(val packageName: String) {
    QQ("com.tencent.mobileqq"),
    HEYBOX("com.max.xiaoheihe");

    companion object {
        fun fromPackage(packageName: String?): SuggestionApp? =
            entries.firstOrNull { it.packageName == packageName }
    }
}
