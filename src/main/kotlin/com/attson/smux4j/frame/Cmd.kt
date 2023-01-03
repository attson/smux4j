package com.attson.smux4j.frame


const val cmdSYN: UByte = 0u
const val cmdFIN: UByte = 1u
const val cmdPSH: UByte = 2u
const val cmdNOP: UByte = 3u
const val cmdUPD: UByte = 4u

fun cmdString(cmd: UByte): String {
    return when (cmd) {
        cmdNOP -> "cmdNOP"
        cmdSYN -> "cmdSYN"
        cmdFIN -> "cmdFIN"
        cmdPSH -> "cmdPSH"
        cmdUPD -> "cmdUPD"
        else -> "unknown"
    }
}