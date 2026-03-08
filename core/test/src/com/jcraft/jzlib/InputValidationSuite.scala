package com.jcraft.jzlib

import JZlib._

class InputValidationSuite extends munit.FunSuite {

  // --- Null input checks (ported from madler/zlib 5c47755) ---

  test("compress rejects null input") {
    intercept[ZStreamException] {
      JZlib.compress(null)
    }
  }

  test("uncompress rejects null input") {
    intercept[ZStreamException] {
      JZlib.uncompress(null)
    }
  }

  test("uncompress2 rejects null input") {
    intercept[ZStreamException] {
      JZlib.uncompress2(null)
    }
  }

  // --- State validation (ported from madler/zlib b516b4b) ---

  test("deflate on uninitialized Deflater returns Z_STREAM_ERROR") {
    val deflater = new Deflater()
    deflater.end()
    val err      = deflater.deflate(Z_NO_FLUSH)
    assertEquals(err, Z_STREAM_ERROR)
    assert(deflater.getMessage != null, "expected a descriptive error message")
  }

  test("deflateParams on uninitialized Deflater returns Z_STREAM_ERROR with message") {
    val deflater = new Deflater()
    deflater.end()
    val err      = deflater.params(Z_DEFAULT_COMPRESSION, Z_DEFAULT_STRATEGY)
    assertEquals(err, Z_STREAM_ERROR)
    assert(deflater.getMessage != null)
  }

  test("deflateSetDictionary on uninitialized Deflater returns Z_STREAM_ERROR") {
    val deflater = new Deflater()
    deflater.end()
    val dict     = "test dictionary".getBytes
    val err      = deflater.setDictionary(dict, dict.length)
    assertEquals(err, Z_STREAM_ERROR)
  }

  test("inflate on re-initialized Inflater works after end") {
    val inflater = new Inflater()
    inflater.end()
    // Re-init should work
    val err      = inflater.init()
    assertEquals(err, Z_OK)
    inflater.end()
  }

  // --- Negative length in combine (ported from madler/zlib ba829a4) ---

  test("CRC32.combine rejects negative len2") {
    intercept[IllegalArgumentException] {
      CRC32.combine(0L, 0L, -1L)
    }
  }

  test("CRC32.combine accepts zero len2") {
    val crc1 = 12345L
    assertEquals(CRC32.combine(crc1, 0L, 0L), crc1)
  }

  test("Adler32.combine rejects negative len2") {
    intercept[IllegalArgumentException] {
      Adler32.combine(1L, 1L, -1L)
    }
  }

  test("JZlib.crc32_combine rejects negative len2") {
    intercept[IllegalArgumentException] {
      JZlib.crc32_combine(0L, 0L, -5L)
    }
  }

  test("JZlib.adler32_combine rejects negative len2") {
    intercept[IllegalArgumentException] {
      JZlib.adler32_combine(1L, 1L, -5L)
    }
  }

  // --- Window size validation (ported from madler/zlib 049578f) ---

  test("windowBits=8 with zlib wrapper is silently upgraded to 9") {
    val deflater = new Deflater()
    val err      = deflater.init(Z_DEFAULT_COMPRESSION, 8, false)
    assertEquals(err, Z_OK)
    val data     = "hello window test".getBytes
    val output   = new Array[Byte](1024)
    deflater.setInput(data)
    deflater.setOutput(output)
    val ret      = deflater.deflate(Z_FINISH)
    assert(ret == Z_STREAM_END || ret == Z_OK)
    deflater.end()
  }

  test("windowBits=8 with raw deflate (nowrap) is rejected") {
    val deflater = new Deflater()
    val err      = deflater.init(Z_DEFAULT_COMPRESSION, 8, true)
    assertEquals(err, Z_STREAM_ERROR)
  }

  test("windowBits=8 with GZIP wrapper is rejected via init with wrapperType") {
    val deflater = new Deflater()
    val err      = deflater.init(Z_DEFAULT_COMPRESSION, 8, 8, W_GZIP)
    assertEquals(err, Z_STREAM_ERROR)
  }

  test("windowBits=8 with W_NONE is rejected via init with wrapperType") {
    val deflater = new Deflater()
    val err      = deflater.init(Z_DEFAULT_COMPRESSION, 8, 8, W_NONE)
    assertEquals(err, Z_STREAM_ERROR)
  }

  test("windowBits=8 with W_ZLIB is accepted via init with wrapperType") {
    val deflater = new Deflater()
    val err      = deflater.init(Z_DEFAULT_COMPRESSION, 8, 8, W_ZLIB)
    assertEquals(err, Z_OK)
    deflater.end()
  }

  // --- Null buffer checks for setInput / setOutput ---

  test("Deflater.setInput rejects null buffer") {
    val deflater = Deflater()
    intercept[NullPointerException] {
      deflater.setInput(null)
    }
    deflater.end()
  }

  test("Deflater.setOutput rejects null buffer") {
    val deflater = Deflater()
    intercept[NullPointerException] {
      deflater.setOutput(null)
    }
    deflater.end()
  }

  test("Inflater.setInput rejects null buffer") {
    val inflater = Inflater()
    intercept[NullPointerException] {
      inflater.setInput(null)
    }
    inflater.end()
  }

  test("Inflater.setOutput rejects null buffer") {
    val inflater = Inflater()
    intercept[NullPointerException] {
      inflater.setOutput(null)
    }
    inflater.end()
  }

  // --- Null / bounds checks for setDictionary ---

  test("Deflater.setDictionary rejects null dictionary") {
    val deflater = Deflater()
    intercept[NullPointerException] {
      deflater.setDictionary(null, 10)
    }
    deflater.end()
  }

  test("Deflater.setDictionary rejects negative dictLength") {
    val deflater = Deflater()
    val dict     = "test".getBytes
    intercept[IllegalArgumentException] {
      deflater.setDictionary(dict, -1)
    }
    deflater.end()
  }

  test("Inflater.setDictionary rejects null dictionary") {
    val inflater = Inflater()
    intercept[NullPointerException] {
      inflater.setDictionary(null, 10)
    }
    inflater.end()
  }

  test("Inflater.setDictionary rejects negative dictLength") {
    val inflater = Inflater()
    val dict     = "test".getBytes
    intercept[IllegalArgumentException] {
      inflater.setDictionary(dict, -1)
    }
    inflater.end()
  }

  test("windowBits=9 still works with all wrapper types") {
    val d1 = new Deflater()
    assertEquals(d1.init(Z_DEFAULT_COMPRESSION, 9, 8, W_ZLIB), Z_OK)
    d1.end()

    val d2 = new Deflater()
    assertEquals(d2.init(Z_DEFAULT_COMPRESSION, 9, 8, W_NONE), Z_OK)
    d2.end()

    val d3 = new Deflater()
    assertEquals(d3.init(Z_DEFAULT_COMPRESSION, 9, 8, W_GZIP), Z_OK)
    d3.end()
  }
}
