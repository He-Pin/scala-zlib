package com.jcraft.jzlib

class CompanionObjectSuite extends munit.FunSuite {

  private val sampleData: Array[Byte] =
    "The quick brown fox jumps over the lazy dog".getBytes("UTF-8")

  private def compress(d: Deflater): Array[Byte] = {
    d.setInput(sampleData)
    val buf = new Array[Byte](256)
    d.setOutput(buf)
    d.deflate(JZlib.Z_FINISH)
    val out = new Array[Byte](d.total_out.toInt)
    System.arraycopy(buf, 0, out, 0, out.length)
    d.end()
    out
  }

  test("Deflater.apply() creates default deflater") {
    val d = Deflater()
    try {
      d.setInput(sampleData)
      val out = new Array[Byte](256)
      d.setOutput(out)
      val ret = d.deflate(JZlib.Z_FINISH)
      assert(ret == JZlib.Z_STREAM_END, s"expected Z_STREAM_END, got $ret")
      assert(d.total_out > 0, "should produce compressed output")
    } finally d.end()
  }

  test("Deflater(level) creates deflater with specified level") {
    val d = Deflater(JZlib.Z_BEST_COMPRESSION)
    try {
      d.setInput(sampleData)
      val out = new Array[Byte](256)
      d.setOutput(out)
      val ret = d.deflate(JZlib.Z_FINISH)
      assert(ret == JZlib.Z_STREAM_END, s"expected Z_STREAM_END, got $ret")
      assert(d.total_out > 0, "should produce compressed output")
    } finally d.end()
  }

  test("Deflater(level, wrapperType) creates deflater with wrapper type") {
    val d = Deflater(JZlib.Z_DEFAULT_COMPRESSION, JZlib.W_GZIP)
    try {
      d.setInput(sampleData)
      val out = new Array[Byte](256)
      d.setOutput(out)
      val ret = d.deflate(JZlib.Z_FINISH)
      assert(ret == JZlib.Z_STREAM_END, s"expected Z_STREAM_END, got $ret")
      // GZIP header starts with magic bytes 0x1f 0x8b
      assertEquals(out(0), 0x1f.toByte)
      assertEquals(out(1), 0x8b.toByte)
    } finally d.end()
  }

  test("Deflater.gzip() creates GZIP deflater") {
    val d = Deflater.gzip()
    try {
      d.setInput(sampleData)
      val out = new Array[Byte](256)
      d.setOutput(out)
      val ret = d.deflate(JZlib.Z_FINISH)
      assert(ret == JZlib.Z_STREAM_END, s"expected Z_STREAM_END, got $ret")
      // GZIP magic bytes
      assertEquals(out(0), 0x1f.toByte)
      assertEquals(out(1), 0x8b.toByte)
    } finally d.end()
  }

  test("Inflater.apply() creates default inflater") {
    val compressed = compress(Deflater())
    val i          = Inflater()
    try {
      i.setInput(compressed)
      val result = new Array[Byte](256)
      i.setOutput(result)
      val ret    = i.inflate(JZlib.Z_NO_FLUSH)
      assert(ret == JZlib.Z_STREAM_END, s"expected Z_STREAM_END, got $ret")
      assertEquals(new String(result, 0, i.total_out.toInt, "UTF-8"), new String(sampleData, "UTF-8"))
    } finally i.end()
  }

  test("Inflater(wrapperType) creates inflater with wrapper type") {
    val compressed = compress(Deflater.gzip())
    val i          = Inflater(JZlib.W_GZIP)
    try {
      i.setInput(compressed)
      val result = new Array[Byte](256)
      i.setOutput(result)
      val ret    = i.inflate(JZlib.Z_NO_FLUSH)
      assert(ret == JZlib.Z_STREAM_END, s"expected Z_STREAM_END, got $ret")
      assertEquals(new String(result, 0, i.total_out.toInt, "UTF-8"), new String(sampleData, "UTF-8"))
    } finally i.end()
  }

  test("Inflater.auto() auto-detects ZLIB format") {
    val compressed = compress(Deflater())
    val i          = Inflater.auto()
    try {
      i.setInput(compressed)
      val result = new Array[Byte](256)
      i.setOutput(result)
      val ret    = i.inflate(JZlib.Z_NO_FLUSH)
      assert(ret == JZlib.Z_STREAM_END, s"expected Z_STREAM_END, got $ret")
      assertEquals(new String(result, 0, i.total_out.toInt, "UTF-8"), new String(sampleData, "UTF-8"))
    } finally i.end()
  }

  test("Inflater.auto() auto-detects GZIP format") {
    val compressed = compress(Deflater.gzip())
    val i          = Inflater.auto()
    try {
      i.setInput(compressed)
      val result = new Array[Byte](256)
      i.setOutput(result)
      val ret    = i.inflate(JZlib.Z_NO_FLUSH)
      assert(ret == JZlib.Z_STREAM_END, s"expected Z_STREAM_END, got $ret")
      assertEquals(new String(result, 0, i.total_out.toInt, "UTF-8"), new String(sampleData, "UTF-8"))
    } finally i.end()
  }

  test("round-trip: Deflater.gzip() + Inflater.gzip()") {
    val compressed = compress(Deflater.gzip())
    val i          = Inflater.gzip()
    try {
      i.setInput(compressed)
      val result    = new Array[Byte](256)
      i.setOutput(result)
      val ret       = i.inflate(JZlib.Z_NO_FLUSH)
      assert(ret == JZlib.Z_STREAM_END, s"expected Z_STREAM_END, got $ret")
      val recovered = new String(result, 0, i.total_out.toInt, "UTF-8")
      assertEquals(recovered, new String(sampleData, "UTF-8"))
    } finally i.end()
  }
}
