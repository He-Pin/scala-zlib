package com.jcraft.jzlib

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream, IOException }

class StreamEdgeCaseSuite extends munit.FunSuite {

  private def compress(data: Array[Byte]): Array[Byte] = {
    val baos = new ByteArrayOutputStream()
    val dos  = new DeflaterOutputStream(baos)
    dos.write(data)
    dos.close()
    baos.toByteArray
  }

  test("DeflaterOutputStream close is idempotent") {
    val baos = new ByteArrayOutputStream()
    val dos  = new DeflaterOutputStream(baos)
    dos.write(Array[Byte](1, 2, 3))
    dos.close()
    dos.close() // should not throw
  }

  test("InflaterInputStream close is idempotent") {
    val compressed = compress("hello".getBytes("UTF-8"))
    val iis        = new InflaterInputStream(new ByteArrayInputStream(compressed))
    val buf        = new Array[Byte](256)
    iis.read(buf)
    iis.close()
    iis.close() // should not throw
  }

  test("DeflaterOutputStream handles empty input") {
    val baos       = new ByteArrayOutputStream()
    val dos        = new DeflaterOutputStream(baos)
    dos.close()
    val compressed = baos.toByteArray
    // Should produce valid compressed output (empty)
    val iis        = new InflaterInputStream(new ByteArrayInputStream(compressed))
    val result     = new ByteArrayOutputStream()
    val buf        = new Array[Byte](256)
    var n          = iis.read(buf)
    while (n != -1) {
      result.write(buf, 0, n)
      n = iis.read(buf)
    }
    assertEquals(result.toByteArray.length, 0)
  }

  test("DeflaterOutputStream with syncFlush produces output after each write") {
    val baos       = new ByteArrayOutputStream()
    val dos        = new DeflaterOutputStream(baos)
    dos.setSyncFlush(true)
    dos.write("hello".getBytes("UTF-8"))
    dos.flush()
    val afterFirst = baos.size()
    assert(afterFirst > 0, "syncFlush should produce output after flush")
    dos.close()
  }

  test("InflaterInputStream read returns -1 at end of stream") {
    val compressed = compress("test".getBytes("UTF-8"))
    val iis        = new InflaterInputStream(new ByteArrayInputStream(compressed))
    val buf        = new Array[Byte](256)
    // Read all data
    while (iis.read(buf) != -1) {}
    // Further reads should return -1
    assertEquals(iis.read(buf), -1)
    assertEquals(iis.read(buf), -1)
    iis.close()
  }

  test("InflaterInputStream available throws IOException after close") {
    val compressed = compress("test".getBytes("UTF-8"))
    val iis        = new InflaterInputStream(new ByteArrayInputStream(compressed))
    iis.close()
    intercept[IOException] {
      iis.available()
    }
  }

  test("round-trip with large data and small buffer") {
    val data       = Array.fill[Byte](100000)((scala.util.Random.nextInt(256) - 128).toByte)
    val baos       = new ByteArrayOutputStream()
    val deflater   = new Deflater(JZlib.Z_DEFAULT_COMPRESSION)
    val dos        = new DeflaterOutputStream(baos, deflater, 32) // small 32-byte buffer
    dos.write(data)
    dos.close()
    val compressed = baos.toByteArray

    val inflater = new Inflater()
    val iis      = new InflaterInputStream(new ByteArrayInputStream(compressed), inflater, 32)
    val result   = new ByteArrayOutputStream()
    val buf      = new Array[Byte](32) // small read buffer
    var n        = iis.read(buf)
    while (n != -1) {
      result.write(buf, 0, n)
      n = iis.read(buf)
    }
    iis.close()
    assertEquals(result.toByteArray.toSeq, data.toSeq)
  }
}
