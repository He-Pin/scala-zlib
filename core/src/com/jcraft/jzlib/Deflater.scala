/* -*-mode:scala; c-basic-offset:2; indent-tabs-mode:nil -*- */
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

import JZlib._

/**
 * Compresses data using the DEFLATE algorithm (RFC 1951).
 *
 * `Deflater` is the high-level compression API. It supports raw deflate, zlib-wrapped (RFC 1950), and GZIP-wrapped (RFC
 * 1952) output via [[JZlib.WrapperType]].
 *
 * Thread safety: instances are '''not''' thread-safe. Each thread should use its own `Deflater`.
 *
 * ==Basic usage==
 * {{{
 * val deflater = new Deflater()
 * deflater.init(JZlib.Z_DEFAULT_COMPRESSION)
 * deflater.setInput(inputData)
 * deflater.setOutput(outputBuffer)
 * while (deflater.deflate(JZlib.Z_FINISH) != JZlib.Z_STREAM_END) {
 *   // process or grow output buffer
 * }
 * deflater.end()
 * }}}
 *
 * ==GZIP output==
 * {{{
 * val deflater = new Deflater(JZlib.Z_DEFAULT_COMPRESSION, 15, 8, JZlib.W_GZIP)
 * }}}
 *
 * @see
 *   [[Inflater]] for decompression
 * @see
 *   [[JZlib]] for compression levels, flush modes, and return codes
 */
final class Deflater extends ZStream {

  private object Const {
    final val MAX_WBITS     = 15
    final val DEF_WBITS     = MAX_WBITS
    final val MAX_MEM_LEVEL = 9
  }

  private var _finished: Boolean = false

  /**
   * Creates a `Deflater` and initializes it with the given compression level using the default window bits (`MAX_WBITS`
   * \= 15) and zlib wrapping.
   *
   * @param level
   *   compression level: [[JZlib.Z_NO_COMPRESSION]] (0) through [[JZlib.Z_BEST_COMPRESSION]] (9), or
   *   [[JZlib.Z_DEFAULT_COMPRESSION]] (-1)
   * @throws GZIPException
   *   if initialization fails
   */
  def this(level: Int) = {
    this()
    val ret = init(level, MAX_WBITS)
    if (ret != Z_OK)
      throw new GZIPException(ret + ": " + msg)
  }

  /**
   * Creates a `Deflater` with the given level and optional raw-deflate mode.
   *
   * @param level
   *   compression level (0–9 or -1)
   * @param nowrap
   *   if `true`, produces raw deflate output (no zlib header/trailer)
   * @throws GZIPException
   *   if initialization fails
   */
  def this(level: Int, nowrap: Boolean) = {
    this()
    val ret = init(level, MAX_WBITS, nowrap)
    if (ret != Z_OK)
      throw new GZIPException(ret + ": " + msg)
  }

  /**
   * Creates a `Deflater` with the given level and window bits.
   *
   * @param level
   *   compression level (0–9 or -1)
   * @param bits
   *   window size in bits (9–15); larger values give better compression at the cost of more memory
   * @throws GZIPException
   *   if initialization fails
   */
  def this(level: Int, bits: Int) = {
    this()
    val ret = init(level, bits, false)
    if (ret != Z_OK)
      throw new GZIPException(ret + ": " + msg)
  }

  /**
   * Creates a `Deflater` with the given level, window bits, and raw-deflate flag.
   *
   * @param level
   *   compression level (0–9 or -1)
   * @param bits
   *   window size in bits (9–15)
   * @param nowrap
   *   if `true`, produces raw deflate output
   * @throws GZIPException
   *   if initialization fails
   */
  def this(level: Int, bits: Int, nowrap: Boolean) = {
    this()
    val ret = init(level, bits, nowrap)
    if (ret != Z_OK)
      throw new GZIPException(ret + ": " + msg)
  }

  /**
   * Creates a `Deflater` with full control over compression parameters.
   *
   * This is the most flexible constructor. Use [[JZlib.W_GZIP]] for GZIP output or [[JZlib.W_NONE]] for raw deflate.
   *
   * @param level
   *   compression level (0–9 or -1)
   * @param bits
   *   window size in bits (9–15)
   * @param memlevel
   *   memory level for internal compression state (1–9); higher values use more memory but are faster
   * @param wrapperType
   *   output format: [[JZlib.W_ZLIB]], [[JZlib.W_GZIP]], or [[JZlib.W_NONE]]. [[JZlib.W_ANY]] is invalid for
   *   compression and causes [[JZlib.Z_STREAM_ERROR]].
   * @throws GZIPException
   *   if initialization fails
   */
  def this(level: Int, bits: Int, memlevel: Int, wrapperType: JZlib.WrapperType) = {
    this()
    val ret = init(level, bits, memlevel, wrapperType)
    if (ret != Z_OK)
      throw new GZIPException(ret + ": " + msg)
  }

  /**
   * Creates a `Deflater` with the given level, window bits, and memory level.
   *
   * @param level
   *   compression level (0–9 or -1)
   * @param bits
   *   window size in bits (9–15)
   * @param memlevel
   *   memory level (1–9)
   * @throws GZIPException
   *   if initialization fails
   */
  def this(level: Int, bits: Int, memlevel: Int) = {
    this()
    val ret = init(level, bits, memlevel)
    if (ret != Z_OK)
      throw new GZIPException(ret + ": " + msg)
  }

  /**
   * Initializes with the given compression level using default window bits.
   *
   * @param level
   *   compression level (0–9 or -1)
   * @return
   *   [[JZlib.Z_OK]] on success
   */
  def init(level: Int): Int =
    init(level, MAX_WBITS)

  /**
   * Initializes with the given compression level and raw-deflate flag.
   *
   * @param level
   *   compression level (0–9 or -1)
   * @param nowrap
   *   if `true`, raw deflate (no header/trailer)
   * @return
   *   [[JZlib.Z_OK]] on success
   */
  def init(level: Int, nowrap: Boolean): Int =
    init(level, MAX_WBITS, nowrap)

  /**
   * Initializes with the given compression level and window bits.
   *
   * @param level
   *   compression level (0–9 or -1)
   * @param bits
   *   window size in bits (9–15)
   * @return
   *   [[JZlib.Z_OK]] on success
   */
  def init(level: Int, bits: Int): Int =
    init(level, bits, false)

  /**
   * Initializes with full control over parameters including wrapper type.
   *
   * @param level
   *   compression level (0–9 or -1)
   * @param bits
   *   window size in bits (9–15)
   * @param memlevel
   *   memory level (1–9)
   * @param wrapperType
   *   output format: [[JZlib.W_ZLIB]], [[JZlib.W_GZIP]], or [[JZlib.W_NONE]]
   * @return
   *   [[JZlib.Z_OK]] on success, or [[JZlib.Z_STREAM_ERROR]] on invalid parameters
   */
  def init(level: Int, bits: Int, memlevel: Int, wrapperType: JZlib.WrapperType): Int = {
    if (bits < 9 || bits > 15) {
      return Z_STREAM_ERROR
    }
    val adjustedBits =
      if (wrapperType == JZlib.W_NONE) bits * -1
      else if (wrapperType == JZlib.W_GZIP) bits + 16
      else if (wrapperType == JZlib.W_ANY) return Z_STREAM_ERROR
      else bits // W_ZLIB
    init(level, adjustedBits, memlevel)
  }

  /**
   * Core initializer used by all other `init` overloads.
   *
   * @param level
   *   compression level (0–9 or -1)
   * @param bits
   *   window size in bits; negative values mean raw deflate, values above 15 enable GZIP wrapping
   * @param memlevel
   *   memory level (1–9)
   * @return
   *   [[JZlib.Z_OK]] on success
   */
  def init(level: Int, bits: Int, memlevel: Int): Int = {
    _finished = false
    dstate = new Deflate(this)
    dstate.deflateInit(level, bits, memlevel)
  }

  /**
   * Initializes with the given level, window bits, and raw-deflate flag.
   *
   * @param level
   *   compression level (0–9 or -1)
   * @param bits
   *   window size in bits (9–15)
   * @param nowrap
   *   if `true`, produces raw deflate output
   * @return
   *   [[JZlib.Z_OK]] on success
   */
  def init(level: Int, bits: Int, nowrap: Boolean): Int = {
    _finished = false
    dstate = new Deflate(this)
    dstate.deflateInit(level, if (nowrap) -bits else bits)
  }

  /**
   * Compresses input data, writing to the output buffer.
   *
   * Call repeatedly until all input is consumed and output is produced. Use [[JZlib.Z_FINISH]] as the flush parameter
   * to signal the end of input; the method returns [[JZlib.Z_STREAM_END]] when all output has been generated.
   *
   * @param flush
   *   flush mode: [[JZlib.Z_NO_FLUSH]], [[JZlib.Z_SYNC_FLUSH]], [[JZlib.Z_FULL_FLUSH]], or [[JZlib.Z_FINISH]]
   * @return
   *   [[JZlib.Z_OK]] if progress was made, [[JZlib.Z_STREAM_END]] when compression is complete, or a negative error
   *   code
   */
  override def deflate(flush: Int): Int = {
    if (dstate == null) {
      return Z_STREAM_ERROR
    }
    val ret = dstate.deflate(flush)
    if (ret == Z_STREAM_END)
      _finished = true
    ret
  }

  /**
   * Frees all internal compression state.
   *
   * Must be called when the deflater is no longer needed to release resources. After calling `end()`, the deflater
   * cannot be reused.
   *
   * @return
   *   [[JZlib.Z_OK]] on success, or [[JZlib.Z_STREAM_ERROR]] if the deflater was not initialized
   */
  override def end(): Int = {
    _finished = true
    if (dstate == null) return Z_STREAM_ERROR
    val ret = dstate.deflateEnd()
    dstate = null
    free()
    ret
  }

  /**
   * Dynamically changes the compression level and strategy.
   *
   * This can be used, for example, to switch between [[JZlib.Z_DEFAULT_STRATEGY]] and [[JZlib.Z_FILTERED]] mid-stream.
   *
   * @param level
   *   new compression level (0–9 or -1)
   * @param strategy
   *   new strategy: [[JZlib.Z_DEFAULT_STRATEGY]], [[JZlib.Z_FILTERED]], or [[JZlib.Z_HUFFMAN_ONLY]]
   * @return
   *   [[JZlib.Z_OK]] on success
   */
  def params(level: Int, strategy: Int): Int = {
    if (dstate == null) return Z_STREAM_ERROR
    dstate.deflateParams(level, strategy)
  }

  /**
   * Sets a preset dictionary for compression.
   *
   * Must be called after `init` and before the first call to `deflate`. The inflater must use the same dictionary
   * (matched by Adler-32 checksum).
   *
   * @param dictionary
   *   byte array containing the dictionary data
   * @param dictLength
   *   number of bytes to use from the dictionary array
   * @return
   *   [[JZlib.Z_OK]] on success
   */
  def setDictionary(dictionary: Array[Byte], dictLength: Int): Int = {
    if (dstate == null)
      return Z_STREAM_ERROR
    dstate.deflateSetDictionary(dictionary, dictLength)
  }

  /**
   * Returns `true` after [[deflate]] has returned [[JZlib.Z_STREAM_END]], indicating that all input has been
   * compressed.
   */
  override def finished(): Boolean = _finished

  /**
   * Copies the compression state from another `Deflater` into this one.
   *
   * @param src
   *   the source deflater whose state will be copied
   * @return
   *   [[JZlib.Z_OK]] on success
   */
  def copy(src: Deflater): Int = {
    this._finished = src._finished
    Deflate.deflateCopy(this, src)
  }
}
