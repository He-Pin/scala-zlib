package com.jcraft.jzlib

import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole
import java.util.concurrent.TimeUnit

// ─── Additional benchmark state for comprehensive coverage ─────────────────

@State(Scope.Thread)
class ComprehensiveBenchmarkData {

  var smallData: Array[Byte]  = _
  var largeData: Array[Byte]  = _
  var oneMBData: Array[Byte]  = _
  var dictionary: Array[Byte] = _

  // Pre-compressed with zlib wrapper
  var compressedSmall: Array[Byte] = _
  var compressedLarge: Array[Byte] = _
  var compressedOneMB: Array[Byte] = _

  // Pre-compressed with GZIP wrapper (via JZlib.gzip)
  var gzipSmall: Array[Byte] = _
  var gzipLarge: Array[Byte] = _

  // CRC32 values for combine benchmarks
  var crc1: Long     = _
  var crc2: Long     = _
  var halfLen: Int   = _
  var combineOp: Int = _

  // Adler32 values for combine benchmarks
  var adler1: Long = _
  var adler2: Long = _

  // Pre-built GZIPHeader for read benchmarks
  var prebuiltHeader: GZIPHeader = _

  // Deflater with data fed for getDictionary benchmark
  var dictCompressedSmall: Array[Byte] = _

  // Sync-flush compressed data for inflater sync benchmarks
  var syncFlushCompressed: Array[Byte] = _

  @Setup(Level.Trial)
  def setup(): Unit = {
    smallData = ("Hello, World! " * 73).getBytes("UTF-8")

    largeData = new Array[Byte](65536)
    val rng     = new java.util.Random(42)
    rng.nextBytes(largeData)
    val pattern = "PATTERN_DATA_HERE".getBytes
    for (i <- 0 until largeData.length by 128)
      System.arraycopy(pattern, 0, largeData, i, Math.min(pattern.length, largeData.length - i))

    oneMBData = new Array[Byte](1024 * 1024)
    val rng2 = new java.util.Random(123)
    rng2.nextBytes(oneMBData)
    val pat2 = "REPEATING_BENCHMARK_PATTERN_FOR_REALISTIC_DATA".getBytes
    for (i <- 0 until oneMBData.length by 256)
      System.arraycopy(pat2, 0, oneMBData, i, Math.min(pat2.length, oneMBData.length - i))

    dictionary = "Hello World Pattern Data Benchmark Test Compress".getBytes("UTF-8")

    // Zlib-compressed data
    compressedSmall = JZlib.compress(smallData)
    compressedLarge = JZlib.compress(largeData)
    compressedOneMB = JZlib.compress(oneMBData)

    // GZIP-compressed data
    gzipSmall = JZlib.gzip(smallData)
    gzipLarge = JZlib.gzip(largeData)

    // CRC32 combine operands
    halfLen = oneMBData.length / 2
    val c1 = new CRC32(); c1.update(oneMBData, 0, halfLen); crc1 = c1.getValue
    val c2 = new CRC32(); c2.update(oneMBData, halfLen, halfLen); crc2 = c2.getValue
    combineOp = CRC32.combineGen(halfLen.toLong)

    // Adler32 combine operands
    val a1 = new Adler32(); a1.update(oneMBData, 0, halfLen); adler1 = a1.getValue
    val a2 = new Adler32(); a2.update(oneMBData, halfLen, halfLen); adler2 = a2.getValue

    // Pre-built GZIPHeader
    prebuiltHeader = new GZIPHeader()
    prebuiltHeader.setName("benchmark-data.txt")
    prebuiltHeader.setComment("benchmark test data")
    prebuiltHeader.setModifiedTime(1700000000L)
    prebuiltHeader.setOS(GZIPHeader.OS_UNIX)

    // Dictionary-compressed data
    dictCompressedSmall = {
      val d        = new Deflater
      d.init(JZlib.Z_DEFAULT_COMPRESSION)
      d.setDictionary(dictionary, dictionary.length)
      d.setInput(smallData)
      val out      = new Array[Byte](smallData.length * 2 + 256)
      d.setOutput(out)
      var finished = false
      while (!finished) {
        val err = d.deflate(JZlib.Z_FINISH)
        if (err == JZlib.Z_STREAM_END) finished = true
        else if (err < 0) throw new RuntimeException(s"Deflate failed: $err")
      }
      val len      = d.next_out_index
      d.end()
      java.util.Arrays.copyOf(out, len)
    }

    // Sync-flush compressed data
    syncFlushCompressed = {
      val d         = new Deflater
      d.init(JZlib.Z_DEFAULT_COMPRESSION)
      val out       = new Array[Byte](largeData.length * 2 + 256)
      d.setOutput(out)
      val chunkSize = largeData.length / 4
      for (i <- 0 until 4) {
        val off   = i * chunkSize
        val len   = if (i == 3) largeData.length - off else chunkSize
        val chunk = java.util.Arrays.copyOfRange(largeData, off, off + len)
        d.setInput(chunk)
        val flush = if (i < 3) JZlib.Z_SYNC_FLUSH else JZlib.Z_FINISH
        var done  = false
        while (!done) {
          val err = d.deflate(flush)
          if (err == JZlib.Z_STREAM_END) done = true
          else if (err == JZlib.Z_OK) done = true
          else if (err < 0) throw new RuntimeException(s"Deflate flush failed: $err")
        }
      }
      val len = d.next_out_index
      d.end()
      java.util.Arrays.copyOf(out, len)
    }
  }
}

// ─── Comprehensive API Coverage Benchmarks ─────────────────────────────────

@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
class ComprehensiveBenchmark {

  // ── 1. JZlib.gzip() / JZlib.gunzip() one-shot ──

  @Benchmark
  def gzipSmall(state: ComprehensiveBenchmarkData, bh: Blackhole): Unit =
    bh.consume(JZlib.gzip(state.smallData))

  @Benchmark
  def gzipLarge(state: ComprehensiveBenchmarkData, bh: Blackhole): Unit =
    bh.consume(JZlib.gzip(state.largeData))

  @Benchmark
  def gunzipSmall(state: ComprehensiveBenchmarkData, bh: Blackhole): Unit =
    bh.consume(JZlib.gunzip(state.gzipSmall))

  @Benchmark
  def gunzipLarge(state: ComprehensiveBenchmarkData, bh: Blackhole): Unit =
    bh.consume(JZlib.gunzip(state.gzipLarge))

  // ── 2. JZlib.uncompress2() ──

  @Benchmark
  def uncompress2Small(state: ComprehensiveBenchmarkData, bh: Blackhole): Unit = {
    val result = JZlib.uncompress2(state.compressedSmall)
    bh.consume(result.data)
    bh.consume(result.inputBytesUsed)
  }

  @Benchmark
  def uncompress2Large(state: ComprehensiveBenchmarkData, bh: Blackhole): Unit = {
    val result = JZlib.uncompress2(state.compressedLarge)
    bh.consume(result.data)
    bh.consume(result.inputBytesUsed)
  }

  // ── 3. CRC32.combineGen() + combineOp() ──

  @Benchmark
  def crc32CombineGen(state: ComprehensiveBenchmarkData, bh: Blackhole): Unit =
    bh.consume(CRC32.combineGen(state.halfLen.toLong))

  @Benchmark
  def crc32CombineOp(state: ComprehensiveBenchmarkData, bh: Blackhole): Unit =
    bh.consume(CRC32.combineOp(state.combineOp, state.crc1, state.crc2))

  @Benchmark
  def crc32CombineGenThenOp(state: ComprehensiveBenchmarkData, bh: Blackhole): Unit = {
    val op = CRC32.combineGen(state.halfLen.toLong)
    bh.consume(CRC32.combineOp(op, state.crc1, state.crc2))
  }

  // ── 4. Adler32.combine() (with pre-computed operands) ──

  @Benchmark
  def adler32CombinePrecomputed(state: ComprehensiveBenchmarkData, bh: Blackhole): Unit =
    bh.consume(Adler32.combine(state.adler1, state.adler2, state.halfLen.toLong))

  // ── 5. GZIPHeader metadata operations ──

  @Benchmark
  def gzipHeaderCreate(bh: Blackhole): Unit = {
    val h = new GZIPHeader()
    h.setName("test-file.txt")
    h.setComment("created by benchmark")
    h.setModifiedTime(1700000000L)
    h.setOS(GZIPHeader.OS_UNIX)
    bh.consume(h)
  }

  @Benchmark
  def gzipHeaderReadFields(state: ComprehensiveBenchmarkData, bh: Blackhole): Unit = {
    val h = state.prebuiltHeader
    bh.consume(h.getName)
    bh.consume(h.getComment)
    bh.consume(h.getModifiedTime)
    bh.consume(h.getOS)
    bh.consume(h.getCRC)
  }

  @Benchmark
  def gzipHeaderClone(state: ComprehensiveBenchmarkData, bh: Blackhole): Unit =
    bh.consume(state.prebuiltHeader.clone())

  @Benchmark
  def gzipHeaderDetectOS(bh: Blackhole): Unit =
    bh.consume(GZIPHeader.detectOS())

  @Benchmark
  def gzipHeaderSetOSAuto(bh: Blackhole): Unit = {
    val h = new GZIPHeader()
    h.setOSAuto()
    bh.consume(h.getOS)
  }

  // ── 6. Deflater.getDictionary() / Inflater.getDictionary() ──

  @Benchmark
  def deflaterGetDictionary(state: ComprehensiveBenchmarkData, bh: Blackhole): Unit = {
    val deflater = new Deflater
    deflater.init(JZlib.Z_DEFAULT_COMPRESSION)
    deflater.setInput(state.smallData)
    val output   = new Array[Byte](state.smallData.length * 2 + 256)
    deflater.setOutput(output)
    deflater.deflate(JZlib.Z_SYNC_FLUSH)

    val dict    = new Array[Byte](32768)
    val dictLen = Array(0)
    bh.consume(deflater.getDictionary(dict, dictLen))
    bh.consume(dictLen(0))
    deflater.end()
  }

  @Benchmark
  def inflaterGetDictionary(state: ComprehensiveBenchmarkData, bh: Blackhole): Unit = {
    val inflater = new Inflater
    inflater.init()
    inflater.setInput(state.compressedSmall)
    val output   = new Array[Byte](state.smallData.length)
    inflater.setOutput(output)
    var loop     = true
    while (loop) {
      val err = inflater.inflate(JZlib.Z_NO_FLUSH)
      if (err == JZlib.Z_STREAM_END) loop = false
      else if (err != JZlib.Z_OK) throw new RuntimeException(s"Inflate failed: $err")
    }

    val dict    = new Array[Byte](32768)
    val dictLen = Array(0)
    bh.consume(inflater.getDictionary(dict, dictLen))
    bh.consume(dictLen(0))
    inflater.end()
  }

  // ── 7. Deflater with sync flush ──

  @Benchmark
  def deflateWithSyncFlush(state: ComprehensiveBenchmarkData, bh: Blackhole): Unit = {
    val deflater = new Deflater
    deflater.init(JZlib.Z_DEFAULT_COMPRESSION)
    val output   = new Array[Byte](state.smallData.length * 2 + 256)
    deflater.setOutput(output)

    // First chunk with SYNC_FLUSH
    val half = state.smallData.length / 2
    deflater.setInput(java.util.Arrays.copyOfRange(state.smallData, 0, half))
    deflater.deflate(JZlib.Z_SYNC_FLUSH)

    // Second chunk with FINISH
    deflater.setInput(java.util.Arrays.copyOfRange(state.smallData, half, state.smallData.length))
    var done = false
    while (!done) {
      val err = deflater.deflate(JZlib.Z_FINISH)
      if (err == JZlib.Z_STREAM_END) done = true
      else if (err < 0) throw new RuntimeException(s"Deflate failed: $err")
    }
    val len  = deflater.next_out_index
    deflater.end()
    bh.consume(java.util.Arrays.copyOf(output, len))
  }

  @Benchmark
  def deflateWithFullFlush(state: ComprehensiveBenchmarkData, bh: Blackhole): Unit = {
    val deflater = new Deflater
    deflater.init(JZlib.Z_DEFAULT_COMPRESSION)
    val output   = new Array[Byte](state.smallData.length * 2 + 256)
    deflater.setOutput(output)

    val half = state.smallData.length / 2
    deflater.setInput(java.util.Arrays.copyOfRange(state.smallData, 0, half))
    deflater.deflate(JZlib.Z_FULL_FLUSH)

    deflater.setInput(java.util.Arrays.copyOfRange(state.smallData, half, state.smallData.length))
    var done = false
    while (!done) {
      val err = deflater.deflate(JZlib.Z_FINISH)
      if (err == JZlib.Z_STREAM_END) done = true
      else if (err < 0) throw new RuntimeException(s"Deflate failed: $err")
    }
    val len  = deflater.next_out_index
    deflater.end()
    bh.consume(java.util.Arrays.copyOf(output, len))
  }

  // ── 8. Inflater syncPoint ──

  @Benchmark
  def inflaterSyncPoint(state: ComprehensiveBenchmarkData, bh: Blackhole): Unit = {
    val inflater = new Inflater
    inflater.init()
    inflater.setInput(state.compressedSmall)
    val output   = new Array[Byte](state.smallData.length)
    inflater.setOutput(output)
    inflater.inflate(JZlib.Z_NO_FLUSH)
    bh.consume(inflater.syncPoint())
    inflater.end()
  }

  // ── 9. Single-byte checksum updates ──

  @Benchmark
  def crc32SingleByteUpdate(bh: Blackhole): Unit = {
    val crc = new CRC32()
    var i   = 0
    while (i < 1024) {
      crc.update(i & 0xff)
      i += 1
    }
    bh.consume(crc.getValue)
  }

  @Benchmark
  def adler32SingleByteUpdate(bh: Blackhole): Unit = {
    val adler = new Adler32()
    var i     = 0
    while (i < 1024) {
      adler.update(i & 0xff)
      i += 1
    }
    bh.consume(adler.getValue)
  }

  // ── 10. Deflater/Inflater toString() ──

  @Benchmark
  def deflaterToString(state: ComprehensiveBenchmarkData, bh: Blackhole): Unit = {
    val deflater = Deflater()
    deflater.setInput(state.smallData)
    val output   = new Array[Byte](state.smallData.length * 2 + 256)
    deflater.setOutput(output)
    bh.consume(deflater.toString)
    deflater.end()
  }

  @Benchmark
  def inflaterToString(state: ComprehensiveBenchmarkData, bh: Blackhole): Unit = {
    val inflater = Inflater()
    inflater.setInput(state.compressedSmall)
    val output   = new Array[Byte](state.smallData.length)
    inflater.setOutput(output)
    bh.consume(inflater.toString)
    inflater.end()
  }

  // ── 11. JZlib.getErrorDescription() (all codes) ──

  @Benchmark
  def getErrorDescriptionAllCodes(bh: Blackhole): Unit = {
    bh.consume(JZlib.getErrorDescription(JZlib.Z_OK))
    bh.consume(JZlib.getErrorDescription(JZlib.Z_STREAM_END))
    bh.consume(JZlib.getErrorDescription(JZlib.Z_NEED_DICT))
    bh.consume(JZlib.getErrorDescription(JZlib.Z_ERRNO))
    bh.consume(JZlib.getErrorDescription(JZlib.Z_STREAM_ERROR))
    bh.consume(JZlib.getErrorDescription(JZlib.Z_DATA_ERROR))
    bh.consume(JZlib.getErrorDescription(JZlib.Z_MEM_ERROR))
    bh.consume(JZlib.getErrorDescription(JZlib.Z_BUF_ERROR))
    bh.consume(JZlib.getErrorDescription(JZlib.Z_VERSION_ERROR))
  }

  // ── 12. Deflater/Inflater companion object factory methods ──

  @Benchmark
  def deflaterFactoryDefault(bh: Blackhole): Unit = {
    val d = Deflater()
    bh.consume(d)
    d.end()
  }

  @Benchmark
  def deflaterFactoryWithLevel(bh: Blackhole): Unit = {
    val d = Deflater(JZlib.Z_BEST_SPEED)
    bh.consume(d)
    d.end()
  }

  @Benchmark
  def deflaterFactoryWithWrapper(bh: Blackhole): Unit = {
    val d = Deflater(JZlib.Z_DEFAULT_COMPRESSION, JZlib.W_GZIP)
    bh.consume(d)
    d.end()
  }

  @Benchmark
  def deflaterFactoryGzip(bh: Blackhole): Unit = {
    val d = Deflater.gzip()
    bh.consume(d)
    d.end()
  }

  @Benchmark
  def inflaterFactoryDefault(bh: Blackhole): Unit = {
    val i = Inflater()
    bh.consume(i)
    i.end()
  }

  @Benchmark
  def inflaterFactoryGzip(bh: Blackhole): Unit = {
    val i = Inflater.gzip()
    bh.consume(i)
    i.end()
  }

  @Benchmark
  def inflaterFactoryAuto(bh: Blackhole): Unit = {
    val i = Inflater.auto()
    bh.consume(i)
    i.end()
  }

  @Benchmark
  def inflaterFactoryWithWrapper(bh: Blackhole): Unit = {
    val i = Inflater(JZlib.W_NONE)
    bh.consume(i)
    i.end()
  }

  // ── 13. AutoCloseable usage pattern ──

  @Benchmark
  def deflaterAutoCloseable(state: ComprehensiveBenchmarkData, bh: Blackhole): Unit = {
    val result = scala.util.Using(Deflater(JZlib.Z_DEFAULT_COMPRESSION)) { deflater =>
      deflater.setInput(state.smallData)
      val output = new Array[Byte](state.smallData.length * 2 + 256)
      deflater.setOutput(output)
      var done   = false
      while (!done) {
        val err = deflater.deflate(JZlib.Z_FINISH)
        if (err == JZlib.Z_STREAM_END) done = true
        else if (err < 0) throw new RuntimeException(s"Deflate failed: $err")
      }
      java.util.Arrays.copyOf(output, deflater.next_out_index)
    }
    bh.consume(result.get)
  }

  @Benchmark
  def inflaterAutoCloseable(state: ComprehensiveBenchmarkData, bh: Blackhole): Unit = {
    val result = scala.util.Using(Inflater()) { inflater =>
      inflater.setInput(state.compressedSmall)
      val output = new Array[Byte](state.smallData.length)
      inflater.setOutput(output)
      var loop   = true
      while (loop) {
        val err = inflater.inflate(JZlib.Z_NO_FLUSH)
        if (err == JZlib.Z_STREAM_END) loop = false
        else if (err != JZlib.Z_OK) throw new RuntimeException(s"Inflate failed: $err")
      }
      output
    }
    bh.consume(result.get)
  }

  // ── Additional coverage: compressBound with level, deflateBound (instance) ──

  @Benchmark
  def compressBoundWithLevelZero(bh: Blackhole): Unit =
    bh.consume(JZlib.compressBound(65536L, JZlib.Z_NO_COMPRESSION))

  @Benchmark
  def compressBoundWithLevelDefault(bh: Blackhole): Unit =
    bh.consume(JZlib.compressBound(65536L, JZlib.Z_DEFAULT_COMPRESSION))

  @Benchmark
  def deflaterInstanceBound(bh: Blackhole): Unit = {
    val d = Deflater(JZlib.Z_DEFAULT_COMPRESSION)
    bh.consume(d.deflateBound(65536L))
    d.end()
  }

  // ── Additional coverage: Deflater.copy ──

  @Benchmark
  def deflaterCopy(state: ComprehensiveBenchmarkData, bh: Blackhole): Unit = {
    val src    = new Deflater
    src.init(JZlib.Z_DEFAULT_COMPRESSION)
    src.setInput(state.smallData)
    val output = new Array[Byte](state.smallData.length * 2 + 256)
    src.setOutput(output)
    src.deflate(JZlib.Z_SYNC_FLUSH)

    val dst = new Deflater
    bh.consume(dst.copy(src))
    src.end()
    dst.end()
  }

  // ── Additional coverage: Deflater.params (strategy change) ──

  @Benchmark
  def deflaterParams(state: ComprehensiveBenchmarkData, bh: Blackhole): Unit = {
    val deflater = new Deflater
    deflater.init(JZlib.Z_DEFAULT_COMPRESSION)
    deflater.setInput(state.smallData)
    val output   = new Array[Byte](state.smallData.length * 2 + 256)
    deflater.setOutput(output)
    deflater.deflate(JZlib.Z_SYNC_FLUSH)
    bh.consume(deflater.params(JZlib.Z_BEST_SPEED, JZlib.Z_FILTERED))
    deflater.end()
  }

  // ── Additional coverage: CRC32/Adler32 reset ──

  @Benchmark
  def crc32Reset(bh: Blackhole): Unit = {
    val crc = new CRC32()
    crc.update(0x42)
    crc.reset()
    bh.consume(crc.getValue)
  }

  @Benchmark
  def crc32ResetWithValue(bh: Blackhole): Unit = {
    val crc = new CRC32()
    crc.reset(0xdeadbeefL)
    bh.consume(crc.getValue)
  }

  @Benchmark
  def adler32Reset(bh: Blackhole): Unit = {
    val adler = new Adler32()
    adler.update(0x42)
    adler.reset()
    bh.consume(adler.getValue)
  }

  @Benchmark
  def adler32ResetWithValue(bh: Blackhole): Unit = {
    val adler = new Adler32()
    adler.reset(0x00010001L)
    bh.consume(adler.getValue)
  }

  // ── Additional coverage: CRC32.getCRC32Table ──

  @Benchmark
  def crc32GetTable(bh: Blackhole): Unit =
    bh.consume(CRC32.getCRC32Table)

  // ── Additional coverage: JZlib.adler32_combine / JZlib.crc32_combine ──

  @Benchmark
  def jzlibAdler32Combine(state: ComprehensiveBenchmarkData, bh: Blackhole): Unit =
    bh.consume(JZlib.adler32_combine(state.adler1, state.adler2, state.halfLen.toLong))

  @Benchmark
  def jzlibCrc32Combine(state: ComprehensiveBenchmarkData, bh: Blackhole): Unit =
    bh.consume(JZlib.crc32_combine(state.crc1, state.crc2, state.halfLen.toLong))

  // ── Additional coverage: JZlib.version() ──

  @Benchmark
  def jzlibVersion(bh: Blackhole): Unit =
    bh.consume(JZlib.version())

  // ── Additional coverage: GZIPHeader.setCRC / getCRC ──

  @Benchmark
  def gzipHeaderSetGetCRC(bh: Blackhole): Unit = {
    val h = new GZIPHeader()
    h.setCRC(0xdeadbeefL)
    bh.consume(h.getCRC)
  }

  // ── Additional coverage: Deflater.finished() ──

  @Benchmark
  def deflaterFinishedCheck(state: ComprehensiveBenchmarkData, bh: Blackhole): Unit = {
    val d      = Deflater()
    d.setInput(state.smallData)
    val output = new Array[Byte](state.smallData.length * 2 + 256)
    d.setOutput(output)
    bh.consume(d.finished())
    d.deflate(JZlib.Z_FINISH)
    bh.consume(d.finished())
    d.end()
  }

  // ── Additional coverage: Inflater.finished() ──

  @Benchmark
  def inflaterFinishedCheck(state: ComprehensiveBenchmarkData, bh: Blackhole): Unit = {
    val i      = Inflater()
    i.setInput(state.compressedSmall)
    val output = new Array[Byte](state.smallData.length)
    i.setOutput(output)
    bh.consume(i.finished())
    var loop   = true
    while (loop) {
      val err = i.inflate(JZlib.Z_NO_FLUSH)
      if (err == JZlib.Z_STREAM_END) loop = false
      else if (err != JZlib.Z_OK) throw new RuntimeException(s"Inflate failed: $err")
    }
    bh.consume(i.finished())
    i.end()
  }

  // ── Additional coverage: GZIPException / ZStreamException ──

  @Benchmark
  def gzipExceptionCreate(bh: Blackhole): Unit =
    bh.consume(new GZIPException("benchmark test"))

  @Benchmark
  def zstreamExceptionCreate(bh: Blackhole): Unit =
    bh.consume(new ZStreamException("benchmark test"))
}
