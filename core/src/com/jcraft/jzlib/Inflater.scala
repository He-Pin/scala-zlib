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
 * Decompresses data that was compressed using the DEFLATE algorithm (RFC 1951).
 *
 * `Inflater` is the high-level decompression API. It supports raw deflate, zlib-wrapped (RFC 1950), GZIP-wrapped (RFC
 * 1952), and auto-detecting input via [[JZlib.W_ANY]].
 *
 * Thread safety: instances are '''not''' thread-safe. Each thread should use its own `Inflater`.
 *
 * ==Basic usage==
 * {{{
 * val inflater = new Inflater()
 * inflater.setInput(compressedData)
 * inflater.setOutput(outputBuffer)
 * while (inflater.inflate(JZlib.Z_NO_FLUSH) != JZlib.Z_STREAM_END) {
 *   // process output
 * }
 * inflater.end()
 * }}}
 *
 * ==Auto-detecting ZLIB or GZIP==
 * {{{
 * val inflater = new Inflater(JZlib.W_ANY)
 * }}}
 *
 * @see
 *   [[Deflater]] for compression
 * @see
 *   [[JZlib]] for constants and wrapper types
 */
final class Inflater extends ZStream {

  private object Const {
    final val MAX_WBITS = 15
    final val DEF_WBITS = MAX_WBITS
  }

  private var _finished: Boolean = false

  // Default constructor: init with DEF_WBITS
  init()

  /**
   * Creates an `Inflater` configured for the given wrapper type.
   *
   * @param wrapperType
   *   expected input format: [[JZlib.W_ZLIB]], [[JZlib.W_GZIP]], [[JZlib.W_NONE]] (raw), or [[JZlib.W_ANY]]
   *   (auto-detect)
   * @throws GZIPException
   *   if initialization fails
   */
  def this(wrapperType: JZlib.WrapperType) = {
    this()
    val ret = init(DEF_WBITS, wrapperType)
    if (ret != Z_OK)
      throw new GZIPException(ret + ": " + msg)
  }

  /**
   * Creates an `Inflater` with the given window bits and wrapper type.
   *
   * @param w
   *   window size in bits (9–15)
   * @param wrapperType
   *   expected input format
   * @throws GZIPException
   *   if initialization fails
   */
  def this(w: Int, wrapperType: JZlib.WrapperType) = {
    this()
    val ret = init(w, wrapperType)
    if (ret != Z_OK)
      throw new GZIPException(ret + ": " + msg)
  }

  /**
   * Creates an `Inflater` with the given window bits (zlib-wrapped).
   *
   * @param w
   *   window size in bits (9–15)
   * @throws GZIPException
   *   if initialization fails
   */
  def this(w: Int) = {
    this()
    val ret = init(w, false)
    if (ret != Z_OK)
      throw new GZIPException(ret + ": " + msg)
  }

  /**
   * Creates an `Inflater` with default window bits and optional raw mode.
   *
   * @param nowrap
   *   if `true`, expects raw deflate input (no header/trailer)
   * @throws GZIPException
   *   if initialization fails
   */
  def this(nowrap: Boolean) = {
    this()
    val ret = init(DEF_WBITS, nowrap)
    if (ret != Z_OK)
      throw new GZIPException(ret + ": " + msg)
  }

  /**
   * Creates an `Inflater` with the given window bits and raw-deflate flag.
   *
   * @param w
   *   window size in bits (9–15)
   * @param nowrap
   *   if `true`, expects raw deflate input
   * @throws GZIPException
   *   if initialization fails
   */
  def this(w: Int, nowrap: Boolean) = {
    this()
    val ret = init(w, nowrap)
    if (ret != Z_OK)
      throw new GZIPException(ret + ": " + msg)
  }

  /**
   * Initializes with default window bits and zlib wrapping.
   *
   * @return
   *   [[JZlib.Z_OK]] on success
   */
  def init(): Int =
    init(DEF_WBITS)

  /**
   * Initializes with default window bits for the given wrapper type.
   *
   * @param wrapperType
   *   expected input format
   * @return
   *   [[JZlib.Z_OK]] on success
   */
  def init(wrapperType: JZlib.WrapperType): Int =
    init(DEF_WBITS, wrapperType)

  /**
   * Initializes with the given window bits and wrapper type.
   *
   * Use [[JZlib.W_ANY]] to auto-detect ZLIB or GZIP input.
   *
   * @param w
   *   window size in bits (9–15)
   * @param wrapperType
   *   expected input format
   * @return
   *   [[JZlib.Z_OK]] on success
   */
  def init(w: Int, wrapperType: JZlib.WrapperType): Int = {
    var nowrap = false
    var wbits  = w
    if (wrapperType == JZlib.W_NONE) {
      nowrap = true
    } else if (wrapperType == JZlib.W_GZIP) {
      wbits += 16
    } else if (wrapperType == JZlib.W_ANY) {
      wbits = wbits | Inflate.INFLATE_ANY
    }
    // W_ZLIB: no adjustment
    init(wbits, nowrap)
  }

  /**
   * Initializes with default window bits and optional raw mode.
   *
   * @param nowrap
   *   if `true`, expects raw deflate input
   * @return
   *   [[JZlib.Z_OK]] on success
   */
  def init(nowrap: Boolean): Int =
    init(DEF_WBITS, nowrap)

  /**
   * Initializes with the given window bits (zlib wrapping).
   *
   * @param w
   *   window size in bits (9–15)
   * @return
   *   [[JZlib.Z_OK]] on success
   */
  def init(w: Int): Int =
    init(w, false)

  /**
   * Core initializer used by all other `init` overloads.
   *
   * @param w
   *   window size in bits; negative values mean raw deflate, values above 15 enable GZIP, bitwise-OR with
   *   `Inflate.INFLATE_ANY` enables auto-detection
   * @param nowrap
   *   if `true`, negates `w` for raw deflate
   * @return
   *   [[JZlib.Z_OK]] on success
   */
  def init(w: Int, nowrap: Boolean): Int = {
    _finished = false
    istate = new Inflate(this)
    istate.inflateInit(if (nowrap) -w else w)
  }

  /**
   * Decompresses input data, writing to the output buffer.
   *
   * Call repeatedly until [[JZlib.Z_STREAM_END]] is returned, indicating all compressed data has been processed.
   *
   * @param f
   *   flush mode (typically [[JZlib.Z_NO_FLUSH]] or [[JZlib.Z_FINISH]])
   * @return
   *   [[JZlib.Z_OK]] if progress was made, [[JZlib.Z_STREAM_END]] when decompression is complete, [[JZlib.Z_NEED_DICT]]
   *   if a preset dictionary is required, or a negative error code
   */
  override def inflate(f: Int): Int = {
    if (istate == null) return Z_STREAM_ERROR
    val ret = istate.inflate(f)
    if (ret == Z_STREAM_END)
      _finished = true
    ret
  }

  /**
   * Frees all internal decompression state.
   *
   * Must be called when the inflater is no longer needed to release resources. After calling `end()`, the inflater
   * cannot be reused without re-initialization.
   *
   * @return
   *   [[JZlib.Z_OK]] on success, or [[JZlib.Z_STREAM_ERROR]] if the inflater was not initialized
   */
  override def end(): Int = {
    _finished = true
    if (istate == null) return Z_STREAM_ERROR
    val ret = istate.inflateEnd()
    // istate = null  // intentionally left commented as in original
    ret
  }

  /**
   * Skips invalid compressed data until a possible full flush point is found, allowing recovery from data errors.
   *
   * @return
   *   [[JZlib.Z_OK]] if a sync point was found
   */
  def sync(): Int = {
    if (istate == null)
      return Z_STREAM_ERROR
    istate.inflateSync()
  }

  /**
   * Returns 1 if the inflate is currently at a sync point in the compressed data, 0 otherwise.
   *
   * @return
   *   1 if at a sync point, 0 otherwise
   */
  def syncPoint(): Int = {
    if (istate == null)
      return Z_STREAM_ERROR
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
   *   [[JZlib.Z_OK]] on success, or [[JZlib.Z_DATA_ERROR]] if the dictionary does not match
   */
  def setDictionary(dictionary: Array[Byte], dictLength: Int): Int = {
    if (istate == null)
      return Z_STREAM_ERROR
    istate.inflateSetDictionary(dictionary, dictLength)
  }

  /** Returns `true` when decompression has completed (inflate state is DONE). */
  override def finished(): Boolean = istate.mode == 12 /*DONE*/
}
