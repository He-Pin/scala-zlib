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

final class Deflater extends ZStream {

  private object Const {
    final val MAX_WBITS     = 15
    final val DEF_WBITS     = MAX_WBITS
    final val MAX_MEM_LEVEL = 9
  }

  private var _finished: Boolean = false

  def this(level: Int) = {
    this()
    val ret = init(level, MAX_WBITS)
    if (ret != Z_OK)
      throw new GZIPException(ret + ": " + msg)
  }

  def this(level: Int, nowrap: Boolean) = {
    this()
    val ret = init(level, MAX_WBITS, nowrap)
    if (ret != Z_OK)
      throw new GZIPException(ret + ": " + msg)
  }

  def this(level: Int, bits: Int) = {
    this()
    val ret = init(level, bits, false)
    if (ret != Z_OK)
      throw new GZIPException(ret + ": " + msg)
  }

  def this(level: Int, bits: Int, nowrap: Boolean) = {
    this()
    val ret = init(level, bits, nowrap)
    if (ret != Z_OK)
      throw new GZIPException(ret + ": " + msg)
  }

  def this(level: Int, bits: Int, memlevel: Int, wrapperType: JZlib.WrapperType) = {
    this()
    val ret = init(level, bits, memlevel, wrapperType)
    if (ret != Z_OK)
      throw new GZIPException(ret + ": " + msg)
  }

  def this(level: Int, bits: Int, memlevel: Int) = {
    this()
    val ret = init(level, bits, memlevel)
    if (ret != Z_OK)
      throw new GZIPException(ret + ": " + msg)
  }

  def init(level: Int): Int =
    init(level, MAX_WBITS)

  def init(level: Int, nowrap: Boolean): Int =
    init(level, MAX_WBITS, nowrap)

  def init(level: Int, bits: Int): Int =
    init(level, bits, false)

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

  def init(level: Int, bits: Int, memlevel: Int): Int = {
    _finished = false
    dstate = new Deflate(this)
    dstate.deflateInit(level, bits, memlevel)
  }

  def init(level: Int, bits: Int, nowrap: Boolean): Int = {
    _finished = false
    dstate = new Deflate(this)
    dstate.deflateInit(level, if (nowrap) -bits else bits)
  }

  override def deflate(flush: Int): Int = {
    if (dstate == null) {
      return Z_STREAM_ERROR
    }
    val ret = dstate.deflate(flush)
    if (ret == Z_STREAM_END)
      _finished = true
    ret
  }

  override def end(): Int = {
    _finished = true
    if (dstate == null) return Z_STREAM_ERROR
    val ret = dstate.deflateEnd()
    dstate = null
    free()
    ret
  }

  def params(level: Int, strategy: Int): Int = {
    if (dstate == null) return Z_STREAM_ERROR
    dstate.deflateParams(level, strategy)
  }

  def setDictionary(dictionary: Array[Byte], dictLength: Int): Int = {
    if (dstate == null)
      return Z_STREAM_ERROR
    dstate.deflateSetDictionary(dictionary, dictLength)
  }

  override def finished(): Boolean = _finished

  def copy(src: Deflater): Int = {
    this._finished = src._finished
    Deflate.deflateCopy(this, src)
  }
}
