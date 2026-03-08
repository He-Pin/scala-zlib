package com.jcraft.jzlib

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream }
import JZlib._

class DeflaterInflaterStreamSuite extends munit.FunSuite {

  test("compress and decompress with streams") {
    val data = "Hello, World! This is a test of compression.".getBytes
    val baos = new ByteArrayOutputStream()
    val dos  = new DeflaterOutputStream(baos)
    dos.write(data)
    dos.close()

    val bais   = new ByteArrayInputStream(baos.toByteArray)
    val iis    = new InflaterInputStream(bais)
    val result = readAll(iis)
    iis.close()

    assertEquals(result.toSeq, data.toSeq)
  }

  test("deflate and inflate data one by one") {
    val data = randomBytes(1024)

    val baos = new ByteArrayOutputStream()
    val gos  = new DeflaterOutputStream(baos)
    data.foreach(b => gos.write(b & 0xff))
    gos.close()

    val bais   = new ByteArrayInputStream(baos.toByteArray)
    val iis    = new InflaterInputStream(bais)
    val baos2  = new ByteArrayOutputStream()
    streamCopy(iis, baos2, 1)
    val result = baos2.toByteArray

    assertEquals(result.length, data.length)
    assertEquals(result.toSeq, data.toSeq)
  }

  test("deflate and inflate with various buffer sizes") {
    (1 to 100 by 3).foreach { bufSize =>
      val data = randomBytes(10240)

      val baos = new ByteArrayOutputStream()
      val gos  = new DeflaterOutputStream(baos)
      writeInChunks(data, gos, bufSize)
      gos.close()

      val baos2  = new ByteArrayOutputStream()
      val iis    = new InflaterInputStream(new ByteArrayInputStream(baos.toByteArray))
      streamCopy(iis, baos2, bufSize)
      val result = baos2.toByteArray

      assertEquals(result.length, data.length, s"length mismatch at bufSize=$bufSize")
      assertEquals(result.toSeq, data.toSeq, s"data mismatch at bufSize=$bufSize")
    }
  }

  test("deflate and inflate nowrap data") {
    (1 to 100 by 3).foreach { bufSize =>
      val data = randomBytes(10240)

      val deflater = new Deflater(Z_DEFAULT_COMPRESSION, DEF_WBITS, true)
      val baos     = new ByteArrayOutputStream()
      val gos      = new DeflaterOutputStream(baos, deflater)
      writeInChunks(data, gos, bufSize)
      gos.close()

      val inflater = new Inflater(DEF_WBITS, true)
      val baos2    = new ByteArrayOutputStream()
      val iis      = new InflaterInputStream(new ByteArrayInputStream(baos.toByteArray), inflater)
      streamCopy(iis, baos2, bufSize)
      val result   = baos2.toByteArray

      assertEquals(result.length, data.length, s"length mismatch at bufSize=$bufSize")
      assertEquals(result.toSeq, data.toSeq, s"data mismatch at bufSize=$bufSize")
    }
  }

  test("deflate and inflate nowrap data with MAX_WBITS") {
    val bufSize = 100

    List(
      randomBytes(10240),
      """{"color":2,"id":"EvLd4UG.CXjnk35o1e8LrYYQfHu0h.d*SqVJPoqmzXM::Ly::Snaps::Store::Commit"}""".getBytes,
    ).foreach { data =>
      val deflater = new Deflater(Z_DEFAULT_COMPRESSION, MAX_WBITS, true)
      val inflater = new Inflater(MAX_WBITS, true)

      val baos = new ByteArrayOutputStream()
      val gos  = new DeflaterOutputStream(baos, deflater)
      writeInChunks(data, gos, bufSize)
      gos.close()

      val baos2  = new ByteArrayOutputStream()
      val iis    = new InflaterInputStream(new ByteArrayInputStream(baos.toByteArray), inflater)
      streamCopy(iis, baos2, bufSize)
      val result = baos2.toByteArray

      assertEquals(result.length, data.length)
      assertEquals(result.toSeq, data.toSeq)
    }
  }

  test("large data stream round-trip") {
    val data = randomBytes(100000)
    val baos = new ByteArrayOutputStream()
    val dos  = new DeflaterOutputStream(baos)
    dos.write(data)
    dos.close()

    val bais   = new ByteArrayInputStream(baos.toByteArray)
    val iis    = new InflaterInputStream(bais)
    val result = readAll(iis)
    iis.close()

    assertEquals(result.toSeq, data.toSeq)
  }

  test("interop with java.util.zip.InflaterInputStream") {
    val data = "Hello interop test!".getBytes
    val baos = new ByteArrayOutputStream()
    val dos  = new DeflaterOutputStream(baos)
    dos.write(data)
    dos.close()

    val bais   = new ByteArrayInputStream(baos.toByteArray)
    val juzIis = new java.util.zip.InflaterInputStream(bais)
    val result = readAll(juzIis)
    juzIis.close()

    assertEquals(result.toSeq, data.toSeq)
  }

  test("interop with java.util.zip.DeflaterOutputStream") {
    val data   = "Hello interop test from JDK!".getBytes
    val baos   = new ByteArrayOutputStream()
    val juzDos = new java.util.zip.DeflaterOutputStream(baos)
    juzDos.write(data)
    juzDos.close()

    val bais   = new ByteArrayInputStream(baos.toByteArray)
    val iis    = new InflaterInputStream(bais)
    val result = readAll(iis)
    iis.close()

    assertEquals(result.toSeq, data.toSeq)
  }

  private def readAll(is: java.io.InputStream): Array[Byte] = {
    val baos = new ByteArrayOutputStream()
    val buf  = new Array[Byte](1024)
    var n    = is.read(buf)
    while (n != -1) {
      baos.write(buf, 0, n)
      n = is.read(buf)
    }
    baos.toByteArray
  }

  private def streamCopy(
    is: java.io.InputStream,
    os: java.io.OutputStream,
    bufSize: Int,
  ): Unit = {
    val buf = new Array[Byte](bufSize)
    var n   = is.read(buf)
    while (n != -1) {
      os.write(buf, 0, n)
      n = is.read(buf)
    }
    is.close()
  }

  private def writeInChunks(
    data: Array[Byte],
    os: java.io.OutputStream,
    chunkSize: Int,
  ): Unit = {
    var off = 0
    while (off < data.length) {
      val len = Math.min(chunkSize, data.length - off)
      os.write(data, off, len)
      off += len
    }
  }

  private def randomBytes(n: Int): Array[Byte] = {
    val rng = new scala.util.Random(42)
    Array.fill[Byte](n)(rng.nextInt(256).toByte)
  }
}
