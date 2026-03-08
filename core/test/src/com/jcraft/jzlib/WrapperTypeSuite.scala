package com.jcraft.jzlib

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream }
import JZlib._

class WrapperTypeSuite extends munit.FunSuite {

  val data: Array[Byte] = "hello, hello!".getBytes
  val comprLen          = 40000
  val uncomprLen        = comprLen

  test("deflate/inflate with W_ZLIB") {
    roundTripWithWrapperType(W_ZLIB, W_ZLIB)
  }

  test("deflate/inflate with W_GZIP") {
    roundTripWithWrapperType(W_GZIP, W_GZIP)
  }

  test("deflate/inflate with W_NONE (raw)") {
    roundTripWithWrapperType(W_NONE, W_NONE)
  }

  test("inflate with W_ANY auto-detects ZLIB") {
    roundTripWithWrapperType(W_ZLIB, W_ANY)
  }

  test("inflate with W_ANY auto-detects GZIP") {
    roundTripWithWrapperType(W_GZIP, W_ANY)
  }

  test("inflate with W_ANY auto-detects NONE") {
    roundTripWithWrapperType(W_NONE, W_ANY)
  }

  test("W_ZLIB deflated data fails with W_GZIP inflate") {
    assertInflateFails(W_ZLIB, W_GZIP)
  }

  test("W_ZLIB deflated data fails with W_NONE inflate") {
    assertInflateFails(W_ZLIB, W_NONE)
  }

  test("W_GZIP deflated data fails with W_ZLIB inflate") {
    assertInflateFails(W_GZIP, W_ZLIB)
  }

  test("W_GZIP deflated data fails with W_NONE inflate") {
    assertInflateFails(W_GZIP, W_NONE)
  }

  test("W_NONE deflated data fails with W_ZLIB inflate") {
    assertInflateFails(W_NONE, W_ZLIB)
  }

  test("W_NONE deflated data fails with W_GZIP inflate") {
    assertInflateFails(W_NONE, W_GZIP)
  }

  test("Deflater with WrapperType via streams") {
    val cases = List(
      (W_ZLIB, List(W_ZLIB, W_ANY), List(W_GZIP, W_NONE)),
      (W_GZIP, List(W_GZIP, W_ANY), List(W_ZLIB, W_NONE)),
      (W_NONE, List(W_NONE, W_ANY), List(W_ZLIB, W_GZIP)),
    )

    cases.foreach { case (deflateWrapper, goodInflate, badInflate) =>
      val baos     = new ByteArrayOutputStream()
      val deflater = new Deflater(Z_DEFAULT_COMPRESSION, DEF_WBITS, 9, deflateWrapper)
      val gos      = new DeflaterOutputStream(baos, deflater)
      gos.write(data)
      gos.close()

      val deflated = baos.toByteArray

      goodInflate.foreach { w =>
        val baos2    = new ByteArrayOutputStream()
        val inflater = new Inflater(w)
        val iis      = new InflaterInputStream(new ByteArrayInputStream(deflated), inflater)
        val buf      = new Array[Byte](1024)
        var n        = iis.read(buf)
        while (n != -1) {
          baos2.write(buf, 0, n)
          n = iis.read(buf)
        }
        iis.close()
        val result   = baos2.toByteArray

        assertEquals(result.length, data.length, s"length mismatch deflate=$deflateWrapper inflate=$w")
        assertEquals(result.toSeq, data.toSeq, s"data mismatch deflate=$deflateWrapper inflate=$w")
      }

      badInflate.foreach { w =>
        val inflater = new Inflater(w)
        val iis      = new InflaterInputStream(new ByteArrayInputStream(deflated), inflater)
        try {
          val buf = new Array[Byte](1024)
          var n   = iis.read(buf)
          while (n != -1) { n = iis.read(buf) }
          fail(s"expected IOException for deflate=$deflateWrapper inflate=$w")
        } catch {
          case _: java.io.IOException => // expected
        }
      }
    }
  }

  test("wbits+32 auto-detects ZLIB and GZIP") {
    val compr   = new Array[Byte](comprLen)
    val uncompr = new Array[Byte](uncomprLen)

    // ZLIB format (DEF_WBITS)
    val deflater1 = new Deflater
    var err       = deflater1.init(Z_BEST_SPEED, DEF_WBITS, 9)
    assertEquals(err, Z_OK)
    deflateData(deflater1, data, compr)

    val inflater1 = new Inflater
    err = inflater1.init(DEF_WBITS + 32)
    assertEquals(err, Z_OK)
    val result1   = inflateData(inflater1, compr, uncompr)
    assertEquals(new String(result1), new String(data))

    // GZIP format (DEF_WBITS + 16)
    val deflater2 = new Deflater
    err = deflater2.init(Z_BEST_SPEED, DEF_WBITS + 16, 9)
    assertEquals(err, Z_OK)
    deflateData(deflater2, data, compr)

    val inflater2 = new Inflater
    err = inflater2.init(DEF_WBITS + 32)
    assertEquals(err, Z_OK)
    val result2   = inflateData(inflater2, compr, uncompr)
    assertEquals(new String(result2), new String(data))
  }

  private def roundTripWithWrapperType(
    deflateWrapper: WrapperType,
    inflateWrapper: WrapperType,
  ): Unit = {
    val compr   = new Array[Byte](comprLen)
    val uncompr = new Array[Byte](uncomprLen)

    val deflater = new Deflater
    var err      = deflater.init(Z_DEFAULT_COMPRESSION, DEF_WBITS, 9, deflateWrapper)
    assertEquals(err, Z_OK)

    deflater.setInput(data)
    deflater.setOutput(compr)
    err = deflater.deflate(Z_FINISH)
    assertEquals(err, Z_STREAM_END)
    err = deflater.end()
    assertEquals(err, Z_OK)

    val inflater = new Inflater
    err = inflater.init(DEF_WBITS, inflateWrapper)
    assertEquals(err, Z_OK)

    inflater.setInput(compr)
    var loop = true
    while (loop) {
      inflater.setOutput(uncompr)
      err = inflater.inflate(Z_NO_FLUSH)
      if (err == Z_STREAM_END) loop = false
      else assertEquals(err, Z_OK)
    }
    err = inflater.end()
    assertEquals(err, Z_OK)

    val totalOut = inflater.total_out.toInt
    assertEquals(new String(uncompr, 0, totalOut), new String(data))
  }

  private def assertInflateFails(
    deflateWrapper: WrapperType,
    inflateWrapper: WrapperType,
  ): Unit = {
    val compr   = new Array[Byte](comprLen)
    val uncompr = new Array[Byte](uncomprLen)

    val deflater = new Deflater
    var err      = deflater.init(Z_DEFAULT_COMPRESSION, DEF_WBITS, 9, deflateWrapper)
    assertEquals(err, Z_OK)

    deflater.setInput(data)
    deflater.setOutput(compr)
    err = deflater.deflate(Z_FINISH)
    assertEquals(err, Z_STREAM_END)
    err = deflater.end()
    assertEquals(err, Z_OK)

    val inflater = new Inflater
    err = inflater.init(DEF_WBITS, inflateWrapper)
    assertEquals(err, Z_OK)

    inflater.setInput(compr)
    var loop = true
    while (loop) {
      inflater.setOutput(uncompr)
      err = inflater.inflate(Z_NO_FLUSH)
      if (err == Z_STREAM_END) loop = false
      else {
        assertEquals(err, Z_DATA_ERROR)
        loop = false
      }
    }
  }

  private def deflateData(
    deflater: Deflater,
    input: Array[Byte],
    compr: Array[Byte],
  ): Unit = {
    deflater.setInput(input)
    deflater.setOutput(compr)
    var err = deflater.deflate(Z_FINISH)
    assertEquals(err, Z_STREAM_END)
    err = deflater.end()
    assertEquals(err, Z_OK)
  }

  private def inflateData(
    inflater: Inflater,
    compr: Array[Byte],
    uncompr: Array[Byte],
  ): Array[Byte] = {
    inflater.setInput(compr)
    var loop = true
    var err  = Z_OK
    while (loop) {
      inflater.setOutput(uncompr)
      err = inflater.inflate(Z_NO_FLUSH)
      if (err == Z_STREAM_END) loop = false
      else assertEquals(err, Z_OK)
    }
    err = inflater.end()
    assertEquals(err, Z_OK)

    val totalOut = inflater.total_out.toInt
    val result   = new Array[Byte](totalOut)
    System.arraycopy(uncompr, 0, result, 0, totalOut)
    result
  }
}
