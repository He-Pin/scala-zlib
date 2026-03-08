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

package com.jcraft.jzlib

import java.io.{ FilterInputStream, IOException, InputStream }
import JZlib._

/**
 * ZInputStream
 *
 * @deprecated
 *   use DeflaterOutputStream or InflaterInputStream
 */
@deprecated("Use DeflaterOutputStream or InflaterInputStream", "1.1.5")
class ZInputStream(in: InputStream) extends FilterInputStream(in) {

  protected var flush: Int         = Z_NO_FLUSH
  protected var compress: Boolean  = _
  protected var theIn: InputStream = _

  protected var deflater: Deflater       = _
  protected var iis: InflaterInputStream = _

  // Default constructor: inflate mode
  {
    iis = new InflaterInputStream(in, false)
    compress = false
  }

  def this(in: InputStream, nowrap: Boolean) = {
    this(in)
    iis = new InflaterInputStream(in, nowrap)
    compress = false
  }

  def this(in: InputStream, level: Int) = {
    this(in)
    this.theIn = in
    deflater = new Deflater()
    deflater.init(level)
    compress = true
  }

  private val buf1: Array[Byte] = new Array[Byte](1)

  override def read(): Int = {
    if (read(buf1, 0, 1) == -1) -1 else buf1(0) & 0xff
  }

  private val buf: Array[Byte] = new Array[Byte](512)

  override def read(b: Array[Byte], off: Int, len: Int): Int = {
    if (compress) {
      deflater.setOutput(b, off, len)
      var result = -2 // sentinel
      var loop   = true
      while (loop) {
        val datalen = theIn.read(buf, 0, buf.length)
        if (datalen == -1) { result = -1; loop = false }
        else {
          deflater.setInput(buf, 0, datalen, true)
          val err = deflater.deflate(flush)
          if (deflater.next_out_index > 0) { result = deflater.next_out_index; loop = false }
          else if (err == Z_STREAM_END) { result = 0; loop = false }
          else if (err == Z_STREAM_ERROR || err == Z_DATA_ERROR)
            throw new ZStreamException("deflating: " + deflater.msg)
        }
      }
      result
    } else {
      iis.read(b, off, len)
    }
  }

  override def skip(n: Long): Long = {
    val len = Math.min(n, 512L).toInt
    val tmp = new Array[Byte](len)
    read(tmp).toLong
  }

  def getFlushMode(): Int = flush

  def setFlushMode(flush: Int): Unit =
    this.flush = flush

  def getTotalIn(): Long =
    if (compress) deflater.total_in else iis.getTotalIn()

  def getTotalOut(): Long =
    if (compress) deflater.total_out else iis.getTotalOut()

  override def close(): Unit = {
    if (compress) deflater.end()
    else iis.close()
  }
}
