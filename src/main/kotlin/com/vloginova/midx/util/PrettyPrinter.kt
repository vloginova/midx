package com.vloginova.midx.util

enum class FontStyle(val value: String) {
    BOLD("\u001b[1;37m"),
    RED("\u001b[0;31m")
}

private const val RESET = "\u001b[0m"

// TODO: add non-UNIX support?
fun prettyPrint(text: String, vararg styles: FontStyle) {
    for (style in styles) print(style.value)
    print(text)
    print(RESET)
}