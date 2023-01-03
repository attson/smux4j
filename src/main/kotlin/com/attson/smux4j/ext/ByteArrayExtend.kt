package com.attson.smux4j.ext

import java.lang.StringBuilder
import java.nio.charset.Charset

fun ByteArray.formatString(): String {
    val stringBuilder = StringBuilder(this.size * 2 + 2)

    stringBuilder.append("[")

    this.forEach {
        stringBuilder.append("$it,")
    }
    return stringBuilder.append("]").toString()
}

fun ByteArray.sizeContentEquals(other: ByteArray, size: Int): Boolean {
    return this.copyOf(size).contentEquals(other)
}

fun ByteArray.sizeToString(charset: Charset, size: Int): String {
    return this.copyOf(size).toString(charset)
}