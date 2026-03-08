/* -*-mode:scala; c-basic-offset:2; -*- */
/*
Copyright (c) 2011 ymnk, JCraft,Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

  1. Redistributions of source code must retain the above copyright notice,
     this list of conditions and the following disclaimer.

  2. Redistributions in binary form must reproduce the above copyright
     notice, this list of conditions and the following disclaimer in
     the documentation and/or other materials provided with the distribution.

  3. The names of the authors may not be used to endorse or promote products
     derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL JCRAFT,
INC. OR ANY CONTRIBUTORS TO THIS SOFTWARE BE LIABLE FOR ANY DIRECT, INDIRECT,
INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
/*
 * This program is based on zlib-1.1.3, so all credit should go authors
 * Jean-loup Gailly(jloup@gzip.org) and Mark Adler(madler@alumni.caltech.edu)
 * and contributors of zlib.
 */

package com.jcraft.jzlib

/**
 * Constants and utility functions for the zlib compression library.
 *
 * This object provides all public constants needed for compression and decompression, including compression levels,
 * flush modes, return codes, and the [[WrapperType]] enum for selecting the output format.
 *
 * ==Compression levels==
 *
 *   - [[Z_NO_COMPRESSION]] (0) — no compression, pass-through
 *   - [[Z_BEST_SPEED]] (1) — fastest compression
 *   - [[Z_BEST_COMPRESSION]] (9) — smallest output
 *   - [[Z_DEFAULT_COMPRESSION]] (-1) — balance of speed and size (level 6)
 *
 * ==Flush modes==
 *
 *   - [[Z_NO_FLUSH]] (0) — normal operation
 *   - [[Z_PARTIAL_FLUSH]] (1) — flush pending output
 *   - [[Z_SYNC_FLUSH]] (2) — flush to byte boundary
 *   - [[Z_FULL_FLUSH]] (3) — flush + reset compression state
 *   - [[Z_FINISH]] (4) — signal end of input; must call until `Z_STREAM_END`
 *
 * ==Return codes==
 *
 *   - [[Z_OK]] (0) — success
 *   - [[Z_STREAM_END]] (1) — end of compressed data reached
 *   - [[Z_NEED_DICT]] (2) — a preset dictionary is required
 *   - [[Z_ERRNO]] (-1) — file error
 *   - [[Z_STREAM_ERROR]] (-2) — stream state inconsistency
 *   - [[Z_DATA_ERROR]] (-3) — invalid or corrupted data
 *   - [[Z_MEM_ERROR]] (-4) — insufficient memory
 *   - [[Z_BUF_ERROR]] (-5) — no progress possible
 *   - [[Z_VERSION_ERROR]] (-6) — version mismatch
 *
 * @see
 *   [[Deflater]] for compression
 * @see
 *   [[Inflater]] for decompression
 */
object JZlib {

  /** Returns the library version string. */
  private val _version  = "1.1.0"
  def version(): String = _version

  /** Maximum window size in bits (32K LZ77 window). */
  final val MAX_WBITS = 15 // 32K LZ77 window
  /** Default window size (same as [[MAX_WBITS]]). */
  final val DEF_WBITS = MAX_WBITS

  /**
   * Selects the wrapper format for compressed data.
   *
   *   - [[W_NONE]] — raw deflate, no header or trailer
   *   - [[W_ZLIB]] — zlib wrapper (RFC 1950): 2-byte header + Adler-32 trailer
   *   - [[W_GZIP]] — GZIP wrapper (RFC 1952): GZIP header + CRC-32 trailer
   *   - [[W_ANY]] — auto-detect ZLIB or GZIP on inflate (invalid for deflate)
   */
  sealed abstract class WrapperType
  object WrapperType {
    case object NONE extends WrapperType
    case object ZLIB extends WrapperType
    case object GZIP extends WrapperType
    case object ANY  extends WrapperType
  }

  /** Raw deflate — no header or trailer (RFC 1951). */
  val W_NONE: WrapperType = WrapperType.NONE

  /** Zlib wrapping — 2-byte header + Adler-32 trailer (RFC 1950). */
  val W_ZLIB: WrapperType = WrapperType.ZLIB

  /** GZIP wrapping — GZIP header + CRC-32 trailer (RFC 1952). */
  val W_GZIP: WrapperType = WrapperType.GZIP

  /** Auto-detect ZLIB or GZIP on inflate; invalid for deflate. */
  val W_ANY: WrapperType = WrapperType.ANY

  // --- Compression levels ---

  /** No compression (level 0). */
  final val Z_NO_COMPRESSION = 0

  /** Fastest compression (level 1). */
  final val Z_BEST_SPEED = 1

  /** Best compression ratio (level 9). */
  final val Z_BEST_COMPRESSION = 9

  /** Default compression level (-1); equivalent to level 6. */
  final val Z_DEFAULT_COMPRESSION = -1

  // --- Compression strategies ---

  /** Filtered strategy — for data produced by a filter or predictor. */
  final val Z_FILTERED = 1

  /** Huffman-only strategy — no string matching. */
  final val Z_HUFFMAN_ONLY = 2

  /** RLE strategy — fast for data with many repeated bytes. */
  final val Z_RLE = 3

  /** Fixed Huffman tables — fastest encoding, no dynamic tree overhead. */
  final val Z_FIXED = 4

  /** Default strategy — suitable for most data. */
  final val Z_DEFAULT_STRATEGY = 0

  // --- Flush modes ---

  /** Normal operation — allow deflate to decide when to flush. */
  final val Z_NO_FLUSH = 0

  /** Flush pending output (for streaming protocols). */
  final val Z_PARTIAL_FLUSH = 1

  /** Flush to byte boundary; output can be safely split here. */
  final val Z_SYNC_FLUSH = 2

  /** Flush + reset compression state (allows random access). */
  final val Z_FULL_FLUSH = 3

  /** Signal end of input; call until [[Z_STREAM_END]] is returned. */
  final val Z_FINISH = 4

  // --- Return codes ---

  /** Success. */
  final val Z_OK = 0

  /** End of compressed data reached. */
  final val Z_STREAM_END = 1

  /** A preset dictionary is required for decompression. */
  final val Z_NEED_DICT = 2

  /** File error. */
  final val Z_ERRNO = -1

  /** Stream state inconsistency (e.g., uninitialized or corrupt state). */
  final val Z_STREAM_ERROR = -2

  /** Invalid or corrupted compressed data. */
  final val Z_DATA_ERROR = -3

  /** Insufficient memory. */
  final val Z_MEM_ERROR = -4

  /** No progress possible (output buffer full or input exhausted). */
  final val Z_BUF_ERROR = -5

  /** Library version mismatch. */
  final val Z_VERSION_ERROR = -6

  // --- Block types ---

  /** Binary data block. */
  final val Z_BINARY: Byte = 0

  /** ASCII/text data block. */
  final val Z_ASCII: Byte = 1

  /** Unknown data type. */
  final val Z_UNKNOWN: Byte = 2

  /**
   * Combines two Adler-32 checksums into one.
   *
   * @see
   *   [[Adler32]]
   */
  def adler32_combine(adler1: Long, adler2: Long, len2: Long): Long =
    Adler32.combine(adler1, adler2, len2)

  /**
   * Combines two CRC-32 checksums into one.
   *
   * @see
   *   [[CRC32]]
   */
  def crc32_combine(crc1: Long, crc2: Long, len2: Long): Long =
    CRC32.combine(crc1, crc2, len2)

  /**
   * Returns a detailed human-readable description for a zlib return code.
   *
   * @param code
   *   a zlib return code (Z_OK, Z_STREAM_ERROR, etc.)
   * @return
   *   detailed description of the error
   */
  def getErrorDescription(code: Int): String = code match {
    case Z_OK            => "OK: operation completed successfully"
    case Z_STREAM_END    => "Stream end: all input consumed and output produced"
    case Z_NEED_DICT     => "Dictionary required: a preset dictionary is needed for decompression"
    case Z_ERRNO         => "File I/O error"
    case Z_STREAM_ERROR  => "Stream error: stream state is inconsistent (uninitialized or corrupted)"
    case Z_DATA_ERROR    => "Data error: input data is corrupted or not in the expected format"
    case Z_MEM_ERROR     => "Memory error: insufficient memory for the operation"
    case Z_BUF_ERROR     => "Buffer error: no progress possible (output buffer full or input exhausted)"
    case Z_VERSION_ERROR => "Version error: zlib library version mismatch"
    case _               => s"Unknown error code: $code"
  }

  /**
   * Returns a conservative upper bound on the compressed size for the given uncompressed length. This is useful for
   * pre-allocating output buffers when no [[Deflater]] state is available.
   *
   * Since this method has no access to deflater parameters, it returns the worst-case bound that is safe for any
   * combination of compression level, window bits, memory level, and wrapper type. The formula matches modern C zlib's
   * `deflateBound()` when called without a valid stream.
   *
   * For a tighter bound when a [[Deflater]] has been initialized, use [[Deflater.deflateBound]] instead.
   *
   * @param sourceLen
   *   the length of uncompressed data
   * @return
   *   upper bound on compressed size
   */
  def deflateBound(sourceLen: Long): Long = {
    // upper bound for fixed blocks with 9-bit literals and length 255
    // (memLevel == 2, lowest that may not use stored blocks) -- ~13% overhead
    val fixedlen = sourceLen + (sourceLen >> 3) + (sourceLen >> 8) +
      (sourceLen >> 9) + 4

    // upper bound for stored blocks with length 127 (memLevel == 1) -- ~4% overhead
    val storelen = sourceLen + (sourceLen >> 5) + (sourceLen >> 7) +
      (sourceLen >> 11) + 7

    // without stream state, use larger bound plus maximum wrapper (GZIP = 18 bytes)
    (if (fixedlen > storelen) fixedlen else storelen) + 18
  }

  /**
   * Equivalent to [[deflateBound]] — returns an upper bound on compressed size. Matches the C zlib `compressBound()`
   * function.
   */
  def compressBound(sourceLen: Long): Long = deflateBound(sourceLen)

  /**
   * One-shot compression using default settings ([[Z_DEFAULT_COMPRESSION]], [[W_ZLIB]]).
   *
   * @param data
   *   the uncompressed data
   * @return
   *   compressed data as a byte array
   * @throws ZStreamException
   *   if compression fails
   */
  def compress(data: Array[Byte]): Array[Byte] = {
    if (data == null)
      throw new ZStreamException("compress: null input data")
    val deflater = new Deflater()
    deflater.init(Z_DEFAULT_COMPRESSION)

    deflater.setInput(data)
    deflater.setNextInIndex(0)
    deflater.setAvailIn(data.length)

    val boundLong = deflateBound(data.length.toLong)
    if (boundLong > Int.MaxValue)
      throw new ZStreamException(
        s"input too large for compress: ${data.length} bytes (max ~2GB)",
      )
    val bound     = boundLong.toInt
    val output    = new Array[Byte](bound)
    deflater.setOutput(output)
    deflater.setNextOutIndex(0)
    deflater.setAvailOut(output.length)

    val err = deflater.deflate(Z_FINISH)
    if (err != Z_STREAM_END) {
      deflater.end()
      throw new ZStreamException(s"compress failed: ${deflater.getMessage}")
    }

    val compressedLen = output.length - deflater.getAvailOut
    deflater.end()

    val result = new Array[Byte](compressedLen)
    System.arraycopy(output, 0, result, 0, compressedLen)
    result
  }

  /**
   * One-shot decompression.
   *
   * @param data
   *   the compressed data (zlib format)
   * @return
   *   decompressed data as a byte array
   * @throws ZStreamException
   *   if decompression fails
   */
  def uncompress(data: Array[Byte]): Array[Byte] = {
    if (data == null)
      throw new ZStreamException("uncompress: null input data")
    val inflater = new Inflater()
    inflater.init()

    inflater.setInput(data)
    inflater.setNextInIndex(0)
    inflater.setAvailIn(data.length)

    val initSize = math.min(data.length.toLong * 2, Int.MaxValue).toInt
    var output   = new Array[Byte](math.max(initSize, 64))
    var totalOut = 0
    val buf      = new Array[Byte](8192)

    var err = Z_OK
    while (err != Z_STREAM_END) {
      inflater.setOutput(buf)
      inflater.setNextOutIndex(0)
      inflater.setAvailOut(buf.length)

      err = inflater.inflate(Z_NO_FLUSH)
      if (err != Z_OK && err != Z_STREAM_END) {
        inflater.end()
        throw new ZStreamException(s"uncompress failed: ${inflater.getMessage}")
      }

      val produced = buf.length - inflater.getAvailOut
      if (produced > 0) {
        while (totalOut + produced > output.length) {
          val newSize =
            math.min(output.length.toLong * 2, Int.MaxValue).toInt
          if (newSize <= output.length) {
            inflater.end()
            throw new ZStreamException(
              "uncompress: decompressed data exceeds maximum array size",
            )
          }
          val grown   = new Array[Byte](newSize)
          System.arraycopy(output, 0, grown, 0, totalOut)
          output = grown
        }
        System.arraycopy(buf, 0, output, totalOut, produced)
        totalOut += produced
      }
    }

    inflater.end()
    val result = new Array[Byte](totalOut)
    System.arraycopy(output, 0, result, 0, totalOut)
    result
  }

  /**
   * One-shot decompression that also reports how many source bytes were consumed. This is useful when there is trailing
   * data after the compressed stream.
   *
   * @param data
   *   the compressed data (zlib format), possibly followed by trailing bytes
   * @return
   *   an [[UncompressResult]] containing the decompressed data and the number of input bytes consumed
   * @throws ZStreamException
   *   if decompression fails
   */
  def uncompress2(data: Array[Byte]): UncompressResult = {
    if (data == null)
      throw new ZStreamException("uncompress2: null input data")
    val inflater = new Inflater()
    inflater.init()

    inflater.setInput(data)
    inflater.setNextInIndex(0)
    inflater.setAvailIn(data.length)

    val initSize = math.min(data.length.toLong * 2, Int.MaxValue).toInt
    var output   = new Array[Byte](math.max(initSize, 64))
    var totalOut = 0
    val buf      = new Array[Byte](8192)

    var err = Z_OK
    while (err != Z_STREAM_END) {
      inflater.setOutput(buf)
      inflater.setNextOutIndex(0)
      inflater.setAvailOut(buf.length)

      err = inflater.inflate(Z_NO_FLUSH)
      if (err != Z_OK && err != Z_STREAM_END) {
        inflater.end()
        throw new ZStreamException(s"uncompress2 failed: ${inflater.getMessage}")
      }

      val produced = buf.length - inflater.getAvailOut
      if (produced > 0) {
        while (totalOut + produced > output.length) {
          val newSize =
            math.min(output.length.toLong * 2, Int.MaxValue).toInt
          if (newSize <= output.length) {
            inflater.end()
            throw new ZStreamException(
              "uncompress2: decompressed data exceeds maximum array size",
            )
          }
          val grown   = new Array[Byte](newSize)
          System.arraycopy(output, 0, grown, 0, totalOut)
          output = grown
        }
        System.arraycopy(buf, 0, output, totalOut, produced)
        totalOut += produced
      }
    }

    val inputBytesUsed = inflater.getTotalIn.toInt
    inflater.end()
    val result         = new Array[Byte](totalOut)
    System.arraycopy(output, 0, result, 0, totalOut)
    UncompressResult(result, inputBytesUsed)
  }

  /** Result of [[uncompress2]], containing the decompressed data and the number of input bytes consumed. */
  case class UncompressResult(data: Array[Byte], inputBytesUsed: Int)
}

// Kept for source compatibility – the companion object holds the real API.
/** Not instantiable. Use the [[JZlib$ companion object]] for all constants and utilities. */
final class JZlib private ()
