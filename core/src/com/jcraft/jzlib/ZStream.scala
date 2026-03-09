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
 *   - `next_in` / `next_in_index` / `avail_in` - input buffer, position, and remaining bytes
 *   - `next_out` / `next_out_index` / `avail_out` - output buffer, position, and remaining capacity
 *   - `total_in` / `total_out` - cumulative byte counts
 *   - `msg` - last error message (or `null`)
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

  /**
   * Creates a `ZStream` with a custom checksum implementation.
   *
   * @param adler
   *   the [[Checksum]] instance to use for integrity checking (typically [[Adler32]] or [[CRC32]])
   */
  def this(adler: Checksum) = {
    this()
    this.adler = adler
  }

  {
    this.adler = new Adler32()
  }

  /**
   * Initializes the inflate (decompression) stream with default window bits (`MAX_WBITS` = 15) and zlib wrapping.
   *
   * @return
   *   [[JZlib.Z_OK]] on success
   * @see
   *   [[Inflater]] for the recommended decompression API
   */
  def inflateInit(): Int = inflateInit(DEF_WBITS)

  /**
   * Initializes the inflate stream with default window bits and optional raw mode.
   *
   * @param nowrap
   *   if `true`, expects raw deflate input (no zlib header/trailer)
   * @return
   *   [[JZlib.Z_OK]] on success
   */
  def inflateInit(nowrap: Boolean): Int = inflateInit(DEF_WBITS, nowrap)

  /**
   * Initializes the inflate stream with the given window bits and zlib wrapping.
   *
   * @param w
   *   window size in bits (9-15); must match or exceed the window size used during compression
   * @return
   *   [[JZlib.Z_OK]] on success
   */
  def inflateInit(w: Int): Int = inflateInit(w, false)

  /**
   * Initializes the inflate stream with default window bits for the given wrapper type.
   *
   * @param wrapperType
   *   expected input format: [[JZlib.W_ZLIB]], [[JZlib.W_GZIP]], [[JZlib.W_NONE]] (raw), or [[JZlib.W_ANY]]
   *   (auto-detect)
   * @return
   *   [[JZlib.Z_OK]] on success
   */
  def inflateInit(wrapperType: JZlib.WrapperType): Int = inflateInit(DEF_WBITS, wrapperType)

  /**
   * Initializes the inflate stream with the given window bits and wrapper type.
   *
   * Use [[JZlib.W_ANY]] to auto-detect whether input is ZLIB or GZIP wrapped.
   *
   * @param w
   *   window size in bits (9-15)
   * @param wrapperType
   *   expected input format: [[JZlib.W_ZLIB]], [[JZlib.W_GZIP]], [[JZlib.W_NONE]], or [[JZlib.W_ANY]]
   * @return
   *   [[JZlib.Z_OK]] on success
   */
  def inflateInit(w: Int, wrapperType: JZlib.WrapperType): Int = {
    var ww     = w
    var nowrap = false
    if (wrapperType == W_NONE) { nowrap = true }
    else if (wrapperType == W_GZIP) { ww += 16 }
    else if (wrapperType == W_ANY) { ww |= Inflate.INFLATE_ANY }
    // W_ZLIB: nothing
    inflateInit(ww, nowrap)
  }

  /**
   * Core inflate initializer used by all other `inflateInit` overloads.
   *
   * @param w
   *   window size in bits; negative values mean raw deflate, values above 15 enable GZIP wrapping
   * @param nowrap
   *   if `true`, negates `w` for raw deflate mode
   * @return
   *   [[JZlib.Z_OK]] on success
   */
  def inflateInit(w: Int, nowrap: Boolean): Int = {
    istate = new Inflate(this)
    istate.inflateInit(if (nowrap) -w else w)
  }

  /**
   * Decompresses input data, writing to the output buffer.
   *
   * Call repeatedly until [[JZlib.Z_STREAM_END]] is returned. Set input via [[setInput]] and output via [[setOutput]]
   * before each call.
   *
   * @param f
   *   flush mode: typically [[JZlib.Z_NO_FLUSH]] for normal operation, or [[JZlib.Z_FINISH]] to hint that no more input
   *   will be provided
   * @return
   *   [[JZlib.Z_OK]] if progress was made, [[JZlib.Z_STREAM_END]] when decompression is complete, [[JZlib.Z_NEED_DICT]]
   *   if a preset dictionary is required, or a negative error code ([[JZlib.Z_STREAM_ERROR]], [[JZlib.Z_DATA_ERROR]],
   *   [[JZlib.Z_BUF_ERROR]])
   */
  def inflate(f: Int): Int = {
    if (istate == null) return Z_STREAM_ERROR
    istate.inflate(f)
  }

  /**
   * Frees all internal decompression state.
   *
   * Must be called when the inflate stream is no longer needed to release resources. After calling `inflateEnd()`, the
   * stream cannot be used for decompression without re-initialization via [[inflateInit]].
   *
   * @return
   *   [[JZlib.Z_OK]] on success, or [[JZlib.Z_STREAM_ERROR]] if the inflate state was not initialized
   */
  def inflateEnd(): Int = {
    if (istate == null) return Z_STREAM_ERROR
    istate.inflateEnd()
  }

  /**
   * Skips invalid compressed data until a possible full flush point is found.
   *
   * This can be used to recover from data corruption by searching for a valid sync point in the compressed stream.
   *
   * @return
   *   [[JZlib.Z_OK]] if a sync point was found, or [[JZlib.Z_STREAM_ERROR]] if the inflate state was not initialized
   */
  def inflateSync(): Int = {
    if (istate == null) return Z_STREAM_ERROR
    istate.inflateSync()
  }

  /**
   * Checks whether the inflate stream is currently at a sync point in the compressed data.
   *
   * @return
   *   1 if at a sync point, 0 otherwise, or [[JZlib.Z_STREAM_ERROR]] if the inflate state was not initialized
   */
  def inflateSyncPoint(): Int = {
    if (istate == null) return Z_STREAM_ERROR
    istate.inflateSyncPoint()
  }

  /**
   * Sets a preset dictionary for decompression.
   *
   * Call this after [[inflate]] returns [[JZlib.Z_NEED_DICT]]. The dictionary must match the one used during
   * compression (verified by Adler-32 checksum).
   *
   * @param dictionary
   *   byte array containing the dictionary data
   * @param dictLength
   *   number of bytes to use from the dictionary array
   * @return
   *   [[JZlib.Z_OK]] on success, [[JZlib.Z_DATA_ERROR]] if the dictionary does not match, or [[JZlib.Z_STREAM_ERROR]]
   *   if the inflate state was not initialized
   */
  def inflateSetDictionary(dictionary: Array[Byte], dictLength: Int): Int = {
    if (istate == null) return Z_STREAM_ERROR
    istate.inflateSetDictionary(dictionary, dictLength)
  }

  /** Returns `true` when the inflate state has reached `DONE`, indicating decompression is complete. */
  def inflateFinished(): Boolean = istate.mode == 12 /*DONE*/

  /**
   * Initializes the deflate (compression) stream with the given compression level, using default window bits
   * (`MAX_WBITS` = 15) and zlib wrapping.
   *
   * @param level
   *   compression level: [[JZlib.Z_NO_COMPRESSION]] (0) through [[JZlib.Z_BEST_COMPRESSION]] (9), or
   *   [[JZlib.Z_DEFAULT_COMPRESSION]] (-1)
   * @return
   *   [[JZlib.Z_OK]] on success
   * @see
   *   [[Deflater]] for the recommended compression API
   */
  def deflateInit(level: Int): Int = deflateInit(level, MAX_WBITS)

  /**
   * Initializes the deflate stream with the given level and optional raw-deflate mode.
   *
   * @param level
   *   compression level (0-9 or -1)
   * @param nowrap
   *   if `true`, produces raw deflate output (no zlib header/trailer)
   * @return
   *   [[JZlib.Z_OK]] on success
   */
  def deflateInit(level: Int, nowrap: Boolean): Int = deflateInit(level, MAX_WBITS, nowrap)

  /**
   * Initializes the deflate stream with the given level and window bits (zlib wrapping).
   *
   * @param level
   *   compression level (0-9 or -1)
   * @param bits
   *   window size in bits (9-15); larger values give better compression at the cost of more memory
   * @return
   *   [[JZlib.Z_OK]] on success
   */
  def deflateInit(level: Int, bits: Int): Int = deflateInit(level, bits, false)

  /**
   * Initializes the deflate stream with full control over parameters including wrapper type.
   *
   * @param level
   *   compression level (0-9 or -1)
   * @param bits
   *   window size in bits (9-15)
   * @param memlevel
   *   memory level for internal compression state (1-9); higher values use more memory but are faster
   * @param wrapperType
   *   output format: [[JZlib.W_ZLIB]], [[JZlib.W_GZIP]], or [[JZlib.W_NONE]]. [[JZlib.W_ANY]] is invalid for
   *   compression and causes [[JZlib.Z_STREAM_ERROR]].
   * @return
   *   [[JZlib.Z_OK]] on success, or [[JZlib.Z_STREAM_ERROR]] on invalid parameters
   */
  def deflateInit(level: Int, bits: Int, memlevel: Int, wrapperType: JZlib.WrapperType): Int = {
    if (bits < 9 || bits > 15) return Z_STREAM_ERROR
    var b = bits
    if (wrapperType == W_NONE) { b *= -1 }
    else if (wrapperType == W_GZIP) { b += 16 }
    else if (wrapperType == W_ANY) { return Z_STREAM_ERROR }
    // W_ZLIB: nothing
    deflateInit(level, b, memlevel)
  }

  /**
   * Core deflate initializer with compression level, window bits, and memory level.
   *
   * @param level
   *   compression level (0-9 or -1)
   * @param bits
   *   window size in bits; negative values mean raw deflate, values above 15 enable GZIP wrapping
   * @param memlevel
   *   memory level (1-9)
   * @return
   *   [[JZlib.Z_OK]] on success
   */
  def deflateInit(level: Int, bits: Int, memlevel: Int): Int = {
    dstate = new Deflate(this)
    dstate.deflateInit(level, bits, memlevel)
  }

  /**
   * Initializes the deflate stream with the given level, window bits, and raw-deflate flag.
   *
   * @param level
   *   compression level (0-9 or -1)
   * @param bits
   *   window size in bits (9-15)
   * @param nowrap
   *   if `true`, produces raw deflate output (no header/trailer)
   * @return
   *   [[JZlib.Z_OK]] on success
   */
  def deflateInit(level: Int, bits: Int, nowrap: Boolean): Int = {
    dstate = new Deflate(this)
    dstate.deflateInit(level, if (nowrap) -bits else bits)
  }

  /**
   * Compresses input data, writing to the output buffer.
   *
   * Call repeatedly until all input is consumed and output is produced. Set input via [[setInput]] and output via
   * [[setOutput]] before each call. Use [[JZlib.Z_FINISH]] as the flush parameter to signal the end of input; the
   * method returns [[JZlib.Z_STREAM_END]] when all output has been generated.
   *
   * @param flush
   *   flush mode: [[JZlib.Z_NO_FLUSH]], [[JZlib.Z_SYNC_FLUSH]], [[JZlib.Z_FULL_FLUSH]], or [[JZlib.Z_FINISH]]
   * @return
   *   [[JZlib.Z_OK]] if progress was made, [[JZlib.Z_STREAM_END]] when compression is complete, or a negative error
   *   code ([[JZlib.Z_STREAM_ERROR]], [[JZlib.Z_BUF_ERROR]])
   */
  def deflate(flush: Int): Int = {
    if (dstate == null) return Z_STREAM_ERROR
    dstate.deflate(flush)
  }

  /**
   * Frees all internal compression state.
   *
   * Must be called when the deflate stream is no longer needed to release resources. After calling `deflateEnd()`, the
   * stream cannot be used for compression without re-initialization via [[deflateInit]].
   *
   * @return
   *   [[JZlib.Z_OK]] on success, or [[JZlib.Z_STREAM_ERROR]] if the deflate state was not initialized
   */
  def deflateEnd(): Int = {
    if (dstate == null) return Z_STREAM_ERROR
    val ret = dstate.deflateEnd()
    dstate = null
    ret
  }

  /**
   * Dynamically changes the compression level and strategy on an active deflate stream.
   *
   * This can be used to switch between [[JZlib.Z_DEFAULT_STRATEGY]] and [[JZlib.Z_FILTERED]] mid-stream.
   *
   * @param level
   *   new compression level (0-9 or -1)
   * @param strategy
   *   new strategy: [[JZlib.Z_DEFAULT_STRATEGY]], [[JZlib.Z_FILTERED]], or [[JZlib.Z_HUFFMAN_ONLY]]
   * @return
   *   [[JZlib.Z_OK]] on success, or [[JZlib.Z_STREAM_ERROR]] if the deflate state was not initialized
   */
  def deflateParams(level: Int, strategy: Int): Int = {
    if (dstate == null) return Z_STREAM_ERROR
    dstate.deflateParams(level, strategy)
  }

  /**
   * Sets a preset dictionary for compression.
   *
   * Must be called after [[deflateInit]] and before the first call to [[deflate]]. The inflater must use the same
   * dictionary (matched by Adler-32 checksum).
   *
   * @param dictionary
   *   byte array containing the dictionary data
   * @param dictLength
   *   number of bytes to use from the dictionary array
   * @return
   *   [[JZlib.Z_OK]] on success, or [[JZlib.Z_STREAM_ERROR]] if the deflate state was not initialized
   */
  def deflateSetDictionary(dictionary: Array[Byte], dictLength: Int): Int = {
    if (dstate == null) return Z_STREAM_ERROR
    dstate.deflateSetDictionary(dictionary, dictLength)
  }

  /**
   * Flushes as much pending compressed output as possible to the output buffer.
   *
   * Copies bytes from the internal pending output buffer to `next_out`, updating `next_out_index`, `avail_out`, and
   * `total_out` accordingly. This is an internal method used during compression.
   */
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

  /**
   * Reads input data into the given buffer, updating the running checksum.
   *
   * Copies up to `size` bytes from `next_in` into `buf` at the given `start` offset, advancing `next_in_index` and
   * decrementing `avail_in`. If wrapping is enabled, the checksum ([[adler]]) is updated with the consumed bytes.
   *
   * @param buf
   *   destination buffer
   * @param start
   *   starting offset in `buf`
   * @param size
   *   maximum number of bytes to read
   * @return
   *   the number of bytes actually read (0 if no input is available)
   */
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

  /**
   * Returns the running Adler-32 checksum value for data processed so far.
   *
   * For zlib-wrapped streams this is the Adler-32 of the uncompressed data. For GZIP-wrapped streams it is the CRC-32.
   *
   * @return
   *   the current checksum value
   */
  def getAdler: Long = adler.getValue

  /**
   * Frees all buffer references held by this stream.
   *
   * Sets `next_in`, `next_out`, and `msg` to `null`. Does '''not''' free internal compression or decompression state;
   * call [[deflateEnd]] or [[inflateEnd]] for that.
   */
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
  def setOutput(buf: Array[Byte]): Unit = {
    if (buf == null) throw new NullPointerException("output buffer is null")
    setOutput(buf, 0, buf.length)
  }

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
  def setInput(buf: Array[Byte]): Unit = {
    if (buf == null) throw new NullPointerException("input buffer is null")
    setInput(buf, 0, buf.length, false)
  }

  /**
   * Sets the input buffer, optionally appending to unconsumed input.
   *
   * @param buf
   *   the byte array containing new input data
   * @param append
   *   if `true`, appends to any unconsumed input rather than replacing it
   */
  def setInput(buf: Array[Byte], append: Boolean): Unit = {
    if (buf == null) throw new NullPointerException("input buffer is null")
    setInput(buf, 0, buf.length, append)
  }

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

  /** Returns the current input byte array. */
  def getNextIn: Array[Byte] = next_in

  /** Sets the input byte array directly. Prefer [[setInput]] for most use cases. */
  def setNextIn(v: Array[Byte]): Unit = next_in = v

  /** Returns the current read position within the input byte array. */
  def getNextInIndex: Int = next_in_index

  /** Sets the read position within the input byte array. */
  def setNextInIndex(v: Int): Unit = next_in_index = v

  /** Returns the number of bytes available in the input buffer. */
  def getAvailIn: Int = avail_in

  /** Sets the number of bytes available in the input buffer. */
  def setAvailIn(v: Int): Unit = avail_in = v

  /** Returns the current output byte array. */
  def getNextOut: Array[Byte] = next_out

  /** Sets the output byte array directly. Prefer [[setOutput]] for most use cases. */
  def setNextOut(v: Array[Byte]): Unit = next_out = v

  /** Returns the current write position within the output byte array. */
  def getNextOutIndex: Int = next_out_index

  /** Sets the write position within the output byte array. */
  def setNextOutIndex(v: Int): Unit = next_out_index = v

  /** Returns the remaining free space in the output buffer. */
  def getAvailOut: Int = avail_out

  /** Sets the remaining free space in the output buffer. */
  def setAvailOut(v: Int): Unit = avail_out = v

  /** Returns the total number of bytes written to output so far. */
  def getTotalOut: Long = total_out

  /** Returns the total number of input bytes read so far. */
  def getTotalIn: Long = total_in

  /** Returns the last error message, or `null` if no error occurred. */
  def getMessage: String = msg

  /**
   * Ends the stream, releasing internal state. Overridden by [[Deflater]] and [[Inflater]].
   *
   * @return
   *   [[JZlib.Z_OK]]
   */
  // Overridden by Inflater and Deflater.
  def end(): Int = Z_OK

  /**
   * Returns `true` if the stream has finished processing. Overridden by [[Deflater]] and [[Inflater]].
   *
   * @return
   *   always `false` in the base class
   */
  def finished(): Boolean = false
}
