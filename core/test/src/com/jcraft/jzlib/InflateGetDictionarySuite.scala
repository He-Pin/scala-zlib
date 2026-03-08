package com.jcraft.jzlib

import JZlib._

class InflateGetDictionarySuite extends munit.FunSuite {

  test("getDictionary on fresh inflater returns empty dictionary") {
    val inflater = new Inflater()
    inflater.init()

    val dict    = new Array[Byte](32768)
    val dictLen = new Array[Int](1)
    val err     = inflater.getDictionary(dict, dictLen)
    assertEquals(err, Z_OK)
    assertEquals(dictLen(0), 0)
    inflater.end()
  }

  test("getDictionary after normal inflate returns decompressed data") {
    val original = "Hello, inflateGetDictionary! " * 10
    val src      = original.getBytes("UTF-8")

    // Compress
    val deflater   = new Deflater()
    deflater.init(Z_DEFAULT_COMPRESSION)
    val compressed = new Array[Byte](1024)
    deflater.setInput(src)
    deflater.setOutput(compressed)
    assertEquals(deflater.deflate(Z_FINISH), Z_STREAM_END)
    val compLen    = deflater.next_out_index.toInt
    deflater.end()

    // Decompress
    val inflater     = new Inflater()
    inflater.init()
    val decompressed = new Array[Byte](src.length)
    inflater.setInput(compressed, 0, compLen, false)
    inflater.setOutput(decompressed)
    assertEquals(inflater.inflate(Z_FINISH), Z_STREAM_END)

    // getDictionary — should return the last bytes of decompressed output
    val dict    = new Array[Byte](32768)
    val dictLen = new Array[Int](1)
    assertEquals(inflater.getDictionary(dict, dictLen), Z_OK)
    assert(dictLen(0) > 0, s"dictLen should be > 0 but was ${dictLen(0)}")
    assertEquals(dictLen(0), src.length)

    // The dictionary content should match the tail of the decompressed data
    val dictSlice = dict.slice(0, dictLen(0))
    assert(
      java.util.Arrays.equals(dictSlice, src),
      "dictionary should match decompressed output",
    )
    inflater.end()
  }

  test(
    "getDictionary after inflate with preset dictionary includes preset bytes",
  ) {
    val presetDict = "common words: the of and to a in".getBytes("UTF-8")
    val original   = "the the the of of and to a in the".getBytes("UTF-8")

    // Compress with preset dictionary
    val deflater   = new Deflater()
    deflater.init(Z_DEFAULT_COMPRESSION)
    deflater.setDictionary(presetDict, presetDict.length)
    val compressed = new Array[Byte](1024)
    deflater.setInput(original)
    deflater.setOutput(compressed)
    assertEquals(deflater.deflate(Z_FINISH), Z_STREAM_END)
    val compLen    = deflater.next_out_index.toInt
    deflater.end()

    // Decompress — will get Z_NEED_DICT
    val inflater     = new Inflater()
    inflater.init()
    val decompressed = new Array[Byte](original.length)
    inflater.setInput(compressed, 0, compLen, false)
    inflater.setOutput(decompressed)
    val err1         = inflater.inflate(Z_NO_FLUSH)
    assertEquals(err1, Z_NEED_DICT)

    // Supply dictionary and continue
    inflater.setDictionary(presetDict, presetDict.length)
    val err2 = inflater.inflate(Z_FINISH)
    assertEquals(err2, Z_STREAM_END)

    // getDictionary — should reflect preset dict + decompressed output
    val dict    = new Array[Byte](32768)
    val dictLen = new Array[Int](1)
    assertEquals(inflater.getDictionary(dict, dictLen), Z_OK)
    assert(
      dictLen(0) >= original.length,
      s"dictLen (${dictLen(0)}) should be >= original.length (${original.length})",
    )

    // The dictionary tail should end with the decompressed output
    val dictData = dict.slice(0, dictLen(0))
    val tail     =
      dictData.slice(dictData.length - original.length, dictData.length)
    assert(
      java.util.Arrays.equals(tail, original),
      "dictionary tail should match decompressed output",
    )
    inflater.end()
  }

  test("getDictionary with null dictionary only returns length") {
    val src = "test data for null dict query".getBytes("UTF-8")

    // Compress
    val deflater   = new Deflater()
    deflater.init(Z_DEFAULT_COMPRESSION)
    val compressed = new Array[Byte](1024)
    deflater.setInput(src)
    deflater.setOutput(compressed)
    assertEquals(deflater.deflate(Z_FINISH), Z_STREAM_END)
    val compLen    = deflater.next_out_index.toInt
    deflater.end()

    // Decompress
    val inflater     = new Inflater()
    inflater.init()
    val decompressed = new Array[Byte](src.length)
    inflater.setInput(compressed, 0, compLen, false)
    inflater.setOutput(decompressed)
    assertEquals(inflater.inflate(Z_FINISH), Z_STREAM_END)

    // Query length only
    val dictLen = new Array[Int](1)
    assertEquals(inflater.getDictionary(null, dictLen), Z_OK)
    assertEquals(dictLen(0), src.length)
    inflater.end()
  }
}
