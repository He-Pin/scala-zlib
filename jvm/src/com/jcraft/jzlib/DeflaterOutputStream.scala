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

import java.io.{ FilterOutputStream, IOException, OutputStream }
import JZlib._

object DeflaterOutputStream {
  private[jzlib] final val DEFAULT_BUFSIZE = 512
}

class DeflaterOutputStream(out: OutputStream, val deflater: Deflater, size: Int, close_out: Boolean)
    extends FilterOutputStream(out) {

  if (out == null || deflater == null) throw new NullPointerException()
  else if (size <= 0) throw new IllegalArgumentException("buffer size must be greater than 0")

  protected var buffer: Array[Byte] = new Array[Byte](size)

  private var closed: Boolean = false

  private var syncFlush: Boolean = false

  private val buf1: Array[Byte] = new Array[Byte](1)

  protected var mydeflater: Boolean = false

  def this(out: OutputStream) = {
    this(out, new Deflater(JZlib.Z_DEFAULT_COMPRESSION), DeflaterOutputStream.DEFAULT_BUFSIZE, true)
    mydeflater = true
  }

  def this(out: OutputStream, deflater: Deflater) =
    this(out, deflater, DeflaterOutputStream.DEFAULT_BUFSIZE, true)

  def this(out: OutputStream, deflater: Deflater, size: Int) =
    this(out, deflater, size, true)

  override def write(b: Int): Unit = {
    buf1(0) = (b & 0xff).toByte
    write(buf1, 0, 1)
  }

  override def write(b: Array[Byte], off: Int, len: Int): Unit = {
    if (deflater.finished()) {
      throw new IOException("finished")
    } else if (off < 0 | len < 0 | off + len > b.length) {
      throw new IndexOutOfBoundsException()
    } else if (len == 0) {
      return
    } else {
      val flush = if (syncFlush) Z_SYNC_FLUSH else Z_NO_FLUSH
      deflater.setInput(b, off, len, true)
      while (deflater.avail_in > 0) {
        val err = deflate(flush)
        if (err == Z_STREAM_END)
          return
      }
    }
  }

  def finish(): Unit = {
    while (!deflater.finished()) {
      deflate(Z_FINISH)
    }
  }

  override def close(): Unit = {
    if (!closed) {
      finish()
      if (mydeflater) {
        deflater.end()
      }
      if (close_out)
        out.close()
      closed = true
    }
  }

  protected def deflate(flush: Int): Int = {
    deflater.setOutput(buffer, 0, buffer.length)
    val err = deflater.deflate(flush)
    err match {
      case Z_OK | Z_STREAM_END                                        =>
      // ok
      case Z_BUF_ERROR if deflater.avail_in <= 0 && flush != Z_FINISH =>
      // flush() without any data
      case _                                                          =>
        throw new IOException("failed to deflate: error=" + err + " avail_out=" + deflater.avail_out)
    }
    val len = deflater.next_out_index
    if (len > 0) {
      out.write(buffer, 0, len)
    }
    err
  }

  override def flush(): Unit = {
    if (syncFlush && !deflater.finished()) {
      var continue = true
      while (continue) {
        val err = deflate(Z_SYNC_FLUSH)
        if (deflater.next_out_index < buffer.length)
          continue = false
        else if (err == Z_STREAM_END)
          continue = false
      }
    }
    out.flush()
  }

  def getTotalIn(): Long = deflater.getTotalIn

  def getTotalOut(): Long = deflater.getTotalOut

  def setSyncFlush(syncFlush: Boolean): Unit =
    this.syncFlush = syncFlush

  def getSyncFlush(): Boolean = this.syncFlush

  def getDeflater(): Deflater = deflater
}
