package com.jcraft.jzlib

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream }

class GZIPMultiMemberSuite extends munit.FunSuite {

  private def gzipCompress(data: Array[Byte]): Array[Byte] = {
    val baos = new ByteArrayOutputStream()
    val gos  = new GZIPOutputStream(baos)
    gos.write(data)
    gos.close()
    baos.toByteArray
  }

  private def readAll(gis: GZIPInputStream): Array[Byte] = {
    val baos = new ByteArrayOutputStream()
    val buf  = new Array[Byte](256)
    var n    = gis.read(buf)
    while (n != -1) {
      baos.write(buf, 0, n)
      n = gis.read(buf)
    }
    baos.toByteArray
  }

  test("read concatenated GZIP streams") {
    val data1 = "Hello, ".getBytes("UTF-8")
    val data2 = "World!".getBytes("UTF-8")

    val combined = gzipCompress(data1) ++ gzipCompress(data2)

    val gis    = new GZIPInputStream(new ByteArrayInputStream(combined))
    val result = readAll(gis)
    gis.close()

    assertEquals(new String(result, "UTF-8"), "Hello, World!")
  }

  test("single member GZIP still works") {
    val data = "Single member test".getBytes("UTF-8")
    val gz   = gzipCompress(data)

    val gis    = new GZIPInputStream(new ByteArrayInputStream(gz))
    val result = readAll(gis)
    gis.close()

    assertEquals(new String(result, "UTF-8"), "Single member test")
  }

  test("empty second member") {
    val data1 = "First".getBytes("UTF-8")
    val data2 = new Array[Byte](0)

    val combined = gzipCompress(data1) ++ gzipCompress(data2)

    val gis    = new GZIPInputStream(new ByteArrayInputStream(combined))
    val result = readAll(gis)
    gis.close()

    assertEquals(new String(result, "UTF-8"), "First")
  }
}
