package com.vloginova.midx

internal enum class FontStyle(val value: String) {
    BOLD("\u001b[1;37m"),
    RED("\u001b[0;31m")
}

private const val RESET = "\u001b[0m"

/**
 * A simple pretty printer just to be used in demo.
 */
internal fun prettyPrint(text: String, vararg styles: FontStyle) {
    if (System.getProperty("os.name").startsWith("Windows")) {
        // Nothing will be pretty on Windows
        print(text)
        return
    }

    for (style in styles) print(style.value)
    print(text)
    print(RESET)
}