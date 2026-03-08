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

final class Inflater extends ZStream {

  private object Const {
    final val MAX_WBITS = 15
    final val DEF_WBITS = MAX_WBITS
  }

  private var _finished: Boolean = false

  // Default constructor: init with DEF_WBITS
  init()

  def this(wrapperType: JZlib.WrapperType) = {
    this()
    val ret = init(DEF_WBITS, wrapperType)
    if (ret != Z_OK)
      throw new GZIPException(ret + ": " + msg)
  }

  def this(w: Int, wrapperType: JZlib.WrapperType) = {
    this()
    val ret = init(w, wrapperType)
    if (ret != Z_OK)
      throw new GZIPException(ret + ": " + msg)
  }

  def this(w: Int) = {
    this()
    val ret = init(w, false)
    if (ret != Z_OK)
      throw new GZIPException(ret + ": " + msg)
  }

  def this(nowrap: Boolean) = {
    this()
    val ret = init(DEF_WBITS, nowrap)
    if (ret != Z_OK)
      throw new GZIPException(ret + ": " + msg)
  }

  def this(w: Int, nowrap: Boolean) = {
    this()
    val ret = init(w, nowrap)
    if (ret != Z_OK)
      throw new GZIPException(ret + ": " + msg)
  }

  def init(): Int =
    init(DEF_WBITS)

  def init(wrapperType: JZlib.WrapperType): Int =
    init(DEF_WBITS, wrapperType)

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

  def init(nowrap: Boolean): Int =
    init(DEF_WBITS, nowrap)

  def init(w: Int): Int =
    init(w, false)

  def init(w: Int, nowrap: Boolean): Int = {
    _finished = false
    istate = new Inflate(this)
    istate.inflateInit(if (nowrap) -w else w)
  }

  override def inflate(f: Int): Int = {
    if (istate == null) return Z_STREAM_ERROR
    val ret = istate.inflate(f)
    if (ret == Z_STREAM_END)
      _finished = true
    ret
  }

  override def end(): Int = {
    _finished = true
    if (istate == null) return Z_STREAM_ERROR
    val ret = istate.inflateEnd()
    // istate = null  // intentionally left commented as in original
    ret
  }

  def sync(): Int = {
    if (istate == null)
      return Z_STREAM_ERROR
    istate.inflateSync()
  }

  def syncPoint(): Int = {
    if (istate == null)
      return Z_STREAM_ERROR
    istate.inflateSyncPoint()
  }

  def setDictionary(dictionary: Array[Byte], dictLength: Int): Int = {
    if (istate == null)
      return Z_STREAM_ERROR
    istate.inflateSetDictionary(dictionary, dictLength)
  }

  override def finished(): Boolean = istate.mode == 12 /*DONE*/
}
