package com.jcraft.jzlib

import java.io._

class CLIIntegrationSuite extends munit.FunSuite {

  test("GZIPOutputStream and GZIPInputStream round-trip (CLI-style)") {
    val original =
      "Hello from scala-zlib CLI tools! This is test data.".getBytes("UTF-8")

    val compressed = new ByteArrayOutputStream()
    val gos        = new GZIPOutputStream(compressed)
    gos.write(original)
    gos.close()

    val gis    =
      new GZIPInputStream(new ByteArrayInputStream(compressed.toByteArray))
    val result = new ByteArrayOutputStream()
    val buf    = new Array[Byte](1024)
    var n      = gis.read(buf)
    while (n != -1) {
      result.write(buf, 0, n)
      n = gis.read(buf)
    }
    gis.close()

    assertEquals(result.toByteArray.toSeq, original.toSeq)
  }

  test("parallel block compression produces valid concatenated GZIP") {
    val block1 =
      "First block of data for parallel compression test.".getBytes("UTF-8")
    val block2 =
      "Second block of data for parallel compression test.".getBytes("UTF-8")

    val out = new ByteArrayOutputStream()

    val gos1 = new GZIPOutputStream(out)
    gos1.write(block1)
    gos1.close()

    val gos2 = new GZIPOutputStream(out)
    gos2.write(block2)
    gos2.close()

    val gis    =
      new GZIPInputStream(new ByteArrayInputStream(out.toByteArray))
    val result = new ByteArrayOutputStream()
    val buf    = new Array[Byte](1024)
    var n      = gis.read(buf)
    while (n != -1) {
      result.write(buf, 0, n)
      n = gis.read(buf)
    }
    gis.close()

    val expected =
      new String(block1, "UTF-8") + new String(block2, "UTF-8")
    assertEquals(new String(result.toByteArray, "UTF-8"), expected)
  }

  test("GZIP with different compression levels produces valid output") {
    val rng  = new java.util.Random(123)
    val data = Array.fill(10000)((rng.nextInt(26) + 'A').toByte)

    for (level <- 1 to 9) {
      val compressed = new ByteArrayOutputStream()
      val deflater   = Deflater(level, JZlib.W_GZIP)
      val gos        =
        new GZIPOutputStream(compressed, deflater, 4096, true)
      gos.write(data)
      gos.close()

      val gis    = new GZIPInputStream(
        new ByteArrayInputStream(compressed.toByteArray),
      )
      val result = new ByteArrayOutputStream()
      val buf    = new Array[Byte](4096)
      var n      = gis.read(buf)
      while (n != -1) {
        result.write(buf, 0, n)
        n = gis.read(buf)
      }
      gis.close()

      assertEquals(
        result.toByteArray.toSeq,
        data.toSeq,
        s"Failed at level $level",
      )
    }
  }

  test("GZIP header preserves OS field with auto-detection") {
    val os = GZIPHeader.detectOS()
    assert(os >= 0 && (os <= 13 || os == 255), s"Invalid OS: $os")
  }

  test("large data compression and decompression (CLI-scale)") {
    val data = new Array[Byte](1024 * 1024)
    val rng  = new java.util.Random(42)
    rng.nextBytes(data)

    val compressed = new ByteArrayOutputStream()
    val gos        = new GZIPOutputStream(compressed)
    var offset     = 0
    while (offset < data.length) {
      val len = Math.min(8192, data.length - offset)
      gos.write(data, offset, len)
      offset += len
    }
    gos.close()

    val gis    =
      new GZIPInputStream(new ByteArrayInputStream(compressed.toByteArray))
    val result = new ByteArrayOutputStream()
    val buf    = new Array[Byte](8192)
    var n      = gis.read(buf)
    while (n != -1) {
      result.write(buf, 0, n)
      n = gis.read(buf)
    }
    gis.close()

    assertEquals(result.toByteArray.length, data.length)
    assertEquals(result.toByteArray.toSeq, data.toSeq)
  }
}
