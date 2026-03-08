package com.jcraft.jzlib

import org.openjdk.jmh.annotations._
import java.util.concurrent.TimeUnit

@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
class ZlibBenchmark {

  private var smallData: Array[Byte]       = _
  private var largeData: Array[Byte]       = _
  private var compressedSmall: Array[Byte] = _
  private var compressedLarge: Array[Byte] = _

  @Setup
  def setup(): Unit = {
    smallData = ("Hello, World! " * 73).getBytes("UTF-8")

    largeData = new Array[Byte](65536)
    val rng     = new java.util.Random(42)
    rng.nextBytes(largeData)
    val pattern = "PATTERN_DATA_HERE".getBytes
    for (i <- 0 until largeData.length by 128) {
      System.arraycopy(pattern, 0, largeData, i, Math.min(pattern.length, largeData.length - i))
    }

    compressedSmall = compress(smallData)
    compressedLarge = compress(largeData)
  }

  private def compress(data: Array[Byte]): Array[Byte] = {
    val deflater = new Deflater
    deflater.init(JZlib.Z_DEFAULT_COMPRESSION)

    deflater.setInput(data)
    val output = new Array[Byte](data.length * 2 + 256)
    deflater.setOutput(output)

    var finished      = false
    while (!finished) {
      val err = deflater.deflate(JZlib.Z_FINISH)
      if (err == JZlib.Z_STREAM_END) finished = true
      else if (err < 0) throw new RuntimeException(s"Deflate failed: $err")
    }
    val compressedLen = deflater.next_out_index
    deflater.end()
    java.util.Arrays.copyOf(output, compressedLen)
  }

  @Benchmark
  def deflateSmall(): Array[Byte] = compress(smallData)

  @Benchmark
  def deflateLarge(): Array[Byte] = compress(largeData)

  @Benchmark
  def inflateSmall(): Array[Byte] = decompress(compressedSmall, smallData.length)

  @Benchmark
  def inflateLarge(): Array[Byte] = decompress(compressedLarge, largeData.length)

  private def decompress(compressed: Array[Byte], expectedLen: Int): Array[Byte] = {
    val inflater = new Inflater
    inflater.init()
    inflater.setInput(compressed)
    val output   = new Array[Byte](expectedLen)
    inflater.setOutput(output)

    var loop = true
    while (loop) {
      val err = inflater.inflate(JZlib.Z_NO_FLUSH)
      if (err == JZlib.Z_STREAM_END) loop = false
      else if (err != JZlib.Z_OK) throw new RuntimeException(s"Inflate failed: $err")
    }
    inflater.end()
    output
  }

  @Benchmark
  def adler32_1KB(): Long = {
    val adler = new Adler32()
    adler.update(smallData, 0, smallData.length)
    adler.getValue
  }

  @Benchmark
  def crc32_1KB(): Long = {
    val crc = new CRC32()
    crc.update(smallData, 0, smallData.length)
    crc.getValue
  }

  @Benchmark
  def roundTripSmall(): Array[Byte] = {
    val compressed = compress(smallData)
    decompress(compressed, smallData.length)
  }
}
