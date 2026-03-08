package com.jcraft.jzlib

import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole
import java.util.concurrent.TimeUnit

// ─── Shared benchmark state ────────────────────────────────────────────────

@State(Scope.Thread)
class BenchmarkData {

  var smallData: Array[Byte]  = _
  var largeData: Array[Byte]  = _
  var oneMBData: Array[Byte]  = _
  var dictionary: Array[Byte] = _

  // Pre-compressed variants (zlib wrapper, default compression)
  var compressedSmall: Array[Byte] = _
  var compressedLarge: Array[Byte] = _

  // Pre-compressed with GZIP wrapper
  var gzipCompressedSmall: Array[Byte] = _
  var gzipCompressedLarge: Array[Byte] = _

  // Pre-compressed raw (no wrapper)
  var rawCompressedSmall: Array[Byte] = _
  var rawCompressedLarge: Array[Byte] = _

  // Pre-compressed with dictionary
  var dictCompressedSmall: Array[Byte] = _

  // Pre-compressed via GZIPOutputStream (for GZIPInputStream benchmarks)
  var gzipStreamCompressedSmall: Array[Byte] = _
  var gzipStreamCompressedLarge: Array[Byte] = _

  // Pre-compressed via DeflaterOutputStream (for InflaterInputStream benchmarks)
  var streamCompressedSmall: Array[Byte] = _
  var streamCompressedLarge: Array[Byte] = _

  // Pre-compressed via ZOutputStream (for ZInputStream benchmarks)
  var zStreamCompressedSmall: Array[Byte] = _

  @Setup(Level.Trial)
  def setup(): Unit = {
    // ~1 KB of repetitive text
    smallData = ("Hello, World! " * 73).getBytes("UTF-8")

    // 64 KB of mixed data (random + patterns for realistic compressibility)
    largeData = new Array[Byte](65536)
    val rng     = new java.util.Random(42)
    rng.nextBytes(largeData)
    val pattern = "PATTERN_DATA_HERE".getBytes
    for (i <- 0 until largeData.length by 128) {
      System.arraycopy(
        pattern,
        0,
        largeData,
        i,
        Math.min(pattern.length, largeData.length - i),
      )
    }

    // 1 MB of mixed data for checksum benchmarks
    oneMBData = new Array[Byte](1024 * 1024)
    val rng2 = new java.util.Random(123)
    rng2.nextBytes(oneMBData)
    val pat2 = "REPEATING_BENCHMARK_PATTERN_FOR_REALISTIC_DATA".getBytes
    for (i <- 0 until oneMBData.length by 256) {
      System.arraycopy(
        pat2,
        0,
        oneMBData,
        i,
        Math.min(pat2.length, oneMBData.length - i),
      )
    }

    // Preset dictionary
    dictionary = "Hello World Pattern Data Benchmark Test Compress".getBytes("UTF-8")

    // Zlib-wrapped compressed data
    compressedSmall = compressZlib(smallData, JZlib.Z_DEFAULT_COMPRESSION)
    compressedLarge = compressZlib(largeData, JZlib.Z_DEFAULT_COMPRESSION)

    // GZIP-wrapped compressed data
    gzipCompressedSmall = compressWithWrapper(smallData, JZlib.W_GZIP)
    gzipCompressedLarge = compressWithWrapper(largeData, JZlib.W_GZIP)

    // Raw compressed data (no wrapper)
    rawCompressedSmall = compressWithWrapper(smallData, JZlib.W_NONE)
    rawCompressedLarge = compressWithWrapper(largeData, JZlib.W_NONE)

    // Dictionary-compressed data
    dictCompressedSmall = compressWithDictionary(smallData, dictionary)

    // Stream-compressed data for InflaterInputStream / GZIPInputStream benchmarks
    streamCompressedSmall = compressWithDeflaterOutputStream(smallData)
    streamCompressedLarge = compressWithDeflaterOutputStream(largeData)
    gzipStreamCompressedSmall = compressWithGzipOutputStream(smallData)
    gzipStreamCompressedLarge = compressWithGzipOutputStream(largeData)

    // ZOutputStream-compressed data
    zStreamCompressedSmall = compressWithZOutputStream(smallData)
  }

  private def compressZlib(data: Array[Byte], level: Int): Array[Byte] = {
    val deflater = new Deflater
    deflater.init(level)
    deflater.setInput(data)
    val output   = new Array[Byte](data.length * 2 + 256)
    deflater.setOutput(output)
    var finished = false
    while (!finished) {
      val err = deflater.deflate(JZlib.Z_FINISH)
      if (err == JZlib.Z_STREAM_END) finished = true
      else if (err < 0) throw new RuntimeException(s"Deflate failed: $err")
    }
    val len      = deflater.next_out_index
    deflater.end()
    java.util.Arrays.copyOf(output, len)
  }

  private def compressWithWrapper(
    data: Array[Byte],
    wrapper: JZlib.WrapperType,
  ): Array[Byte] = {
    val deflater = new Deflater
    deflater.init(JZlib.Z_DEFAULT_COMPRESSION, JZlib.MAX_WBITS, 8, wrapper)
    deflater.setInput(data)
    val output   = new Array[Byte](data.length * 2 + 256)
    deflater.setOutput(output)
    var finished = false
    while (!finished) {
      val err = deflater.deflate(JZlib.Z_FINISH)
      if (err == JZlib.Z_STREAM_END) finished = true
      else if (err < 0) throw new RuntimeException(s"Deflate failed: $err")
    }
    val len      = deflater.next_out_index
    deflater.end()
    java.util.Arrays.copyOf(output, len)
  }

  private def compressWithDictionary(
    data: Array[Byte],
    dict: Array[Byte],
  ): Array[Byte] = {
    val deflater = new Deflater
    deflater.init(JZlib.Z_DEFAULT_COMPRESSION)
    deflater.setDictionary(dict, dict.length)
    deflater.setInput(data)
    val output   = new Array[Byte](data.length * 2 + 256)
    deflater.setOutput(output)
    var finished = false
    while (!finished) {
      val err = deflater.deflate(JZlib.Z_FINISH)
      if (err == JZlib.Z_STREAM_END) finished = true
      else if (err < 0) throw new RuntimeException(s"Deflate failed: $err")
    }
    val len      = deflater.next_out_index
    deflater.end()
    java.util.Arrays.copyOf(output, len)
  }

  private def compressWithDeflaterOutputStream(data: Array[Byte]): Array[Byte] = {
    val baos = new java.io.ByteArrayOutputStream()
    val dos  = new DeflaterOutputStream(baos)
    dos.write(data, 0, data.length)
    dos.close()
    baos.toByteArray
  }

  private def compressWithGzipOutputStream(data: Array[Byte]): Array[Byte] = {
    val baos = new java.io.ByteArrayOutputStream()
    val gos  = new GZIPOutputStream(baos)
    gos.write(data, 0, data.length)
    gos.close()
    baos.toByteArray
  }

  @SuppressWarnings(Array("deprecation"))
  private def compressWithZOutputStream(data: Array[Byte]): Array[Byte] = {
    val baos = new java.io.ByteArrayOutputStream()
    val zos  = new ZOutputStream(baos, JZlib.Z_DEFAULT_COMPRESSION)
    zos.write(data, 0, data.length)
    zos.close()
    baos.toByteArray
  }
}

// ─── A. Core Deflate/Inflate Benchmarks ────────────────────────────────────

@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
class DeflateInflateBenchmark {

  private def deflate(
    data: Array[Byte],
    level: Int,
    wrapper: JZlib.WrapperType = JZlib.W_ZLIB,
  ): Array[Byte] = {
    val deflater = new Deflater
    deflater.init(level, JZlib.MAX_WBITS, 8, wrapper)
    deflater.setInput(data)
    val output   = new Array[Byte](data.length * 2 + 256)
    deflater.setOutput(output)
    var finished = false
    while (!finished) {
      val err = deflater.deflate(JZlib.Z_FINISH)
      if (err == JZlib.Z_STREAM_END) finished = true
      else if (err < 0) throw new RuntimeException(s"Deflate failed: $err")
    }
    val len      = deflater.next_out_index
    deflater.end()
    java.util.Arrays.copyOf(output, len)
  }

  private def inflate(
    compressed: Array[Byte],
    expectedLen: Int,
    wrapper: JZlib.WrapperType = JZlib.W_ZLIB,
  ): Array[Byte] = {
    val inflater = new Inflater
    inflater.init(JZlib.MAX_WBITS, wrapper)
    inflater.setInput(compressed)
    val output   = new Array[Byte](expectedLen)
    inflater.setOutput(output)
    var loop     = true
    while (loop) {
      val err = inflater.inflate(JZlib.Z_NO_FLUSH)
      if (err == JZlib.Z_STREAM_END) loop = false
      else if (err != JZlib.Z_OK) throw new RuntimeException(s"Inflate failed: $err")
    }
    inflater.end()
    output
  }

  // ── Default compression (keep existing) ──

  @Benchmark
  def deflateSmall(state: BenchmarkData, bh: Blackhole): Unit =
    bh.consume(deflate(state.smallData, JZlib.Z_DEFAULT_COMPRESSION))

  @Benchmark
  def deflateLarge(state: BenchmarkData, bh: Blackhole): Unit =
    bh.consume(deflate(state.largeData, JZlib.Z_DEFAULT_COMPRESSION))

  @Benchmark
  def inflateSmall(state: BenchmarkData, bh: Blackhole): Unit =
    bh.consume(inflate(state.compressedSmall, state.smallData.length))

  @Benchmark
  def inflateLarge(state: BenchmarkData, bh: Blackhole): Unit =
    bh.consume(inflate(state.compressedLarge, state.largeData.length))

  @Benchmark
  def roundTripSmall(state: BenchmarkData, bh: Blackhole): Unit = {
    val compressed = deflate(state.smallData, JZlib.Z_DEFAULT_COMPRESSION)
    bh.consume(inflate(compressed, state.smallData.length))
  }

  // ── Compression levels ──

  @Benchmark
  def deflateWithBestSpeed(state: BenchmarkData, bh: Blackhole): Unit =
    bh.consume(deflate(state.largeData, JZlib.Z_BEST_SPEED))

  @Benchmark
  def deflateWithBestCompression(state: BenchmarkData, bh: Blackhole): Unit =
    bh.consume(deflate(state.largeData, JZlib.Z_BEST_COMPRESSION))

  // ── Preset dictionary ──

  @Benchmark
  def deflateWithDictionary(state: BenchmarkData, bh: Blackhole): Unit = {
    val deflater = new Deflater
    deflater.init(JZlib.Z_DEFAULT_COMPRESSION)
    deflater.setDictionary(state.dictionary, state.dictionary.length)
    deflater.setInput(state.smallData)
    val output   = new Array[Byte](state.smallData.length * 2 + 256)
    deflater.setOutput(output)
    var finished = false
    while (!finished) {
      val err = deflater.deflate(JZlib.Z_FINISH)
      if (err == JZlib.Z_STREAM_END) finished = true
      else if (err < 0) throw new RuntimeException(s"Deflate failed: $err")
    }
    val len      = deflater.next_out_index
    deflater.end()
    bh.consume(java.util.Arrays.copyOf(output, len))
  }

  @Benchmark
  def inflateWithDictionary(state: BenchmarkData, bh: Blackhole): Unit = {
    val inflater = new Inflater
    inflater.init()
    inflater.setInput(state.dictCompressedSmall)
    val output   = new Array[Byte](state.smallData.length)
    inflater.setOutput(output)
    var loop     = true
    while (loop) {
      val err = inflater.inflate(JZlib.Z_NO_FLUSH)
      if (err == JZlib.Z_NEED_DICT) {
        inflater.setDictionary(state.dictionary, state.dictionary.length)
      } else if (err == JZlib.Z_STREAM_END) {
        loop = false
      } else if (err != JZlib.Z_OK) {
        throw new RuntimeException(s"Inflate failed: $err")
      }
    }
    inflater.end()
    bh.consume(output)
  }

  // ── GZIP wrapper ──

  @Benchmark
  def deflateGzip(state: BenchmarkData, bh: Blackhole): Unit =
    bh.consume(deflate(state.largeData, JZlib.Z_DEFAULT_COMPRESSION, JZlib.W_GZIP))

  @Benchmark
  def inflateGzip(state: BenchmarkData, bh: Blackhole): Unit =
    bh.consume(inflate(state.gzipCompressedLarge, state.largeData.length, JZlib.W_GZIP))

  // ── Raw deflate (no wrapper) ──

  @Benchmark
  def deflateRaw(state: BenchmarkData, bh: Blackhole): Unit =
    bh.consume(deflate(state.largeData, JZlib.Z_DEFAULT_COMPRESSION, JZlib.W_NONE))

  @Benchmark
  def inflateRaw(state: BenchmarkData, bh: Blackhole): Unit =
    bh.consume(inflate(state.rawCompressedLarge, state.largeData.length, JZlib.W_NONE))

  // ── Flush modes ──

  @Benchmark
  def deflateWithFlush(state: BenchmarkData, bh: Blackhole): Unit = {
    val deflater = new Deflater
    deflater.init(JZlib.Z_DEFAULT_COMPRESSION)
    val output   = new Array[Byte](state.largeData.length * 2 + 256)
    deflater.setOutput(output)

    // Write data in chunks with Z_SYNC_FLUSH between them
    val chunkSize = state.largeData.length / 4
    for (i <- 0 until 4) {
      val off   = i * chunkSize
      val len   = if (i == 3) state.largeData.length - off else chunkSize
      val chunk = java.util.Arrays.copyOfRange(state.largeData, off, off + len)
      deflater.setInput(chunk)
      val flush = if (i < 3) JZlib.Z_SYNC_FLUSH else JZlib.Z_FINISH
      var done  = false
      while (!done) {
        val err = deflater.deflate(flush)
        if (err == JZlib.Z_STREAM_END) done = true
        else if (err == JZlib.Z_OK) done = true
        else if (err < 0) throw new RuntimeException(s"Deflate flush failed: $err")
      }
    }
    val len = deflater.next_out_index
    deflater.end()
    bh.consume(java.util.Arrays.copyOf(output, len))
  }
}

// ─── B. Stream Wrapper Benchmarks ──────────────────────────────────────────

@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
class StreamBenchmark {

  // ── DeflaterOutputStream ──

  @Benchmark
  def deflaterOutputStreamSmall(state: BenchmarkData, bh: Blackhole): Unit = {
    val baos = new java.io.ByteArrayOutputStream()
    val dos  = new DeflaterOutputStream(baos)
    dos.write(state.smallData, 0, state.smallData.length)
    dos.close()
    bh.consume(baos.toByteArray)
  }

  @Benchmark
  def deflaterOutputStreamLarge(state: BenchmarkData, bh: Blackhole): Unit = {
    val baos = new java.io.ByteArrayOutputStream()
    val dos  = new DeflaterOutputStream(baos)
    dos.write(state.largeData, 0, state.largeData.length)
    dos.close()
    bh.consume(baos.toByteArray)
  }

  // ── InflaterInputStream ──

  @Benchmark
  def inflaterInputStreamSmall(state: BenchmarkData, bh: Blackhole): Unit = {
    val bais   = new java.io.ByteArrayInputStream(state.streamCompressedSmall)
    val iis    = new InflaterInputStream(bais)
    val output = readAllBytes(iis)
    iis.close()
    bh.consume(output)
  }

  @Benchmark
  def inflaterInputStreamLarge(state: BenchmarkData, bh: Blackhole): Unit = {
    val bais   = new java.io.ByteArrayInputStream(state.streamCompressedLarge)
    val iis    = new InflaterInputStream(bais)
    val output = readAllBytes(iis)
    iis.close()
    bh.consume(output)
  }

  // ── GZIPOutputStream ──

  @Benchmark
  def gzipOutputStreamSmall(state: BenchmarkData, bh: Blackhole): Unit = {
    val baos = new java.io.ByteArrayOutputStream()
    val gos  = new GZIPOutputStream(baos)
    gos.write(state.smallData, 0, state.smallData.length)
    gos.close()
    bh.consume(baos.toByteArray)
  }

  @Benchmark
  def gzipOutputStreamLarge(state: BenchmarkData, bh: Blackhole): Unit = {
    val baos = new java.io.ByteArrayOutputStream()
    val gos  = new GZIPOutputStream(baos)
    gos.write(state.largeData, 0, state.largeData.length)
    gos.close()
    bh.consume(baos.toByteArray)
  }

  // ── GZIPInputStream ──

  @Benchmark
  def gzipInputStreamSmall(state: BenchmarkData, bh: Blackhole): Unit = {
    val bais   = new java.io.ByteArrayInputStream(state.gzipStreamCompressedSmall)
    val gis    = new GZIPInputStream(bais)
    val output = readAllBytes(gis)
    gis.close()
    bh.consume(output)
  }

  @Benchmark
  def gzipInputStreamLarge(state: BenchmarkData, bh: Blackhole): Unit = {
    val bais   = new java.io.ByteArrayInputStream(state.gzipStreamCompressedLarge)
    val gis    = new GZIPInputStream(bais)
    val output = readAllBytes(gis)
    gis.close()
    bh.consume(output)
  }

  // ── Legacy ZOutputStream / ZInputStream ──

  @SuppressWarnings(Array("deprecation"))
  @Benchmark
  def legacyZOutputStreamCompress(state: BenchmarkData, bh: Blackhole): Unit = {
    val baos = new java.io.ByteArrayOutputStream()
    val zos  = new ZOutputStream(baos, JZlib.Z_DEFAULT_COMPRESSION)
    zos.write(state.smallData, 0, state.smallData.length)
    zos.close()
    bh.consume(baos.toByteArray)
  }

  @SuppressWarnings(Array("deprecation"))
  @Benchmark
  def legacyZInputStreamDecompress(state: BenchmarkData, bh: Blackhole): Unit = {
    val bais   = new java.io.ByteArrayInputStream(state.zStreamCompressedSmall)
    val zis    = new ZInputStream(bais)
    val output = readAllBytes(zis)
    zis.close()
    bh.consume(output)
  }

  private def readAllBytes(is: java.io.InputStream): Array[Byte] = {
    val baos = new java.io.ByteArrayOutputStream()
    val buf  = new Array[Byte](4096)
    var n    = is.read(buf)
    while (n != -1) {
      baos.write(buf, 0, n)
      n = is.read(buf)
    }
    baos.toByteArray
  }
}

// ─── C. Checksum Benchmarks ────────────────────────────────────────────────

@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
class ChecksumBenchmark {

  // ── Adler32 ──

  @Benchmark
  def adler32_1KB(state: BenchmarkData, bh: Blackhole): Unit = {
    val adler = new Adler32()
    adler.update(state.smallData, 0, state.smallData.length)
    bh.consume(adler.getValue)
  }

  @Benchmark
  def adler32_1MB(state: BenchmarkData, bh: Blackhole): Unit = {
    val adler = new Adler32()
    adler.update(state.oneMBData, 0, state.oneMBData.length)
    bh.consume(adler.getValue)
  }

  @Benchmark
  def adler32Combine(state: BenchmarkData, bh: Blackhole): Unit = {
    val half = state.oneMBData.length / 2
    val a1   = {
      val a = new Adler32(); a.update(state.oneMBData, 0, half); a.getValue
    }
    val a2   = {
      val a = new Adler32(); a.update(state.oneMBData, half, half); a.getValue
    }
    bh.consume(Adler32.combine(a1, a2, half.toLong))
  }

  @Benchmark
  def adler32Copy(state: BenchmarkData, bh: Blackhole): Unit = {
    val adler = new Adler32()
    adler.update(state.smallData, 0, state.smallData.length)
    val copy  = adler.copy()
    bh.consume(copy.getValue)
  }

  // ── CRC32 ──

  @Benchmark
  def crc32_1KB(state: BenchmarkData, bh: Blackhole): Unit = {
    val crc = new CRC32()
    crc.update(state.smallData, 0, state.smallData.length)
    bh.consume(crc.getValue)
  }

  @Benchmark
  def crc32_1MB(state: BenchmarkData, bh: Blackhole): Unit = {
    val crc = new CRC32()
    crc.update(state.oneMBData, 0, state.oneMBData.length)
    bh.consume(crc.getValue)
  }

  @Benchmark
  def crc32Combine(state: BenchmarkData, bh: Blackhole): Unit = {
    val half = state.oneMBData.length / 2
    val c1   = {
      val c = new CRC32(); c.update(state.oneMBData, 0, half); c.getValue
    }
    val c2   = {
      val c = new CRC32(); c.update(state.oneMBData, half, half); c.getValue
    }
    bh.consume(CRC32.combine(c1, c2, half.toLong))
  }

  @Benchmark
  def crc32Copy(state: BenchmarkData, bh: Blackhole): Unit = {
    val crc  = new CRC32()
    crc.update(state.smallData, 0, state.smallData.length)
    val copy = crc.copy()
    bh.consume(copy.getValue)
  }
}
