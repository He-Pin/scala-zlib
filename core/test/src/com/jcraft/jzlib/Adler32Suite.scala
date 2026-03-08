package com.jcraft.jzlib

import java.util.zip.{ Adler32 => JuzAdler32 }

class Adler32Suite extends munit.FunSuite {

  test("initial value is 1") {
    val adler = new Adler32
    assertEquals(adler.getValue, 1L)
  }

  test("update with single byte array") {
    val adler = new Adler32
    val data  = "hello".getBytes
    adler.update(data, 0, data.length)
    assert(adler.getValue != 1L)
  }

  test("reset returns to initial state") {
    val adler = new Adler32
    val data  = "hello".getBytes
    adler.update(data, 0, data.length)
    assert(adler.getValue != 1L)
    adler.reset()
    assertEquals(adler.getValue, 1L)
  }

  test("reset with init value") {
    val adler   = new Adler32
    val initVal = 0x12340001L
    adler.reset(initVal)
    assertEquals(adler.getValue, initVal)
  }

  test("copy creates independent instance") {
    val buf1 = randomBytes(1024)
    val buf2 = randomBytes(1024)

    val adler1 = new Adler32
    adler1.update(buf1, 0, buf1.length)

    val adler2 = adler1.copy()

    adler1.update(buf2, 0, buf2.length)
    adler2.update(buf2, 0, buf2.length)

    assertEquals(adler2.getValue, adler1.getValue)
  }

  test("copy produces independent state") {
    val buf1 = randomBytes(1024)
    val buf2 = randomBytes(1024)

    val adler1 = new Adler32
    adler1.update(buf1, 0, buf1.length)
    val adler2 = adler1.copy()

    // Diverge the two instances
    adler1.update(buf2, 0, buf2.length)
    val v1 = adler1.getValue

    // adler2 should still have the old value
    assertNotEquals(adler2.getValue, v1)

    // Now update adler2 with the same data — they should converge
    adler2.update(buf2, 0, buf2.length)
    assertEquals(adler2.getValue, v1)
  }

  test("combine two checksums") {
    val buf1 = randomBytes(1024)
    val buf2 = randomBytes(1024)

    val a1       = {
      val a = new Adler32; a.update(buf1, 0, buf1.length); a.getValue
    }
    val a2       = {
      val a = new Adler32; a.update(buf2, 0, buf2.length); a.getValue
    }
    val expected = {
      val a = new Adler32
      a.update(buf1, 0, buf1.length)
      a.update(buf2, 0, buf2.length)
      a.getValue
    }

    assertEquals(Adler32.combine(a1, a2, buf2.length.toLong), expected)
  }

  test("compatible with java.util.zip.Adler32") {
    val data     = randomBytes(1024)
    val juza     = new JuzAdler32
    juza.update(data, 0, data.length)
    val expected = juza.getValue

    val adler = new Adler32
    adler.update(data, 0, data.length)
    assertEquals(adler.getValue, expected)
  }

  test("compatible with java.util.zip.Adler32 for multiple updates") {
    val buf1 = randomBytes(512)
    val buf2 = randomBytes(512)

    val juza     = new JuzAdler32
    juza.update(buf1, 0, buf1.length)
    juza.update(buf2, 0, buf2.length)
    val expected = juza.getValue

    val adler = new Adler32
    adler.update(buf1, 0, buf1.length)
    adler.update(buf2, 0, buf2.length)
    assertEquals(adler.getValue, expected)
  }

  test("single byte update") {
    val data     = Array[Byte](42)
    val juza     = new JuzAdler32
    juza.update(data, 0, 1)
    val expected = juza.getValue

    val adler = new Adler32
    adler.update(data, 0, 1)
    assertEquals(adler.getValue, expected)
  }

  test("empty update leaves value unchanged") {
    val adler = new Adler32
    val data  = new Array[Byte](0)
    adler.update(data, 0, 0)
    assertEquals(adler.getValue, 1L)
  }

  test("large data compatible with java.util.zip.Adler32") {
    val data     = randomBytes(100000)
    val juza     = new JuzAdler32
    juza.update(data, 0, data.length)
    val expected = juza.getValue

    val adler = new Adler32
    adler.update(data, 0, data.length)
    assertEquals(adler.getValue, expected)
  }

  test("combine with zero-length second buffer") {
    val buf1 = randomBytes(1024)

    val a1       = {
      val a = new Adler32; a.update(buf1, 0, buf1.length); a.getValue
    }
    val a2       = {
      val a = new Adler32; a.getValue // initial value, no data
    }
    val expected = {
      val a = new Adler32
      a.update(buf1, 0, buf1.length)
      a.getValue
    }

    assertEquals(Adler32.combine(a1, a2, 0L), expected)
  }

  test("update with offset and partial length") {
    val data   = "hello, world!".getBytes
    val offset = 7
    val len    = 5 // "world"

    val juza     = new JuzAdler32
    juza.update(data, offset, len)
    val expected = juza.getValue

    val adler = new Adler32
    adler.update(data, offset, len)
    assertEquals(adler.getValue, expected)
  }

  test("single-byte update matches array update") {
    val data = "Hello, World!".getBytes("UTF-8")
    val a1   = new Adler32
    data.foreach(b => a1.update(b.toInt))
    val a2   = new Adler32
    a2.update(data, 0, data.length)
    assertEquals(a1.getValue, a2.getValue)
  }

  private def randomBytes(n: Int): Array[Byte] = {
    val rng = new scala.util.Random(42)
    Array.fill[Byte](n)(rng.nextInt(256).toByte)
  }
}
