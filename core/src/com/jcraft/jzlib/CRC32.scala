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
 * CRC-32 checksum as defined in RFC 1952, used by the GZIP wrapper format.
 *
 * Produces the same results as `java.util.zip.CRC32` but works on all Scala platforms (JVM, Scala.js, Scala Native,
 * WASM).
 *
 * The update method uses a slicing-by-4 algorithm that processes four bytes per loop iteration for improved throughput
 * on bulk data.
 *
 * Instances are not thread-safe; each thread should use its own instance.
 *
 * To merge checksums computed over separate data segments, use `CRC32.combine`.
 */
final class CRC32 extends Checksum {

  private var v: Int = 0

  /** Updates the checksum with the specified bytes from `buf`. */
  def update(buf: Array[Byte], index: Int, len: Int): Unit = {
    var c   = ~v
    var i   = index
    var rem = len

    // slicing-by-4: process four bytes per iteration
    while (rem >= 4) {
      val word = (buf(i) & 0xff) |
        ((buf(i + 1) & 0xff) << 8) |
        ((buf(i + 2) & 0xff) << 16) |
        ((buf(i + 3) & 0xff) << 24)
      val w    = c ^ word
      c = CRC32.crc_slice(3)(w & 0xff) ^
        CRC32.crc_slice(2)((w >>> 8) & 0xff) ^
        CRC32.crc_slice(1)((w >>> 16) & 0xff) ^
        CRC32.crc_slice(0)((w >>> 24) & 0xff)
      i += 4
      rem -= 4
    }

    // process remaining bytes one at a time
    while (rem > 0) {
      rem -= 1
      c = CRC32.crc_table((c ^ buf(i)) & 0xff) ^ (c >>> 8)
      i += 1
    }

    v = ~c
  }

  /** Resets the CRC-32 checksum to 0. */
  def reset(): Unit = v = 0

  /** Resets the CRC-32 checksum to a specific value. */
  def reset(vv: Long): Unit = v = (vv & 0xffffffffL).toInt

  /** Returns the current CRC-32 checksum value. */
  def getValue: Long = v & 0xffffffffL

  /** Creates an independent copy of this checksum. */
  def copy(): CRC32 = {
    val foo = new CRC32
    foo.v = v
    foo
  }
}

/**
 * Companion providing the CRC-32 lookup table, `combine` utility, and the precomputed-operator API (`combineGen` /
 * `combineOp`).
 */
object CRC32 {

  /** Standard CRC-32 reflected polynomial. */
  private final val POLY: Int = 0xedb88320

  /*
   * The following logic has come from RFC1952.
   */
  val crc_table: Array[Int] = {
    val tbl = new Array[Int](256)
    var n   = 0
    while (n < 256) {
      var c = n
      var k = 8
      while (k > 0) {
        k -= 1
        if ((c & 1) != 0) c = POLY ^ (c >>> 1)
        else c = c >>> 1
      }
      tbl(n) = c
      n += 1
    }
    tbl
  }

  /**
   * Four 256-entry tables for slicing-by-4 CRC computation.
   *
   * `crc_slice(0)` is the standard byte-at-a-time table (identical to `crc_table`). `crc_slice(k)(n)` is the CRC of
   * byte `n` followed by `k` zero bytes.
   */
  private[jzlib] val crc_slice: Array[Array[Int]] = {
    val tables = Array.ofDim[Int](4, 256)
    System.arraycopy(crc_table, 0, tables(0), 0, 256)
    var k      = 1
    while (k < 4) {
      var n = 0
      while (n < 256) {
        var c = tables(k - 1)(n)
        c = crc_table(c & 0xff) ^ (c >>> 8)
        tables(k)(n) = c
        n += 1
      }
      k += 1
    }
    tables
  }

  /**
   * Precomputed table of x^(2^i) mod p(x) for i in 0..31.
   *
   * Used by `x2nmodp` and `combineGen` to avoid repeated squaring from scratch.
   */
  private val x2n_table: Array[Int] = {
    val tbl = new Array[Int](32)
    var p   = 1 << 30 // x^1
    tbl(0) = p
    var i   = 1
    while (i < 32) {
      p = multmodp(p, p)
      tbl(i) = p
      i += 1
    }
    tbl
  }

  /**
   * Multiplies `a(x)` by `b(x)` modulo the CRC polynomial in GF(2).
   *
   * Both operands use the reflected bit order. Bit 31 represents x^0 (the identity), bit 30 represents x^1, and so on.
   * The method scans `a` from MSB to LSB, matching the zlib C implementation.
   */
  private[jzlib] def multmodp(a: Int, b: Int): Int = {
    var m: Int = 1 << 31 // start at MSB (x^0)
    var p: Int = 0
    var bb     = b
    var done   = false
    while (!done) {
      if ((a & m) != 0) {
        p ^= bb
        if ((a & (m - 1)) == 0) done = true
      }
      if (!done) {
        m >>>= 1
        bb = if ((bb & 1) != 0) (bb >>> 1) ^ POLY else bb >>> 1
      }
    }
    p
  }

  /**
   * Returns x^(n * 2^k) mod p(x) using the precomputed `x2n_table`.
   *
   * For `combine` and `combineGen`, `k = 3` so that the result is x^(8*len2), i.e. the effect of appending len2 zero
   * bytes.
   */
  private[jzlib] def x2nmodp(n: Long, k: Int): Int      = {
    var p: Int = 1 << 31 // x^0 == 1 (the multiplicative identity)
    var nn     = n
    var i      = k
    while (nn != 0) {
      if ((nn & 1) != 0) {
        p = multmodp(x2n_table(i), p)
      }
      nn >>>= 1
      i += 1
    }
    p
  }
  // The following logic has come from zlib.1.2.
  def combine(crc1: Long, crc2: Long, len2: Long): Long = {
    if (len2 < 0)
      throw new IllegalArgumentException(
        s"len2 must be non-negative, got $len2",
      )
    if (len2 == 0) return crc1
    combineOp(combineGen(len2), crc1, crc2)
  }

  /**
   * Pre-computes a reusable combine operator for a fixed second-segment length.
   *
   * The returned `Int` encodes x^(len2 + 3) mod p(x) and can be applied repeatedly via `combineOp` without recomputing.
   *
   * @param len2
   *   byte length of the second segment (must be positive)
   * @return
   *   opaque operator value for use with `combineOp`
   */
  def combineGen(len2: Long): Int = x2nmodp(len2, 3)

  /**
   * Applies a pre-computed combine operator to merge two CRC-32 values.
   *
   * @param op
   *   operator from `combineGen`
   * @param crc1
   *   checksum of the first segment
   * @param crc2
   *   checksum of the second segment
   * @return
   *   combined CRC-32 checksum
   */
  def combineOp(op: Int, crc1: Long, crc2: Long): Long = {
    (multmodp(op, crc1.toInt) ^ crc2.toInt) & 0xffffffffL
  }

  private final val GF2_DIM = 32

  /**
   * Squares a GF(2) matrix in place.
   *
   * Deprecated: retained for backward compatibility. Internally, the library now uses `multmodp` / `x2nmodp` for the
   * combine operation.
   */
  @deprecated("Use combine / combineGen / combineOp instead", "0.1.0")
  def gf2_matrix_square(square: Array[Long], mat: Array[Long]): Unit = {
    var n = 0
    while (n < GF2_DIM) {
      square(n) = gf2_matrix_times(mat, mat(n)); n += 1
    }
  }

  private def gf2_matrix_times(mat: Array[Long], vec: Long): Long = {
    var sum   = 0L
    var v     = vec
    var index = 0
    while (v != 0) {
      if ((v & 1) != 0) sum ^= mat(index)
      v >>= 1; index += 1
    }
    sum
  }

  def getCRC32Table: Array[Int] = {
    val tmp = new Array[Int](crc_table.length)
    System.arraycopy(crc_table, 0, tmp, 0, tmp.length)
    tmp
  }
}
