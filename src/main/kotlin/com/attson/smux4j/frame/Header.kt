package com.attson.smux4j.frame

const val szCmdUPD: Int = 8

const val initialPeerWindow: UInt = 262144u

const val sizeOfVer = 1
const val sizeOfCmd = 1
const val sizeOfLength = 2
const val sizeOfSid = 4
const val headerSize = sizeOfVer + sizeOfCmd + sizeOfSid + sizeOfLength