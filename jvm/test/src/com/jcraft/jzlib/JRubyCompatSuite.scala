package com.jcraft.jzlib

import java.io._

/**
 * Verifies API compatibility with the original jzlib as used by jruby. This ensures scala-zlib can serve as a drop-in
 * replacement.
 */
class JRubyCompatSuite extends munit.FunSuite {

  private val testData  = "Hello, jruby! Testing zlib compatibility.".getBytes("UTF-8")
  private val largeData = Array.fill[Byte](65536)((scala.util.Random.nextInt(256) - 128).toByte)

  // ─── Deflater API ──────────────────────────────────────────────────────────

  test("Deflater: all constructors") {
    // No-arg constructor
    val d1 = new Deflater()
    d1.end()

    // Level constructor
    val d2 = new Deflater(JZlib.Z_DEFAULT_COMPRESSION)
    d2.end()

    // Level + wbits
    val d3 = new Deflater(JZlib.Z_DEFAULT_COMPRESSION, 15)
    d3.end()

    // Level + wbits + strategy (via init)
    val d4 = new Deflater()
    d4.init(JZlib.Z_DEFAULT_COMPRESSION, 15, 9)
    d4.end()
  }

  test("Deflater: init with WrapperType") {
    val d = new Deflater()
    d.init(JZlib.Z_DEFAULT_COMPRESSION, 15, 8, JZlib.W_GZIP)
    d.end()
  }

  test("Deflater: setInput, setOutput, deflate, finished, end") {
    val d = new Deflater()
    d.init(JZlib.Z_DEFAULT_COMPRESSION)
    d.setInput(testData)
    d.setNextInIndex(0)
    d.setAvailIn(testData.length)

    val output = new Array[Byte](1024)
    d.setOutput(output)
    d.setNextOutIndex(0)
    d.setAvailOut(output.length)

    val err = d.deflate(JZlib.Z_FINISH)
    assertEquals(err, JZlib.Z_STREAM_END)
    assert(d.finished())

    val totalOut = d.getTotalOut
    assert(totalOut > 0)
    assert(d.getTotalIn == testData.length.toLong)

    d.end()
  }

  test("Deflater: params changes compression level mid-stream") {
    val d = new Deflater()
    d.init(JZlib.Z_BEST_SPEED)
    d.setInput(testData)
    d.setNextInIndex(0)
    d.setAvailIn(testData.length)

    val output = new Array[Byte](4096)
    d.setOutput(output)
    d.setNextOutIndex(0)
    d.setAvailOut(output.length)

    d.deflate(JZlib.Z_SYNC_FLUSH)
    d.params(JZlib.Z_BEST_COMPRESSION, JZlib.Z_DEFAULT_STRATEGY)
    d.end()
  }

  test("Deflater: setDictionary") {
    val dict = "common words for dictionary".getBytes("UTF-8")
    val d    = new Deflater()
    d.init(JZlib.Z_DEFAULT_COMPRESSION)
    d.setDictionary(dict, dict.length)
    d.end()
  }

  test("Deflater: copy") {
    val d    = new Deflater()
    d.init(JZlib.Z_DEFAULT_COMPRESSION)
    val copy = new Deflater()
    val err  = copy.copy(d)
    assertEquals(err, JZlib.Z_OK)
    d.end()
    copy.end()
  }

  // ─── Inflater API ──────────────────────────────────────────────────────────

  test("Inflater: all constructors") {
    val i1 = new Inflater()
    i1.end()

    val i2 = new Inflater(15)
    i2.end()
  }

  test("Inflater: init with WrapperType") {
    val i = new Inflater()
    i.init(JZlib.W_ZLIB)
    i.end()
  }

  test("Inflater: inflate compressed data") {
    // First compress
    val d          = new Deflater()
    d.init(JZlib.Z_DEFAULT_COMPRESSION)
    d.setInput(testData)
    d.setNextInIndex(0)
    d.setAvailIn(testData.length)
    val compressed = new Array[Byte](1024)
    d.setOutput(compressed)
    d.setNextOutIndex(0)
    d.setAvailOut(compressed.length)
    d.deflate(JZlib.Z_FINISH)
    val compLen    = compressed.length - d.getAvailOut
    d.end()

    // Then decompress
    val i      = new Inflater()
    i.init()
    i.setInput(compressed)
    i.setNextInIndex(0)
    i.setAvailIn(compLen)
    val result = new Array[Byte](testData.length)
    i.setOutput(result)
    i.setNextOutIndex(0)
    i.setAvailOut(result.length)
    val err    = i.inflate(JZlib.Z_FINISH)
    assertEquals(err, JZlib.Z_STREAM_END)
    assert(i.finished())
    assertEquals(new String(result, "UTF-8"), new String(testData, "UTF-8"))
    i.end()
  }

  // ─── Stream API ────────────────────────────────────────────────────────────

  test("DeflaterOutputStream and InflaterInputStream round-trip") {
    // Compress
    val baos       = new ByteArrayOutputStream()
    val dos        = new DeflaterOutputStream(baos)
    dos.write(testData)
    dos.close()
    val compressed = baos.toByteArray

    // Decompress
    val bais   = new ByteArrayInputStream(compressed)
    val iis    = new InflaterInputStream(bais)
    val result = new ByteArrayOutputStream()
    val buf    = new Array[Byte](1024)
    var n      = iis.read(buf)
    while (n > 0) {
      result.write(buf, 0, n)
      n = iis.read(buf)
    }
    iis.close()
    assertEquals(new String(result.toByteArray, "UTF-8"), new String(testData, "UTF-8"))
  }

  test("GZIPOutputStream and GZIPInputStream round-trip") {
    // Compress
    val baos       = new ByteArrayOutputStream()
    val gos        = new GZIPOutputStream(baos)
    gos.write(testData)
    gos.close()
    val compressed = baos.toByteArray

    // Decompress
    val bais   = new ByteArrayInputStream(compressed)
    val gis    = new GZIPInputStream(bais)
    val result = new ByteArrayOutputStream()
    val buf    = new Array[Byte](1024)
    var n      = gis.read(buf)
    while (n > 0) {
      result.write(buf, 0, n)
      n = gis.read(buf)
    }
    gis.close()
    assertEquals(new String(result.toByteArray, "UTF-8"), new String(testData, "UTF-8"))
  }

  test("GZIPOutputStream interop with java.util.zip.GZIPInputStream") {
    // Compress with scala-zlib
    val baos       = new ByteArrayOutputStream()
    val gos        = new GZIPOutputStream(baos)
    gos.write(testData)
    gos.close()
    val compressed = baos.toByteArray

    // Decompress with java.util.zip
    val bais   = new ByteArrayInputStream(compressed)
    val jgis   = new java.util.zip.GZIPInputStream(bais)
    val result = jgis.readAllBytes()
    jgis.close()
    assertEquals(new String(result, "UTF-8"), new String(testData, "UTF-8"))
  }

  test("java.util.zip.GZIPOutputStream interop with GZIPInputStream") {
    // Compress with java.util.zip
    val baos       = new ByteArrayOutputStream()
    val jgos       = new java.util.zip.GZIPOutputStream(baos)
    jgos.write(testData)
    jgos.close()
    val compressed = baos.toByteArray

    // Decompress with scala-zlib
    val bais   = new ByteArrayInputStream(compressed)
    val gis    = new GZIPInputStream(bais)
    val result = new ByteArrayOutputStream()
    val buf    = new Array[Byte](1024)
    var n      = gis.read(buf)
    while (n > 0) {
      result.write(buf, 0, n)
      n = gis.read(buf)
    }
    gis.close()
    assertEquals(new String(result.toByteArray, "UTF-8"), new String(testData, "UTF-8"))
  }

  // ─── Constants ─────────────────────────────────────────────────────────────

  test("JZlib constants match expected values") {
    assertEquals(JZlib.Z_NO_COMPRESSION, 0)
    assertEquals(JZlib.Z_BEST_SPEED, 1)
    assertEquals(JZlib.Z_BEST_COMPRESSION, 9)
    assertEquals(JZlib.Z_DEFAULT_COMPRESSION, -1)

    assertEquals(JZlib.Z_NO_FLUSH, 0)
    assertEquals(JZlib.Z_PARTIAL_FLUSH, 1)
    assertEquals(JZlib.Z_SYNC_FLUSH, 2)
    assertEquals(JZlib.Z_FULL_FLUSH, 3)
    assertEquals(JZlib.Z_FINISH, 4)

    assertEquals(JZlib.Z_OK, 0)
    assertEquals(JZlib.Z_STREAM_END, 1)
    assertEquals(JZlib.Z_NEED_DICT, 2)
    assertEquals(JZlib.Z_STREAM_ERROR, -2)
    assertEquals(JZlib.Z_DATA_ERROR, -3)
    assertEquals(JZlib.Z_BUF_ERROR, -5)
  }

  test("JZlib WrapperType values") {
    assert(JZlib.W_NONE != null)
    assert(JZlib.W_ZLIB != null)
    assert(JZlib.W_GZIP != null)
    assert(JZlib.W_ANY != null)
  }

  // ─── Checksum API ──────────────────────────────────────────────────────────

  test("Adler32 API compatibility") {
    val a  = new Adler32()
    a.update(testData, 0, testData.length)
    val v1 = a.getValue
    assert(v1 != 0L)

    val copy = a.copy()
    assertEquals(copy.getValue, v1)

    a.reset()
    assertEquals(a.getValue, 1L) // Adler-32 initial value
  }

  test("CRC32 API compatibility") {
    val c  = new CRC32()
    c.update(testData, 0, testData.length)
    val v1 = c.getValue
    assert(v1 != 0L)

    val copy = c.copy()
    assertEquals(copy.getValue, v1)

    c.reset()
    assertEquals(c.getValue, 0L) // CRC-32 initial value
  }

  // ─── Exception types ──────────────────────────────────────────────────────

  test("ZStreamException is throwable") {
    val e = new ZStreamException("test error")
    assert(e.isInstanceOf[Exception])
    assertEquals(e.getMessage, "test error")
  }

  test("GZIPException is throwable") {
    val e = new GZIPException("test gzip error")
    assert(e.isInstanceOf[Exception])
    assertEquals(e.getMessage, "test gzip error")
  }

  // ─── GZIPHeader API ───────────────────────────────────────────────────────

  test("GZIPHeader metadata") {
    val h = new GZIPHeader()
    h.setOS(GZIPHeader.OS_UNIX)
    assertEquals(h.getOS, GZIPHeader.OS_UNIX.toInt)

    h.setName("test.txt")
    assertEquals(h.getName, "test.txt")

    h.setComment("test comment")
    assertEquals(h.getComment, "test comment")
  }

  // ─── Large data test ──────────────────────────────────────────────────────

  test("large data round-trip via streams") {
    val baos       = new ByteArrayOutputStream()
    val dos        = new DeflaterOutputStream(baos)
    dos.write(largeData)
    dos.close()
    val compressed = baos.toByteArray

    val bais   = new ByteArrayInputStream(compressed)
    val iis    = new InflaterInputStream(bais)
    val result = new ByteArrayOutputStream()
    val buf    = new Array[Byte](8192)
    var n      = iis.read(buf)
    while (n > 0) {
      result.write(buf, 0, n)
      n = iis.read(buf)
    }
    iis.close()
    assert(result.toByteArray.sameElements(largeData))
  }
}
