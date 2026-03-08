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

/**
 * Legacy output stream that can both compress and decompress data.
 *
 * '''This class is deprecated.''' Use [[DeflaterOutputStream]] for compression and [[InflaterInputStream]] for
 * decompression instead.
 *
 * When constructed with a compression level, this stream compresses data written to it (deflate mode). When constructed
 * without a level, it decompresses data written to it (inflate mode) and writes the result to the underlying stream.
 *
 * @param out
 *   underlying output stream
 * @deprecated
 *   Use [[DeflaterOutputStream]] for compression or [[InflaterInputStream]] for decompression.
 * @see
 *   [[DeflaterOutputStream]]
 * @see
 *   [[InflaterInputStream]]
 */
@deprecated("Use DeflaterOutputStream or InflaterInputStream", "1.1.5")
class ZOutputStream(out: OutputStream) extends FilterOutputStream(out) {

  protected var bufsize: Int      = 512
  protected var flushMode: Int    = Z_NO_FLUSH
  protected var buf: Array[Byte]  = new Array[Byte](512)
  protected var compress: Boolean = _

  protected var theOut: OutputStream = out

  private var ended: Boolean = false

  private var dos: DeflaterOutputStream = _
  private var inflater: Inflater        = _

  // no-arg path (inflate)
  {
    this.theOut = out
    inflater = new Inflater()
    inflater.init()
    compress = false
  }

  /**
   * Creates a `ZOutputStream` in deflate (compression) mode.
   *
   * @param out
   *   underlying output stream
   * @param level
   *   compression level (0–9, or [[JZlib.Z_DEFAULT_COMPRESSION]])
   */
  def this(out: OutputStream, level: Int) = {
    this(out)
    this.theOut = out
    val deflater = new Deflater(level, false)
    dos = new DeflaterOutputStream(out, deflater)
    compress = true
  }

  /**
   * Creates a `ZOutputStream` in deflate (compression) mode with raw DEFLATE option.
   *
   * @param out
   *   underlying output stream
   * @param level
   *   compression level (0–9, or [[JZlib.Z_DEFAULT_COMPRESSION]])
   * @param nowrap
   *   if `true`, produce raw DEFLATE without a zlib header/trailer
   */
  def this(out: OutputStream, level: Int, nowrap: Boolean) = {
    this(out)
    this.theOut = out
    val deflater = new Deflater(level, nowrap)
    dos = new DeflaterOutputStream(out, deflater)
    compress = true
  }

  private val buf1: Array[Byte] = new Array[Byte](1)

  override def write(b: Int): Unit = {
    buf1(0) = b.toByte
    write(buf1, 0, 1)
  }

  override def write(b: Array[Byte], off: Int, len: Int): Unit = {
    if (len == 0) return
    if (compress) {
      dos.write(b, off, len)
    } else {
      inflater.setInput(b, off, len, true)
      var err = Z_OK
      while (inflater.avail_in > 0) {
        inflater.setOutput(buf, 0, buf.length)
        err = inflater.inflate(flushMode)
        if (inflater.next_out_index > 0)
          theOut.write(buf, 0, inflater.next_out_index)
        if (err != Z_OK)
          return
      }
      if (err != Z_OK)
        throw new ZStreamException("inflating: " + inflater.msg)
    }
  }

  def getFlushMode(): Int = flushMode

  def setFlushMode(flush: Int): Unit =
    this.flushMode = flush

  def finish(): Unit = {
    if (compress) {
      val tmp = flushMode
      flushMode = Z_FINISH
      try {
        write("".getBytes(), 0, 0)
      } finally {
        flushMode = tmp
      }
    } else {
      dos.finish()
    }
    super.flush()
  }

  def end(): Unit = synchronized {
    if (ended) return
    if (compress) {
      try { dos.finish() }
      catch { case _: Exception => }
    } else {
      inflater.end()
    }
    ended = true
  }

  override def close(): Unit = {
    try {
      try { finish() }
      catch { case _: IOException => }
    } finally {
      end()
      theOut.close()
      theOut = null
    }
  }

  def getTotalIn(): Long =
    if (compress) dos.getTotalIn() else inflater.total_in

  def getTotalOut(): Long =
    if (compress) dos.getTotalOut() else inflater.total_out

  override def flush(): Unit =
    theOut.flush()
}
