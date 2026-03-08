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
}

// Kept for source compatibility – the companion object holds the real API.
/** Not instantiable. Use the [[JZlib$ companion object]] for all constants and utilities. */
final class JZlib private ()
