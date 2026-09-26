package com.smartclipboard.app.suggestion

/** Explicit allowlist: input from every other application is ignored. */
internal enum class SuggestionApp(val packageName: String) {
    QQ("com.tencent.mobileqq"),
    HEYBOX("com.max.xiaoheihe"),
    BILIBILI("tv.danmaku.bili"),
    DOUYIN("com.ss.android.ugc.aweme"),
    JMCOMIC2("com.jiaohua_browser"),
    JMCOMIC3("com.a7m3p9xv.t6qk2z8.app");

    companion object {
        fun fromPackage(packageName: String?): SuggestionApp? =
            entries.firstOrNull { it.packageName == packageName }
    }
}
