package com.attson.smux4j.ext.io

import com.attson.smux4j.ext.io.ByteArraysInputStream
import com.attson.smux4j.ext.formatString
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class ByteArraysInputStreamTests {
    @Test
    fun testReadByteArrayOffsetLen() {
        val arrayListOf = arrayListOf<ByteArray>()
        arrayListOf.add(byteArrayOf(1, 2, 3, 4, 5, 6, 7))
        arrayListOf.add(byteArrayOf(8, 9, 10, 11, 12, 13, 14))
        arrayListOf.add(byteArrayOf(15, 16, 17, 18, 19, 20, 21))
        arrayListOf.add(byteArrayOf(22, 23, 24, 25, 26, 27, 28))

        val byteArraysInputStream = ByteArraysInputStream(arrayListOf, 28)

        val dist = ByteArray(14)

        byteArraysInputStream.read(dist, 0, 14)

        Assertions.assertEquals("[1,2,3,4,5,6,7,8,9,10,11,12,13,14,]", dist.formatString())

        Assertions.assertEquals(14, byteArraysInputStream.available())

        val dist2 = ByteArray(14)
        byteArraysInputStream.read(dist2, 0, 14)

        Assertions.assertEquals("[15,16,17,18,19,20,21,22,23,24,25,26,27,28,]", dist2.formatString())

        Assertions.assertEquals(-1, byteArraysInputStream.read(dist2, 0, 14))
        Assertions.assertEquals(0, byteArraysInputStream.available())
    }

    @Test
    fun testRead() {
        val arrayListOf = arrayListOf<ByteArray>()
        arrayListOf.add(byteArrayOf(1, 2, 3, 4, 5, 6, 7))
        arrayListOf.add(byteArrayOf(8, 9, 10, 11, 12, 13, 14))
        arrayListOf.add(byteArrayOf(15, 16, 17, 18, 19, 20, 21))
        arrayListOf.add(byteArrayOf(22, 23, 24, 25, 26, 27, 28))

        val byteArraysInputStream = ByteArraysInputStream(arrayListOf, 28)
        for (i in 0 until 28) {
            Assertions.assertEquals(i + 1, byteArraysInputStream.read())
            Assertions.assertEquals(28 - i - 1, byteArraysInputStream.available())
        }
    }

    @Test
    fun testMarkReset() {
        val arrayListOf = arrayListOf<ByteArray>()
        arrayListOf.add(byteArrayOf(1, 2, 3, 4, 5, 6, 7))
        arrayListOf.add(byteArrayOf(8, 9, 10, 11, 12, 13, 14))
        arrayListOf.add(byteArrayOf(15, 16, 17, 18, 19, 20, 21))
        arrayListOf.add(byteArrayOf(22, 23, 24, 25, 26, 27, 28))

        val byteArraysInputStream = ByteArraysInputStream(arrayListOf, 28)

        byteArraysInputStream.mark(0)

        for (i in 0 until 28) {
            Assertions.assertEquals(i + 1, byteArraysInputStream.read())
            Assertions.assertEquals(28 - i - 1, byteArraysInputStream.available())
        }

        byteArraysInputStream.reset()
        Assertions.assertEquals(28, byteArraysInputStream.available())

        for (i in 0 until 28) {
            Assertions.assertEquals(i + 1, byteArraysInputStream.read())
            Assertions.assertEquals(28 - i - 1, byteArraysInputStream.available())
        }
    }

    @Test
    fun testMarkSupported() {
        val arrayListOf = arrayListOf<ByteArray>()
        arrayListOf.add(byteArrayOf(1, 2, 3, 4, 5, 6, 7))
        arrayListOf.add(byteArrayOf(8, 9, 10, 11, 12, 13, 14))
        arrayListOf.add(byteArrayOf(15, 16, 17, 18, 19, 20, 21))
        arrayListOf.add(byteArrayOf(22, 23, 24, 25, 26, 27, 28))

        val byteArraysInputStream = ByteArraysInputStream(arrayListOf, 28)

        Assertions.assertTrue(byteArraysInputStream.markSupported())
    }

    @Test
    fun testClearRead() {
        val arrayListOf = arrayListOf<ByteArray>()
        arrayListOf.add(byteArrayOf(1, 2, 3, 4, 5, 6, 7))
        arrayListOf.add(byteArrayOf(8, 9, 10, 11, 12, 13, 14))
        arrayListOf.add(byteArrayOf(15, 16, 17, 18, 19, 20, 21))
        arrayListOf.add(byteArrayOf(22, 23, 24, 25, 26, 27, 28))

        val byteArraysInputStream = ByteArraysInputStream(arrayListOf, 28)

        byteArraysInputStream.readAllBytes()
        byteArraysInputStream.clearRead()

        Assertions.assertEquals(0, byteArraysInputStream.available())
        Assertions.assertTrue(arrayListOf.isEmpty())
    }

    @Test
    fun testClearRead1() {
        val arrayListOf = arrayListOf<ByteArray>()
        arrayListOf.add(byteArrayOf(1, 2, 3, 4, 5, 6, 7))
        arrayListOf.add(byteArrayOf(8, 9, 10, 11, 12, 13, 14))
        arrayListOf.add(byteArrayOf(15, 16, 17, 18, 19, 20, 21))
        arrayListOf.add(byteArrayOf(22, 23, 24, 25, 26, 27, 28))

        val byteArraysInputStream = ByteArraysInputStream(arrayListOf, 28)

        val dist = ByteArray(27)

        byteArraysInputStream.read(dist)
        byteArraysInputStream.clearRead()

        Assertions.assertEquals(1, byteArraysInputStream.available())
        Assertions.assertEquals(1, arrayListOf.size)
    }

    @Test
    fun testReadLarge() {
        val arrayListOf = arrayListOf<ByteArray>()
        for (i in 0 until 102401) {
            arrayListOf.add(byteArrayOf(1, 2, 3, 4, 5, 6, 7))
        }

        val byteArraysInputStream = ByteArraysInputStream(arrayListOf, 102401*7)

        val readAllBytes = byteArraysInputStream.readAllBytes()

        Assertions.assertEquals(102401*7, readAllBytes.size)
    }
}