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

import java.io.{EOFException, FilterInputStream, IOException, InputStream}
import JZlib._

object InflaterInputStream {
  private[jzlib] final val DEFAULT_BUFSIZE = 512
}

class InflaterInputStream(in: InputStream,
                           val inflater: Inflater,
                           size: Int,
                           close_in: Boolean)
  extends FilterInputStream(in) {

  if (in == null || inflater == null) throw new NullPointerException()
  else if (size <= 0) throw new IllegalArgumentException("buffer size must be greater than 0")

  protected var buf: Array[Byte] = new Array[Byte](size)

  private var closed: Boolean = false

  private var eof: Boolean = false

  protected var myinflater: Boolean = false

  private val byte1: Array[Byte] = new Array[Byte](1)

  def this(in: InputStream, nowrap: Boolean) = {
    this(in, new Inflater(nowrap), InflaterInputStream.DEFAULT_BUFSIZE, true)
    myinflater = true
  }

  def this(in: InputStream) = {
    this(in, false)
  }

  def this(in: InputStream, inflater: Inflater) =
    this(in, inflater, InflaterInputStream.DEFAULT_BUFSIZE, true)

  def this(in: InputStream, inflater: Inflater, size: Int) =
    this(in, inflater, size, true)

  override def read(): Int = {
    if (closed) throw new IOException("Stream closed")
    if (read(byte1, 0, 1) == -1) -1 else byte1(0) & 0xff
  }

  override def read(b: Array[Byte], off: Int, len: Int): Int = {
    if (closed) throw new IOException("Stream closed")
    if (b == null) {
      throw new NullPointerException()
    } else if (off < 0 || len < 0 || len > b.length - off) {
      throw new IndexOutOfBoundsException()
    } else if (len == 0) {
      return 0
    } else if (eof) {
      return -1
    }

    var n   = 0
    var off0 = off
    inflater.setOutput(b, off, len)
    while (!eof) {
      if (inflater.avail_in == 0)
        fill()
      val err = inflater.inflate(Z_NO_FLUSH)
      n   += inflater.next_out_index - off0
      off0 = inflater.next_out_index
      err match {
        case Z_DATA_ERROR =>
          throw new IOException(inflater.msg)
        case Z_STREAM_END | Z_NEED_DICT =>
          eof = true
          if (err == Z_NEED_DICT)
            return -1
        case _ =>
      }
      if (inflater.avail_out == 0)
        return n
    }
    n
  }

  override def available(): Int = {
    if (closed) throw new IOException("Stream closed")
    if (eof) 0 else 1
  }

  private val skipBuf: Array[Byte] = new Array[Byte](512)

  override def skip(n: Long): Long = {
    if (n < 0)
      throw new IllegalArgumentException("negative skip length")
    if (closed) throw new IOException("Stream closed")

    val max   = Math.min(n, Integer.MAX_VALUE).toInt
    var total = 0
    while (total < max) {
      var len = max - total
      if (len > skipBuf.length) len = skipBuf.length
      len = read(skipBuf, 0, len)
      if (len == -1) {
        eof = true
        return total
      }
      total += len
    }
    total
  }

  override def close(): Unit = {
    if (!closed) {
      if (myinflater)
        inflater.end()
      if (close_in)
        in.close()
      closed = true
    }
  }

  protected def fill(): Unit = {
    if (closed) throw new IOException("Stream closed")
    val len = in.read(buf, 0, buf.length)
    if (len == -1) {
      if (inflater.istate.wrap == 0 && !inflater.finished()) {
        buf(0) = 0
        inflater.setInput(buf, 0, 1, true)
      } else if (inflater.istate.was != -1) { // in reading trailer
        throw new IOException("footer is not found")
      } else {
        throw new EOFException("Unexpected end of ZLIB input stream")
      }
    } else {
      inflater.setInput(buf, 0, len, true)
    }
  }

  override def markSupported(): Boolean = false

  override def mark(readlimit: Int): Unit = {}

  @throws[IOException]
  override def reset(): Unit =
    throw new IOException("mark/reset not supported")

  def getTotalIn(): Long = inflater.getTotalIn

  def getTotalOut(): Long = inflater.getTotalOut

  def getAvailIn(): Array[Byte] = {
    if (inflater.avail_in <= 0)
      return null
    val tmp = new Array[Byte](inflater.avail_in)
    System.arraycopy(inflater.next_in, inflater.next_in_index, tmp, 0, inflater.avail_in)
    tmp
  }

  def readHeader(): Unit = {
    val empty = "".getBytes()
    inflater.setInput(empty, 0, 0, false)
    inflater.setOutput(empty, 0, 0)

    inflater.inflate(Z_NO_FLUSH)
    if (!inflater.istate.inParsingHeader()) {
      return
    }

    val b1 = new Array[Byte](1)
    do {
      val i = in.read(b1)
      if (i <= 0)
        throw new IOException("no input")
      inflater.setInput(b1)
      val err = inflater.inflate(Z_NO_FLUSH)
      if (err != 0 /*Z_OK*/)
        throw new IOException(inflater.msg)
    } while (inflater.istate.inParsingHeader())
  }

  def getInflater(): Inflater = inflater
}
