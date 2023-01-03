package com.attson.smux4j.frame

class Frame(
    private var ver: UByte,
    private var cmd: UByte,
    private var sid: Long
) {
    private var data: ByteArray = ByteArray(0)

    fun getVer(): UByte {
        return ver
    }

    fun getCmd(): UByte {
        return cmd
    }

    fun getSid(): Long {
        return sid
    }

    fun setData(data: ByteArray): Frame {
        this.data = data

        return this
    }

    fun getData(): ByteArray {
        return this.data
    }
}