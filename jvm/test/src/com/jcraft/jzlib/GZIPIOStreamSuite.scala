package com.jcraft.jzlib

import java.io._
import java.util.zip.{ GZIPInputStream => JuzGZIPInputStream }
import java.util.zip.{ GZIPOutputStream => JuzGZIPOutputStream }

class GZIPIOStreamSuite extends munit.FunSuite {

  test("GZIP compress and decompress round-trip") {
    val content = "hello".getBytes

    val baos = new ByteArrayOutputStream()
    val gos  = new GZIPOutputStream(baos)
    gos.write(content)
    gos.close()

    val bais = new ByteArrayInputStream(baos.toByteArray)
    val gis  = new GZIPInputStream(bais)
    val buf  = new Array[Byte](1024)
    val n    = gis.read(buf)

    assertEquals(n, content.length)
    val actual = new Array[Byte](n)
    System.arraycopy(buf, 0, actual, 0, n)
    assertEquals(actual.toSeq, content.toSeq)
  }

  test("GZIP header metadata") {
    val comment = "hi"
    val name    = "/tmp/foo"
    val time    = System.currentTimeMillis() / 1000
    val content = "hello".getBytes

    val baos = new ByteArrayOutputStream()
    val gos  = new GZIPOutputStream(baos)
    gos.setComment(comment)
    gos.setName(name)
    gos.setModifiedTime(time)
    gos.write(content)
    gos.close()

    val bais = new ByteArrayInputStream(baos.toByteArray)
    val gis  = new GZIPInputStream(bais)
    val buf  = new Array[Byte](1024)
    val n    = gis.read(buf)

    assertEquals(n, content.length)
    (0 until n).foreach { i =>
      assertEquals(buf(i), content(i))
    }

    assertEquals(gis.getComment(), comment)
    assertEquals(gis.getName(), name)
    assertEquals(gis.getModifiedTime(), time)

    val crc32 = new CRC32
    crc32.update(content, 0, content.length)
    assertEquals(gis.getCRC(), crc32.getValue)
  }

  test("compatible with java.util.zip.GZIPInputStream (our compress, JDK decompress)") {
    val data = "Hello from scala-zlib GZIPOutputStream!".getBytes

    val baos = new ByteArrayOutputStream()
    val gos  = new GZIPOutputStream(baos)
    gos.write(data)
    gos.close()

    val bais   = new ByteArrayInputStream(baos.toByteArray)
    val juzGis = new JuzGZIPInputStream(bais)
    val result = readAll(juzGis)
    juzGis.close()

    assertEquals(result.toSeq, data.toSeq)
  }

  test("compatible with java.util.zip.GZIPOutputStream (JDK compress, our decompress)") {
    val data = "Hello from java.util.zip.GZIPOutputStream!".getBytes

    val baos   = new ByteArrayOutputStream()
    val juzGos = new JuzGZIPOutputStream(baos)
    juzGos.write(data)
    juzGos.close()

    val bais   = new ByteArrayInputStream(baos.toByteArray)
    val gis    = new GZIPInputStream(bais)
    val result = readAll(gis)
    gis.close()

    assertEquals(result.toSeq, data.toSeq)
  }

  test("large data GZIP round-trip") {
    val rng  = new scala.util.Random(42)
    val data = Array.fill[Byte](100000)(rng.nextInt(256).toByte)

    val baos = new ByteArrayOutputStream()
    val gos  = new GZIPOutputStream(baos)
    gos.write(data)
    gos.close()

    val bais   = new ByteArrayInputStream(baos.toByteArray)
    val gis    = new GZIPInputStream(bais)
    val result = readAll(gis)
    gis.close()

    assertEquals(result.toSeq, data.toSeq)
  }

  test("GZIP CRC32 integrity check") {
    val data = "integrity check data".getBytes

    val crc         = new CRC32
    crc.update(data, 0, data.length)
    val expectedCrc = crc.getValue

    val baos = new ByteArrayOutputStream()
    val gos  = new GZIPOutputStream(baos)
    gos.write(data)
    // finish() writes the trailer including CRC, but does not call end()
    gos.finish()
    assertEquals(gos.getCRC(), expectedCrc)

    val bais = new ByteArrayInputStream(baos.toByteArray)
    val gis  = new GZIPInputStream(bais)
    readAll(gis)
    assertEquals(gis.getCRC(), expectedCrc)
  }

  test("GZIP with repeated text data") {
    val text = "the quick brown fox jumps over the lazy dog\n"
    val data = Array.fill(100)(text).mkString.getBytes

    val baos = new ByteArrayOutputStream()
    val gos  = new GZIPOutputStream(baos)
    gos.write(data)
    gos.close()

    // Compressed should be smaller than original for repetitive data
    assert(
      baos.toByteArray.length < data.length,
      s"compressed ${baos.toByteArray.length} >= original ${data.length}",
    )

    val bais   = new ByteArrayInputStream(baos.toByteArray)
    val gis    = new GZIPInputStream(bais)
    val result = readAll(gis)
    gis.close()

    assertEquals(result.toSeq, data.toSeq)
  }

  private def readAll(is: InputStream): Array[Byte] = {
    val baos = new ByteArrayOutputStream()
    val buf  = new Array[Byte](1024)
    var n    = is.read(buf)
    while (n != -1) {
      baos.write(buf, 0, n)
      n = is.read(buf)
    }
    baos.toByteArray
  }
}
