/* -*-mode:scala; c-basic-offset:2; indent-tabs-mode:nil -*- */
/*
Copyright (c) 2000-2011 ymnk, JCraft,Inc. All rights reserved.

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

import JZlib._

/**
 * Low-level stream state for compression and decompression.
 *
 * '''This class is deprecated.''' Use [[Deflater]] for compression and [[Inflater]] for decompression instead.
 * `ZStream` remains as the base class for both `Deflater` and `Inflater`, but its public methods (`deflateInit`,
 * `inflateInit`, etc.) should not be called directly in new code.
 *
 * ==Key fields==
 *
 *   - `next_in` / `next_in_index` / `avail_in` — input buffer, position, and remaining bytes
 *   - `next_out` / `next_out_index` / `avail_out` — output buffer, position, and remaining capacity
 *   - `total_in` / `total_out` — cumulative byte counts
 *   - `msg` — last error message (or `null`)
 *
 * ==Convenience methods==
 *
 * Use [[setInput]] and [[setOutput]] to configure buffers without manipulating the fields directly.
 *
 * @see
 *   [[Deflater]] the recommended compression API
 * @see
 *   [[Inflater]] the recommended decompression API
 */
/** @deprecated Not for public use in the future. */
@deprecated("Use Deflater or Inflater instead", "1.1.5")
class ZStream {

  private final val MAX_WBITS     = 15
  private final val DEF_WBITS     = MAX_WBITS
  private final val MAX_MEM_LEVEL = 9

  /** Next input byte array. Set via [[setInput]]. */
  var next_in: Array[Byte] = null // next input byte
  /** Current read position within `next_in`. */
  var next_in_index: Int   = 0

  /** Number of bytes available at `next_in` starting from `next_in_index`. */
  var avail_in: Int  = 0  // number of bytes available at next_in
  /** Total number of input bytes read so far. */
  var total_in: Long = 0L // total nb of input bytes read so far

  /** Next output byte array. Set via [[setOutput]]. */
  var next_out: Array[Byte] = null // next output byte should be put there
  /** Current write position within `next_out`. */
  var next_out_index: Int   = 0

  /** Remaining free space in `next_out` starting from `next_out_index`. */
  var avail_out: Int  = 0  // remaining free space at next_out
  /** Total number of bytes written to output so far. */
  var total_out: Long = 0L // total nb of bytes output so far

  /** Last error message, or `null` if no error. */
  var msg: String = null

  var dstate: Deflate = null
  var istate: Inflate = null

  var data_type: Int = 0 // best guess about the data type: ascii or binary

  var adler: Checksum = null

  def this(adler: Checksum) = {
    this()
    this.adler = adler
  }

  {
    this.adler = new Adler32()
  }

  def inflateInit(): Int                               = inflateInit(DEF_WBITS)
  def inflateInit(nowrap: Boolean): Int                = inflateInit(DEF_WBITS, nowrap)
  def inflateInit(w: Int): Int                         = inflateInit(w, false)
  def inflateInit(wrapperType: JZlib.WrapperType): Int = inflateInit(DEF_WBITS, wrapperType)

  def inflateInit(w: Int, wrapperType: JZlib.WrapperType): Int = {
    var ww     = w
    var nowrap = false
    if (wrapperType == W_NONE) { nowrap = true }
    else if (wrapperType == W_GZIP) { ww += 16 }
    else if (wrapperType == W_ANY) { ww |= Inflate.INFLATE_ANY }
    // W_ZLIB: nothing
    inflateInit(ww, nowrap)
  }

  def inflateInit(w: Int, nowrap: Boolean): Int = {
    istate = new Inflate(this)
    istate.inflateInit(if (nowrap) -w else w)
  }

  def inflate(f: Int): Int = {
    if (istate == null) return Z_STREAM_ERROR
    istate.inflate(f)
  }

  def inflateEnd(): Int = {
    if (istate == null) return Z_STREAM_ERROR
    istate.inflateEnd()
  }

  def inflateSync(): Int = {
    if (istate == null) return Z_STREAM_ERROR
    istate.inflateSync()
  }

  def inflateSyncPoint(): Int = {
    if (istate == null) return Z_STREAM_ERROR
    istate.inflateSyncPoint()
  }

  def inflateSetDictionary(dictionary: Array[Byte], dictLength: Int): Int = {
    if (istate == null) return Z_STREAM_ERROR
    istate.inflateSetDictionary(dictionary, dictLength)
  }

  def inflateFinished(): Boolean = istate.mode == 12 /*DONE*/

  def deflateInit(level: Int): Int                  = deflateInit(level, MAX_WBITS)
  def deflateInit(level: Int, nowrap: Boolean): Int = deflateInit(level, MAX_WBITS, nowrap)
  def deflateInit(level: Int, bits: Int): Int       = deflateInit(level, bits, false)

  def deflateInit(level: Int, bits: Int, memlevel: Int, wrapperType: JZlib.WrapperType): Int = {
    if (bits < 9 || bits > 15) return Z_STREAM_ERROR
    var b = bits
    if (wrapperType == W_NONE) { b *= -1 }
    else if (wrapperType == W_GZIP) { b += 16 }
    else if (wrapperType == W_ANY) { return Z_STREAM_ERROR }
    // W_ZLIB: nothing
    deflateInit(level, b, memlevel)
  }

  def deflateInit(level: Int, bits: Int, memlevel: Int): Int = {
    dstate = new Deflate(this)
    dstate.deflateInit(level, bits, memlevel)
  }

  def deflateInit(level: Int, bits: Int, nowrap: Boolean): Int = {
    dstate = new Deflate(this)
    dstate.deflateInit(level, if (nowrap) -bits else bits)
  }

  def deflate(flush: Int): Int = {
    if (dstate == null) return Z_STREAM_ERROR
    dstate.deflate(flush)
  }

  def deflateEnd(): Int = {
    if (dstate == null) return Z_STREAM_ERROR
    val ret = dstate.deflateEnd()
    dstate = null
    ret
  }

  def deflateParams(level: Int, strategy: Int): Int = {
    if (dstate == null) return Z_STREAM_ERROR
    dstate.deflateParams(level, strategy)
  }

  def deflateSetDictionary(dictionary: Array[Byte], dictLength: Int): Int = {
    if (dstate == null) return Z_STREAM_ERROR
    dstate.deflateSetDictionary(dictionary, dictLength)
  }

  // Flush as much pending output as possible.
  def flush_pending(): Unit = {
    var len = dstate.pending
    if (len > avail_out) len = avail_out
    if (len == 0) return

    System.arraycopy(dstate.pending_buf, dstate.pending_out, next_out, next_out_index, len)

    next_out_index += len
    dstate.pending_out += len
    total_out += len
    avail_out -= len
    dstate.pending -= len
    if (dstate.pending == 0) dstate.pending_out = 0
  }

  // Read a new buffer from the current input stream.
  def read_buf(buf: Array[Byte], start: Int, size: Int): Int = {
    var len = avail_in
    if (len > size) len = size
    if (len == 0) return 0

    avail_in -= len

    if (dstate.wrap != 0) adler.update(next_in, next_in_index, len)
    System.arraycopy(next_in, next_in_index, buf, start, len)
    next_in_index += len
    total_in += len
    len
  }

  def getAdler: Long = adler.getValue

  def free(): Unit = {
    next_in = null
    next_out = null
    msg = null
  }

  /**
   * Sets the output buffer.
   *
   * @param buf
   *   the byte array to write compressed/decompressed data into
   */
  def setOutput(buf: Array[Byte]): Unit = setOutput(buf, 0, buf.length)

  /**
   * Sets the output buffer with offset and length.
   *
   * @param buf
   *   the byte array to write into
   * @param off
   *   starting offset in `buf`
   * @param len
   *   maximum number of bytes to write
   */
  def setOutput(buf: Array[Byte], off: Int, len: Int): Unit = {
    next_out = buf
    next_out_index = off
    avail_out = len
  }

  /**
   * Sets the input buffer (replaces any previous input).
   *
   * @param buf
   *   the byte array containing data to compress/decompress
   */
  def setInput(buf: Array[Byte]): Unit = setInput(buf, 0, buf.length, false)

  /**
   * Sets the input buffer, optionally appending to unconsumed input.
   *
   * @param buf
   *   the byte array containing new input data
   * @param append
   *   if `true`, appends to any unconsumed input rather than replacing it
   */
  def setInput(buf: Array[Byte], append: Boolean): Unit = setInput(buf, 0, buf.length, append)

  /**
   * Sets the input buffer with offset, length, and append control.
   *
   * When `append` is `true` and there is unconsumed input, the new data is concatenated to the remaining bytes.
   * Otherwise the buffer is replaced.
   *
   * @param buf
   *   the byte array containing input data
   * @param off
   *   starting offset in `buf`
   * @param len
   *   number of bytes to use from `buf`
   * @param append
   *   if `true`, appends to unconsumed input
   */
  def setInput(buf: Array[Byte], off: Int, len: Int, append: Boolean): Unit = {
    if (len <= 0 && append && next_in != null) return

    if (avail_in > 0 && append) {
      val tmp = new Array[Byte](avail_in + len)
      System.arraycopy(next_in, next_in_index, tmp, 0, avail_in)
      System.arraycopy(buf, off, tmp, avail_in, len)
      next_in = tmp
      next_in_index = 0
      avail_in += len
    } else {
      next_in = buf
      next_in_index = off
      avail_in = len
    }
  }

  def getNextIn: Array[Byte]           = next_in
  def setNextIn(v: Array[Byte]): Unit  = next_in = v
  def getNextInIndex: Int              = next_in_index
  def setNextInIndex(v: Int): Unit     = next_in_index = v
  def getAvailIn: Int                  = avail_in
  def setAvailIn(v: Int): Unit         = avail_in = v
  def getNextOut: Array[Byte]          = next_out
  def setNextOut(v: Array[Byte]): Unit = next_out = v
  def getNextOutIndex: Int             = next_out_index
  def setNextOutIndex(v: Int): Unit    = next_out_index = v
  def getAvailOut: Int                 = avail_out
  def setAvailOut(v: Int): Unit        = avail_out = v
  def getTotalOut: Long                = total_out
  def getTotalIn: Long                 = total_in
  def getMessage: String               = msg

  // Overridden by Inflater and Deflater.
  def end(): Int          = Z_OK
  def finished(): Boolean = false
}
