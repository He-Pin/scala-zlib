package com.jcraft.jzlib

import java.util.zip.{ CRC32 => JuzCRC32 }

class CRC32Suite extends munit.FunSuite {

  test("initial value is 0") {
    val crc = new CRC32
    assertEquals(crc.getValue, 0L)
  }

  test("update with byte array") {
    val crc  = new CRC32
    val data = "hello".getBytes
    crc.update(data, 0, data.length)
    assert(crc.getValue != 0L)
  }

  test("reset clears state") {
    val crc  = new CRC32
    val data = "hello".getBytes
    crc.update(data, 0, data.length)
    assert(crc.getValue != 0L)
    crc.reset()
    assertEquals(crc.getValue, 0L)
  }

  test("reset with init value") {
    val crc     = new CRC32
    val initVal = 0x12345678L
    crc.reset(initVal)
    assertEquals(crc.getValue, initVal)
  }

  test("copy creates independent instance") {
    val buf1 = randomBytes(1024)
    val buf2 = randomBytes(1024)

    val crc1 = new CRC32
    crc1.update(buf1, 0, buf1.length)

    val crc2 = crc1.copy()

    crc1.update(buf2, 0, buf2.length)
    crc2.update(buf2, 0, buf2.length)

    assertEquals(crc2.getValue, crc1.getValue)
  }

  test("copy produces independent state") {
    val buf1 = randomBytes(1024)
    val buf2 = randomBytes(1024)

    val crc1 = new CRC32
    crc1.update(buf1, 0, buf1.length)
    val crc2 = crc1.copy()

    crc1.update(buf2, 0, buf2.length)
    val v1 = crc1.getValue

    assertNotEquals(crc2.getValue, v1)

    crc2.update(buf2, 0, buf2.length)
    assertEquals(crc2.getValue, v1)
  }

  test("combine two checksums") {
    val buf1 = randomBytes(1024)
    val buf2 = randomBytes(1024)

    val c1       = {
      val c = new CRC32; c.update(buf1, 0, buf1.length); c.getValue
    }
    val c2       = {
      val c = new CRC32; c.update(buf2, 0, buf2.length); c.getValue
    }
    val expected = {
      val c = new CRC32
      c.update(buf1, 0, buf1.length)
      c.update(buf2, 0, buf2.length)
      c.getValue
    }

    assertEquals(CRC32.combine(c1, c2, buf2.length.toLong), expected)
  }

  test("compatible with java.util.zip.CRC32") {
    val data     = randomBytes(1024)
    val juzc     = new JuzCRC32
    juzc.update(data, 0, data.length)
    val expected = juzc.getValue

    val crc = new CRC32
    crc.update(data, 0, data.length)
    assertEquals(crc.getValue, expected)
  }

  test("compatible with java.util.zip.CRC32 for multiple updates") {
    val buf1 = randomBytes(512)
    val buf2 = randomBytes(512)

    val juzc     = new JuzCRC32
    juzc.update(buf1, 0, buf1.length)
    juzc.update(buf2, 0, buf2.length)
    val expected = juzc.getValue

    val crc = new CRC32
    crc.update(buf1, 0, buf1.length)
    crc.update(buf2, 0, buf2.length)
    assertEquals(crc.getValue, expected)
  }

  test("single byte update") {
    val data     = Array[Byte](42)
    val juzc     = new JuzCRC32
    juzc.update(data, 0, 1)
    val expected = juzc.getValue

    val crc = new CRC32
    crc.update(data, 0, 1)
    assertEquals(crc.getValue, expected)
  }

  test("large data compatible with java.util.zip.CRC32") {
    val data     = randomBytes(100000)
    val juzc     = new JuzCRC32
    juzc.update(data, 0, data.length)
    val expected = juzc.getValue

    val crc = new CRC32
    crc.update(data, 0, data.length)
    assertEquals(crc.getValue, expected)
  }

  test("getCRC32Table returns copy of internal table") {
    val t1 = CRC32.getCRC32Table
    val t2 = CRC32.getCRC32Table
    assertEquals(t1.toSeq, t2.toSeq)
    assertEquals(t1.length, 256)
  }

  private def randomBytes(n: Int): Array[Byte] = {
    val rng = new scala.util.Random(42)
    Array.fill[Byte](n)(rng.nextInt(256).toByte)
  }
}
