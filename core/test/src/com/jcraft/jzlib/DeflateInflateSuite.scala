package com.jcraft.jzlib

import JZlib._

class DeflateInflateSuite extends munit.FunSuite {

  val comprLen   = 40000
  val uncomprLen = comprLen

  test("deflate and inflate with Z_DEFAULT_COMPRESSION (small buffer)") {
    val data    = "hello, hello!".getBytes
    val compr   = new Array[Byte](comprLen)
    val uncompr = new Array[Byte](uncomprLen)

    val deflater = new Deflater
    var err      = deflater.init(Z_DEFAULT_COMPRESSION)
    assertEquals(err, Z_OK)

    deflater.setInput(data)
    deflater.setOutput(compr)

    while (deflater.total_in < data.length && deflater.total_out < comprLen) {
      deflater.avail_in = 1
      deflater.avail_out = 1
      err = deflater.deflate(Z_NO_FLUSH)
      assertEquals(err, Z_OK)
    }

    var finished = false
    while (!finished) {
      deflater.avail_out = 1
      err = deflater.deflate(Z_FINISH)
      if (err == Z_STREAM_END) finished = true
    }

    err = deflater.end()
    assertEquals(err, Z_OK)

    val inflater = new Inflater
    inflater.setInput(compr)
    inflater.setOutput(uncompr)

    err = inflater.init()
    assertEquals(err, Z_OK)

    var loop = true
    while (inflater.total_out < uncomprLen && inflater.total_in < comprLen && loop) {
      inflater.avail_in = 1
      inflater.avail_out = 1
      err = inflater.inflate(Z_NO_FLUSH)
      if (err == Z_STREAM_END) loop = false
      else assertEquals(err, Z_OK)
    }

    err = inflater.end()
    assertEquals(err, Z_OK)

    val totalOut = inflater.total_out.toInt
    val actual   = new Array[Byte](totalOut)
    System.arraycopy(uncompr, 0, actual, 0, totalOut)
    assertEquals(actual.toSeq, data.toSeq)
  }

  test("deflate and inflate in large buffer") {
    val compr   = new Array[Byte](comprLen)
    val uncompr = new Array[Byte](uncomprLen)

    val deflater = new Deflater
    var err      = deflater.init(Z_BEST_SPEED)
    assertEquals(err, Z_OK)

    deflater.setInput(uncompr)
    deflater.setOutput(compr)

    err = deflater.deflate(Z_NO_FLUSH)
    assertEquals(err, Z_OK)
    assertEquals(deflater.avail_in, 0)

    deflater.params(Z_NO_COMPRESSION, Z_DEFAULT_STRATEGY)
    deflater.setInput(compr)
    deflater.avail_in = comprLen / 2

    err = deflater.deflate(Z_NO_FLUSH)
    assertEquals(err, Z_OK)

    deflater.params(Z_BEST_COMPRESSION, Z_FILTERED)
    deflater.setInput(uncompr)
    deflater.avail_in = uncomprLen

    err = deflater.deflate(Z_NO_FLUSH)
    assertEquals(err, Z_OK)

    err = deflater.deflate(Z_FINISH)
    assertEquals(err, Z_STREAM_END)

    err = deflater.end()
    assertEquals(err, Z_OK)

    val inflater = new Inflater
    inflater.setInput(compr)
    err = inflater.init()
    assertEquals(err, Z_OK)

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
    assertEquals(totalOut, 2 * uncomprLen + comprLen / 2)
  }

  test("deflate and inflate with various compression levels") {
    val data     = "The quick brown fox jumps over the lazy dog. ".getBytes
    // Repeat data to make it compressible
    val repeated = Array.fill(20)(data).flatten

    (1 to 9).foreach { level =>
      val compr   = new Array[Byte](comprLen)
      val uncompr = new Array[Byte](uncomprLen)

      val deflater = new Deflater
      var err      = deflater.init(level)
      assertEquals(err, Z_OK, s"init failed at level $level")

      deflater.setInput(repeated)
      deflater.setOutput(compr)

      err = deflater.deflate(Z_FINISH)
      assertEquals(err, Z_STREAM_END, s"deflate failed at level $level")

      err = deflater.end()
      assertEquals(err, Z_OK)

      val inflater = new Inflater
      err = inflater.init()
      assertEquals(err, Z_OK)

      inflater.setInput(compr)
      inflater.setOutput(uncompr)

      var loop = true
      while (loop) {
        err = inflater.inflate(Z_NO_FLUSH)
        if (err == Z_STREAM_END) loop = false
        else assertEquals(err, Z_OK)
      }

      err = inflater.end()
      assertEquals(err, Z_OK)

      val totalOut = inflater.total_out.toInt
      val actual   = new Array[Byte](totalOut)
      System.arraycopy(uncompr, 0, actual, 0, totalOut)
      assertEquals(actual.toSeq, repeated.toSeq, s"data mismatch at level $level")
    }
  }

  test("deflate and inflate with nowrap (raw deflate)") {
    val data    = "hello, hello!".getBytes
    val compr   = new Array[Byte](comprLen)
    val uncompr = new Array[Byte](uncomprLen)

    val deflater = new Deflater
    var err      = deflater.init(Z_DEFAULT_COMPRESSION, MAX_WBITS, true)
    assertEquals(err, Z_OK)

    deflater.setInput(data)
    deflater.setOutput(compr)

    err = deflater.deflate(Z_FINISH)
    assertEquals(err, Z_STREAM_END)

    err = deflater.end()
    assertEquals(err, Z_OK)

    val inflater = new Inflater
    err = inflater.init(MAX_WBITS, true)
    assertEquals(err, Z_OK)

    inflater.setInput(compr)
    inflater.setOutput(uncompr)

    var loop = true
    while (loop) {
      err = inflater.inflate(Z_NO_FLUSH)
      if (err == Z_STREAM_END) loop = false
      else assertEquals(err, Z_OK)
    }

    err = inflater.end()
    assertEquals(err, Z_OK)

    val totalOut = inflater.total_out.toInt
    val actual   = new Array[Byte](totalOut)
    System.arraycopy(uncompr, 0, actual, 0, totalOut)
    assertEquals(actual.toSeq, data.toSeq)
  }

  test("deflate and inflate with preset dictionary") {
    val hello      = "hello".getBytes
    val dictionary = "hello, hello!".getBytes
    val compr      = new Array[Byte](comprLen)
    val uncompr    = new Array[Byte](uncomprLen)

    val deflater = new Deflater
    var err      = deflater.init(Z_DEFAULT_COMPRESSION)
    assertEquals(err, Z_OK)

    deflater.setDictionary(dictionary, dictionary.length)
    assertEquals(err, Z_OK)

    val dictID = deflater.getAdler

    deflater.setInput(hello)
    deflater.setOutput(compr)

    err = deflater.deflate(Z_FINISH)
    assertEquals(err, Z_STREAM_END)

    err = deflater.end()
    assertEquals(err, Z_OK)

    val inflater = new Inflater
    err = inflater.init()
    assertEquals(err, Z_OK)

    inflater.setInput(compr)
    inflater.setOutput(uncompr)

    var loop = true
    while (loop) {
      err = inflater.inflate(Z_NO_FLUSH)
      err match {
        case Z_STREAM_END =>
          loop = false
        case Z_NEED_DICT  =>
          assertEquals(inflater.getAdler, dictID)
          err = inflater.setDictionary(dictionary, dictionary.length)
          assertEquals(err, Z_OK)
        case _            =>
          assertEquals(err, Z_OK)
      }
    }

    err = inflater.end()
    assertEquals(err, Z_OK)

    val totalOut = inflater.total_out.toInt
    val actual   = new Array[Byte](totalOut)
    System.arraycopy(uncompr, 0, actual, 0, totalOut)
    assertEquals(actual.toSeq, hello.toSeq)
  }

  test("large data round-trip (>64KB)") {
    val rng     = new scala.util.Random(42)
    val data    = Array.fill[Byte](100000)(rng.nextInt(256).toByte)
    val compr   = new Array[Byte](data.length + 1000)
    val uncompr = new Array[Byte](data.length)

    val deflater = new Deflater
    var err      = deflater.init(Z_DEFAULT_COMPRESSION)
    assertEquals(err, Z_OK)

    deflater.setInput(data)
    deflater.setOutput(compr)

    err = deflater.deflate(Z_FINISH)
    assertEquals(err, Z_STREAM_END)

    err = deflater.end()
    assertEquals(err, Z_OK)

    val inflater = new Inflater
    err = inflater.init()
    assertEquals(err, Z_OK)

    inflater.setInput(compr)
    inflater.setOutput(uncompr)

    var loop = true
    while (loop) {
      err = inflater.inflate(Z_NO_FLUSH)
      if (err == Z_STREAM_END) loop = false
      else assertEquals(err, Z_OK)
    }

    err = inflater.end()
    assertEquals(err, Z_OK)

    val totalOut = inflater.total_out.toInt
    assertEquals(totalOut, data.length)
    assertEquals(uncompr.toSeq, data.toSeq)
  }

  test("empty input") {
    val data    = new Array[Byte](0)
    val compr   = new Array[Byte](comprLen)
    val uncompr = new Array[Byte](uncomprLen)

    val deflater = new Deflater
    var err      = deflater.init(Z_DEFAULT_COMPRESSION)
    assertEquals(err, Z_OK)

    deflater.setInput(data)
    deflater.setOutput(compr)

    err = deflater.deflate(Z_FINISH)
    assertEquals(err, Z_STREAM_END)

    err = deflater.end()
    assertEquals(err, Z_OK)

    val inflater = new Inflater
    err = inflater.init()
    assertEquals(err, Z_OK)

    inflater.setInput(compr)
    inflater.setOutput(uncompr)

    var loop = true
    while (loop) {
      err = inflater.inflate(Z_NO_FLUSH)
      if (err == Z_STREAM_END) loop = false
      else assertEquals(err, Z_OK)
    }

    err = inflater.end()
    assertEquals(err, Z_OK)

    assertEquals(inflater.total_out.toInt, 0)
  }

  test("inflate gzip data") {
    val hello   = "foo".getBytes
    val data    = List(0x1f, 0x8b, 0x08, 0x18, 0x08, 0xeb, 0x7a, 0x0b, 0x00, 0x0b,
      0x58, 0x00, 0x59, 0x00, 0x4b, 0xcb, 0xcf, 0x07, 0x00, 0x21, 0x65, 0x73,
      0x8c, 0x03, 0x00, 0x00, 0x00)
      .map(_.toByte)
      .toArray
    val uncompr = new Array[Byte](uncomprLen)

    val inflater = new Inflater
    var err      = inflater.init(15 + 32)
    assertEquals(err, Z_OK)

    inflater.setInput(data)
    inflater.setOutput(uncompr)

    val dataLen = data.length
    var loop    = true
    while (inflater.total_out < uncomprLen && inflater.total_in < dataLen && loop) {
      err = inflater.inflate(Z_NO_FLUSH)
      if (err == Z_STREAM_END) loop = false
      else assertEquals(err, Z_OK)
    }

    err = inflater.end()
    assertEquals(err, Z_OK)

    val totalOut = inflater.total_out.toInt
    val actual   = new Array[Byte](totalOut)
    System.arraycopy(uncompr, 0, actual, 0, totalOut)
    assertEquals(actual.toSeq, hello.toSeq)
  }

  test("deflate and inflate gzip data") {
    val data    = "hello, hello!".getBytes
    val compr   = new Array[Byte](comprLen)
    val uncompr = new Array[Byte](uncomprLen)

    val deflater = new Deflater
    var err      = deflater.init(Z_DEFAULT_COMPRESSION, 15 + 16)
    assertEquals(err, Z_OK)

    deflater.setInput(data)
    deflater.setOutput(compr)

    while (deflater.total_in < data.length && deflater.total_out < comprLen) {
      deflater.avail_in = 1
      deflater.avail_out = 1
      err = deflater.deflate(Z_NO_FLUSH)
      assertEquals(err, Z_OK)
    }

    var finished = false
    while (!finished) {
      deflater.avail_out = 1
      err = deflater.deflate(Z_FINISH)
      if (err == Z_STREAM_END) finished = true
    }

    err = deflater.end()
    assertEquals(err, Z_OK)

    val inflater = new Inflater
    inflater.setInput(compr)
    inflater.setOutput(uncompr)

    err = inflater.init(15 + 32)
    assertEquals(err, Z_OK)

    var loop = true
    while (inflater.total_out < uncomprLen && inflater.total_in < comprLen && loop) {
      inflater.avail_in = 1
      inflater.avail_out = 1
      err = inflater.inflate(Z_NO_FLUSH)
      if (err == Z_STREAM_END) loop = false
      else assertEquals(err, Z_OK)
    }

    err = inflater.end()
    assertEquals(err, Z_OK)

    val totalOut = inflater.total_out.toInt
    val actual   = new Array[Byte](totalOut)
    System.arraycopy(uncompr, 0, actual, 0, totalOut)
    assertEquals(actual.toSeq, data.toSeq)
  }

  test("sync support") {
    val hello   = "hello".getBytes
    val compr   = new Array[Byte](comprLen)
    val uncompr = new Array[Byte](uncomprLen)

    val deflater = new Deflater
    var err      = deflater.init(Z_DEFAULT_COMPRESSION)
    assertEquals(err, Z_OK)

    deflater.setInput(hello)
    deflater.avail_in = 3
    deflater.setOutput(compr)

    err = deflater.deflate(Z_FULL_FLUSH)
    assertEquals(err, Z_OK)

    compr(3) = (compr(3) + 1).toByte
    deflater.avail_in = hello.length - 3

    err = deflater.deflate(Z_FINISH)
    assertEquals(err, Z_STREAM_END)
    val compressedLen = deflater.total_out.toInt

    err = deflater.end()
    assertEquals(err, Z_OK)

    val inflater = new Inflater
    err = inflater.init()
    assertEquals(err, Z_OK)

    inflater.setInput(compr)
    inflater.avail_in = 2
    inflater.setOutput(uncompr)

    err = inflater.inflate(Z_NO_FLUSH)
    assertEquals(err, Z_OK)

    inflater.avail_in = compressedLen - 2
    err = inflater.sync()

    err = inflater.inflate(Z_FINISH)
    assertEquals(err, Z_DATA_ERROR)

    err = inflater.end()
    assertEquals(err, Z_OK)

    val totalOut = inflater.total_out.toInt
    val actual   = new Array[Byte](totalOut)
    System.arraycopy(uncompr, 0, actual, 0, totalOut)

    assertEquals("hel" + new String(actual), new String(hello))
  }

  test("deflate and inflate with Z_FULL_FLUSH") {
    val data    = "hello, hello! this is a full flush test.".getBytes
    val compr   = new Array[Byte](comprLen)
    val uncompr = new Array[Byte](uncomprLen)

    val deflater = new Deflater
    var err      = deflater.init(Z_DEFAULT_COMPRESSION)
    assertEquals(err, Z_OK)

    deflater.setInput(data)
    deflater.setOutput(compr)

    // Feed first half with Z_FULL_FLUSH
    deflater.avail_in = data.length / 2
    err = deflater.deflate(Z_FULL_FLUSH)
    assertEquals(err, Z_OK)

    // Feed second half and finish
    deflater.avail_in = data.length - data.length / 2
    err = deflater.deflate(Z_FINISH)
    assertEquals(err, Z_STREAM_END)

    err = deflater.end()
    assertEquals(err, Z_OK)

    val inflater = new Inflater
    err = inflater.init()
    assertEquals(err, Z_OK)

    inflater.setInput(compr)
    inflater.setOutput(uncompr)

    var loop = true
    while (loop) {
      err = inflater.inflate(Z_NO_FLUSH)
      if (err == Z_STREAM_END) loop = false
      else assertEquals(err, Z_OK)
    }

    err = inflater.end()
    assertEquals(err, Z_OK)

    val totalOut = inflater.total_out.toInt
    val actual   = new Array[Byte](totalOut)
    System.arraycopy(uncompr, 0, actual, 0, totalOut)
    assertEquals(actual.toSeq, data.toSeq)
  }

  test("deflate and inflate with Z_SYNC_FLUSH") {
    val data    = "hello, hello! this is a sync flush test.".getBytes
    val compr   = new Array[Byte](comprLen)
    val uncompr = new Array[Byte](uncomprLen)

    val deflater = new Deflater
    var err      = deflater.init(Z_DEFAULT_COMPRESSION)
    assertEquals(err, Z_OK)

    deflater.setInput(data)
    deflater.setOutput(compr)

    // Feed first half with Z_SYNC_FLUSH
    deflater.avail_in = data.length / 2
    err = deflater.deflate(Z_SYNC_FLUSH)
    assertEquals(err, Z_OK)

    // Feed second half and finish
    deflater.avail_in = data.length - data.length / 2
    err = deflater.deflate(Z_FINISH)
    assertEquals(err, Z_STREAM_END)

    err = deflater.end()
    assertEquals(err, Z_OK)

    val inflater = new Inflater
    err = inflater.init()
    assertEquals(err, Z_OK)

    inflater.setInput(compr)
    inflater.setOutput(uncompr)

    var loop = true
    while (loop) {
      err = inflater.inflate(Z_NO_FLUSH)
      if (err == Z_STREAM_END) loop = false
      else assertEquals(err, Z_OK)
    }

    err = inflater.end()
    assertEquals(err, Z_OK)

    val totalOut = inflater.total_out.toInt
    val actual   = new Array[Byte](totalOut)
    System.arraycopy(uncompr, 0, actual, 0, totalOut)
    assertEquals(actual.toSeq, data.toSeq)
  }

  test("deflate with different strategies") {
    val data = Array.fill(20)("The quick brown fox jumps over the lazy dog. ".getBytes).flatten

    val strategies = List(
      ("Z_FILTERED", Z_FILTERED),
      ("Z_HUFFMAN_ONLY", Z_HUFFMAN_ONLY),
      ("Z_RLE", Z_RLE),
      ("Z_FIXED", Z_FIXED),
    )

    strategies.foreach { case (name, strategy) =>
      val compr   = new Array[Byte](comprLen)
      val uncompr = new Array[Byte](uncomprLen)

      val deflater = new Deflater
      var err      = deflater.init(Z_DEFAULT_COMPRESSION)
      assertEquals(err, Z_OK, s"init failed for strategy $name")

      deflater.params(Z_DEFAULT_COMPRESSION, strategy)

      deflater.setInput(data)
      deflater.setOutput(compr)

      err = deflater.deflate(Z_FINISH)
      assertEquals(err, Z_STREAM_END, s"deflate failed for strategy $name")

      err = deflater.end()
      assertEquals(err, Z_OK)

      val inflater = new Inflater
      err = inflater.init()
      assertEquals(err, Z_OK)

      inflater.setInput(compr)
      inflater.setOutput(uncompr)

      var loop = true
      while (loop) {
        err = inflater.inflate(Z_NO_FLUSH)
        if (err == Z_STREAM_END) loop = false
        else assertEquals(err, Z_OK)
      }

      err = inflater.end()
      assertEquals(err, Z_OK)

      val totalOut = inflater.total_out.toInt
      val actual   = new Array[Byte](totalOut)
      System.arraycopy(uncompr, 0, actual, 0, totalOut)
      assertEquals(actual.toSeq, data.toSeq, s"data mismatch for strategy $name")
    }
  }

  test("inflate detects corrupted data") {
    val data  = "hello, hello! some data to compress for corruption test.".getBytes
    val compr = new Array[Byte](comprLen)

    val deflater = new Deflater
    var err      = deflater.init(Z_DEFAULT_COMPRESSION)
    assertEquals(err, Z_OK)

    deflater.setInput(data)
    deflater.setOutput(compr)

    err = deflater.deflate(Z_FINISH)
    assertEquals(err, Z_STREAM_END)

    val compressedLen = deflater.total_out.toInt
    err = deflater.end()
    assertEquals(err, Z_OK)

    // Corrupt a byte in the middle of the compressed data
    val corruptIdx = compressedLen / 2
    compr(corruptIdx) = (compr(corruptIdx) ^ 0xff).toByte

    val uncompr  = new Array[Byte](uncomprLen)
    val inflater = new Inflater
    err = inflater.init()
    assertEquals(err, Z_OK)

    inflater.setInput(compr)
    inflater.setOutput(uncompr)

    var gotError = false
    var loop     = true
    while (loop) {
      err = inflater.inflate(Z_NO_FLUSH)
      if (err == Z_DATA_ERROR) {
        gotError = true
        loop = false
      } else if (err == Z_STREAM_END) {
        loop = false
      }
    }

    inflater.end()
    assert(gotError, "Expected Z_DATA_ERROR from corrupted compressed data")
  }
}
