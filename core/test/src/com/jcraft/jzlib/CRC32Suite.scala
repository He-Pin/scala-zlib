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

  test("combine with zero-length second buffer") {
    val buf1 = randomBytes(1024)

    val c1       = {
      val c = new CRC32; c.update(buf1, 0, buf1.length); c.getValue
    }
    val c2       = {
      val c = new CRC32; c.getValue // initial value, no data
    }
    val expected = {
      val c = new CRC32
      c.update(buf1, 0, buf1.length)
      c.getValue
    }

    assertEquals(CRC32.combine(c1, c2, 0L), expected)
  }

  test("update with offset and partial length") {
    val data   = "hello, world!".getBytes
    val offset = 7
    val len    = 5 // "world"

    val juzc     = new JuzCRC32
    juzc.update(data, offset, len)
    val expected = juzc.getValue

    val crc = new CRC32
    crc.update(data, offset, len)
    assertEquals(crc.getValue, expected)
  }

  test("getCRC32Table returns copy of internal table") {
    val t1 = CRC32.getCRC32Table
    val t2 = CRC32.getCRC32Table
    assertEquals(t1.toSeq, t2.toSeq)
    assertEquals(t1.length, 256)
  }

  // --- New tests for slicing-by-4 and polynomial combine optimizations ---

  test("empty input leaves CRC at initial value") {
    val crc = new CRC32
    crc.update(Array.emptyByteArray, 0, 0)
    assertEquals(crc.getValue, 0L)
  }

  test("lengths 1 through 17 match java.util.zip.CRC32") {
    for (len <- 1 to 17) {
      val data     = randomBytes(len)
      val juzc     = new JuzCRC32
      juzc.update(data, 0, data.length)
      val expected = juzc.getValue

      val crc = new CRC32
      crc.update(data, 0, data.length)
      assertEquals(crc.getValue, expected, s"mismatch at len=$len")
    }
  }

  test("unaligned lengths exercise remainder path") {
    for (len <- List(5, 6, 7, 9, 10, 11, 13, 14, 15)) {
      val data     = randomBytes(len)
      val juzc     = new JuzCRC32
      juzc.update(data, 0, data.length)
      val expected = juzc.getValue

      val crc = new CRC32
      crc.update(data, 0, data.length)
      assertEquals(crc.getValue, expected, s"mismatch at len=$len")
    }
  }

  test("combineGen and combineOp produce same result as combine") {
    val buf1 = randomBytes(1024)
    val buf2 = randomBytes(512)

    val c1       = {
      val c = new CRC32; c.update(buf1, 0, buf1.length); c.getValue
    }
    val c2       = {
      val c = new CRC32; c.update(buf2, 0, buf2.length); c.getValue
    }
    val expected = CRC32.combine(c1, c2, buf2.length.toLong)

    val op     = CRC32.combineGen(buf2.length.toLong)
    val result = CRC32.combineOp(op, c1, c2)
    assertEquals(result, expected)
  }

  test("combineOp reuses operator for multiple pairs") {
    val segLen = 256
    val op     = CRC32.combineGen(segLen.toLong)

    for (seed <- 0 until 5) {
      val rng  = new scala.util.Random(seed)
      val buf1 = Array.fill[Byte](1024)(rng.nextInt(256).toByte)
      val buf2 = Array.fill[Byte](segLen)(rng.nextInt(256).toByte)

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
      assertEquals(CRC32.combineOp(op, c1, c2), expected, s"seed=$seed")
    }
  }

  test("combineGen identity operator for zero length") {
    val op = CRC32.combineGen(0L)
    val c1 = {
      val c = new CRC32; c.update(randomBytes(100), 0, 100); c.getValue
    }
    assertEquals(CRC32.combineOp(op, c1, 0L), c1)
  }

  test("combine with various segment lengths") {
    for (len <- List(1, 2, 3, 4, 7, 8, 15, 16, 31, 32, 63, 64, 255, 256, 1023, 1024, 4096)) {
      val buf1 = randomBytes(len)
      val buf2 = randomBytes(len)

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
      assertEquals(CRC32.combine(c1, c2, len.toLong), expected, s"len=$len")
    }
  }

  test("crc_slice table 0 matches crc_table") {
    assertEquals(CRC32.crc_slice(0).toSeq, CRC32.crc_table.toSeq)
  }

  private def randomBytes(n: Int): Array[Byte] = {
    val rng = new scala.util.Random(42)
    Array.fill[Byte](n)(rng.nextInt(256).toByte)
  }
}
