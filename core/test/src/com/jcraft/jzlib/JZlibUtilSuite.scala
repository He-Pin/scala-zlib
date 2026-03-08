package com.jcraft.jzlib

import JZlib._

class JZlibUtilSuite extends munit.FunSuite {

  test("deflateBound returns reasonable upper bound") {
    // For small data
    assert(deflateBound(100) >= 100)
    // For empty data — must cover wrapper overhead
    assert(deflateBound(0) > 0)
    // For 1 byte
    assert(deflateBound(1) > 1)
    // For 1MB
    assert(deflateBound(1024 * 1024) > 1024 * 1024)
    // For large data (100MB)
    assert(deflateBound(100L * 1024 * 1024) > 100L * 1024 * 1024)
  }

  test("compressBound equals deflateBound") {
    assertEquals(compressBound(1000), deflateBound(1000))
    assertEquals(compressBound(0), deflateBound(0))
  }

  test("compressed size within deflateBound for all levels") {
    val sizes  = Seq(0, 1, 10, 100, 1000, 10000, 100000)
    val levels = Seq(
      Z_NO_COMPRESSION,
      Z_BEST_SPEED,
      2,
      3,
      4,
      5,
      6,
      7,
      8,
      Z_BEST_COMPRESSION,
      Z_DEFAULT_COMPRESSION,
    )
    for {
      size  <- sizes
      level <- levels
    } {
      val original   = Array.fill[Byte](size)((scala.util.Random.nextInt(256) - 128).toByte)
      val compressed = {
        val d      = new Deflater()
        d.init(level)
        d.setInput(original)
        d.setNextInIndex(0)
        d.setAvailIn(original.length)
        val bound  = deflateBound(original.length.toLong).toInt
        val output = new Array[Byte](bound)
        d.setOutput(output)
        d.setNextOutIndex(0)
        d.setAvailOut(output.length)
        d.deflate(Z_FINISH)
        val len    = output.length - d.getAvailOut
        d.end()
        val result = new Array[Byte](len)
        System.arraycopy(output, 0, result, 0, len)
        result
      }
      assert(
        compressed.length <= deflateBound(original.length.toLong),
        s"level=$level size=$size: compressed=${compressed.length} > bound=${deflateBound(original.length.toLong)}",
      )
    }
  }

  test("deflateBound with level 0 (stored blocks)") {
    // Level 0 uses stored blocks which expand the data slightly;
    // deflateBound must cover this expansion
    val sizes = Seq(0, 1, 127, 128, 255, 256, 1000, 65535, 100000)
    for (size <- sizes) {
      val original   = Array.fill[Byte](size)((scala.util.Random.nextInt(256) - 128).toByte)
      val compressed = {
        val d      = new Deflater()
        d.init(Z_NO_COMPRESSION)
        d.setInput(original)
        d.setNextInIndex(0)
        d.setAvailIn(original.length)
        val bound  = deflateBound(original.length.toLong).toInt
        val output = new Array[Byte](bound)
        d.setOutput(output)
        d.setNextOutIndex(0)
        d.setAvailOut(output.length)
        d.deflate(Z_FINISH)
        val len    = output.length - d.getAvailOut
        d.end()
        len
      }
      assert(
        compressed <= deflateBound(original.length.toLong),
        s"level=0 size=$size: compressed=$compressed > bound=${deflateBound(original.length.toLong)}",
      )
    }
  }

  test("Deflater.deflateBound returns tighter bound with default settings") {
    val d             = Deflater()
    // Instance bound for default settings should be <= static bound
    val sourceLen     = 100000L
    val instanceBound = d.deflateBound(sourceLen)
    val staticBound   = deflateBound(sourceLen)
    assert(
      instanceBound <= staticBound,
      s"instance=$instanceBound should be <= static=$staticBound",
    )
    assert(instanceBound > sourceLen, s"instance=$instanceBound should be > sourceLen=$sourceLen")
    d.end()
  }

  test("Deflater.deflateBound falls back to static bound when not initialized") {
    val d = new Deflater()
    d.end()
    assertEquals(d.deflateBound(1000L), deflateBound(1000L))
  }

  test("compress and uncompress round-trip") {
    val original     = "Hello, World! This is a test of one-shot compression.".getBytes("UTF-8")
    val compressed   = compress(original)
    val decompressed = uncompress(compressed)
    assertEquals(new String(decompressed, "UTF-8"), new String(original, "UTF-8"))
  }

  test("compress and uncompress with large data") {
    val original     = Array.fill[Byte](100000)((scala.util.Random.nextInt(256) - 128).toByte)
    val compressed   = compress(original)
    val decompressed = uncompress(compressed)
    assert(original.sameElements(decompressed))
  }

  test("compress produces smaller output for compressible data") {
    val original   = Array.fill[Byte](10000)(42)
    val compressed = compress(original)
    assert(
      compressed.length < original.length,
      s"Expected compressed (${compressed.length}) < original (${original.length})",
    )
  }

  test("compressed size within deflateBound") {
    val original   = "Test data for bound verification".getBytes("UTF-8")
    val compressed = compress(original)
    assert(compressed.length <= deflateBound(original.length.toLong))
  }

  test("uncompress throws on invalid data") {
    intercept[ZStreamException] {
      uncompress(Array[Byte](1, 2, 3, 4, 5))
    }
  }

  test("compress empty array") {
    val original   = new Array[Byte](0)
    val compressed = compress(original)
    assert(compressed.length > 0, "Compressed empty array should produce non-empty output (header/trailer)")
  }

  test("uncompress empty compressed data") {
    val original     = new Array[Byte](0)
    val compressed   = compress(original)
    val decompressed = uncompress(compressed)
    assertEquals(decompressed.length, 0)
  }

  test("uncompress2 returns correct data and input byte count") {
    val original   = "Hello, uncompress2!".getBytes("UTF-8")
    val compressed = compress(original)
    val result     = uncompress2(compressed)
    assertEquals(new String(result.data, "UTF-8"), new String(original, "UTF-8"))
    assertEquals(result.inputBytesUsed, compressed.length)
  }

  test("uncompress2 with trailing bytes after compressed stream") {
    val original   = "Data before trailing bytes".getBytes("UTF-8")
    val compressed = compress(original)
    val trailing   = Array[Byte](0xde.toByte, 0xad.toByte, 0xbe.toByte, 0xef.toByte)
    val withExtra  = new Array[Byte](compressed.length + trailing.length)
    System.arraycopy(compressed, 0, withExtra, 0, compressed.length)
    System.arraycopy(trailing, 0, withExtra, compressed.length, trailing.length)

    val result = uncompress2(withExtra)
    assertEquals(new String(result.data, "UTF-8"), new String(original, "UTF-8"))
    assertEquals(result.inputBytesUsed, compressed.length)
  }

  test("uncompress2 with exact-fit compressed data") {
    val original   = Array.fill[Byte](10000)(42)
    val compressed = compress(original)
    val result     = uncompress2(compressed)
    assert(original.sameElements(result.data))
    assertEquals(result.inputBytesUsed, compressed.length)
  }

  test("gzip and gunzip round-trip") {
    val original     = "Hello, World! This is a test of one-shot GZIP compression.".getBytes("UTF-8")
    val compressed   = gzip(original)
    val decompressed = gunzip(compressed)
    assertEquals(new String(decompressed, "UTF-8"), new String(original, "UTF-8"))
  }

  test("gzip and gunzip round-trip with large data") {
    val original     = Array.fill[Byte](100000)((scala.util.Random.nextInt(256) - 128).toByte)
    val compressed   = gzip(original)
    val decompressed = gunzip(compressed)
    assert(original.sameElements(decompressed))
  }

  test("gzip produces valid GZIP data (magic bytes 0x1f, 0x8b)") {
    val original   = "Test GZIP magic bytes".getBytes("UTF-8")
    val compressed = gzip(original)
    assert(compressed.length >= 2, "GZIP output must be at least 2 bytes")
    assertEquals(compressed(0) & 0xff, 0x1f, "First magic byte must be 0x1f")
    assertEquals(compressed(1) & 0xff, 0x8b, "Second magic byte must be 0x8b")
  }

  test("gunzip of known GZIP data") {
    // Compress with gzip, then verify gunzip reproduces the original
    val original     = Array.fill[Byte](1000)(0x42.toByte)
    val compressed   = gzip(original)
    // Verify it starts with GZIP magic
    assertEquals(compressed(0) & 0xff, 0x1f)
    assertEquals(compressed(1) & 0xff, 0x8b)
    // Decompress and check
    val decompressed = gunzip(compressed)
    assert(original.sameElements(decompressed))
  }

  test("gzip empty array") {
    val original     = new Array[Byte](0)
    val compressed   = gzip(original)
    assert(compressed.length > 0, "GZIP of empty array should produce non-empty output (header/trailer)")
    assertEquals(compressed(0) & 0xff, 0x1f)
    assertEquals(compressed(1) & 0xff, 0x8b)
    val decompressed = gunzip(compressed)
    assertEquals(decompressed.length, 0)
  }

  test("compressBound with level parameter") {
    // For non-zero levels, should equal deflateBound
    assertEquals(compressBound(1000L, Z_DEFAULT_COMPRESSION), deflateBound(1000L))
    assertEquals(compressBound(1000L, Z_BEST_SPEED), deflateBound(1000L))
    assertEquals(compressBound(1000L, Z_BEST_COMPRESSION), deflateBound(1000L))

    // For level 0, should account for stored-block overhead
    val level0Bound = compressBound(100000L, Z_NO_COMPRESSION)
    assert(level0Bound > 100000L, "Level 0 bound must be > source length")

    // Verify level 0 bound is actually sufficient
    val original   = Array.fill[Byte](100000)((scala.util.Random.nextInt(256) - 128).toByte)
    val compressed = {
      val d      = new Deflater()
      d.init(Z_NO_COMPRESSION)
      d.setInput(original)
      d.setNextInIndex(0)
      d.setAvailIn(original.length)
      val bound  = compressBound(original.length.toLong, Z_NO_COMPRESSION).toInt
      val output = new Array[Byte](bound)
      d.setOutput(output)
      d.setNextOutIndex(0)
      d.setAvailOut(output.length)
      d.deflate(Z_FINISH)
      val len    = output.length - d.getAvailOut
      d.end()
      len
    }
    assert(
      compressed <= level0Bound,
      s"level=0: compressed=$compressed > bound=$level0Bound",
    )
  }
}
