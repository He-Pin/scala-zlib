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

import java.io.{ IOException, OutputStream }
import JZlib._

class GZIPOutputStream(out: OutputStream, deflater: Deflater, size: Int, close_out: Boolean)
    extends DeflaterOutputStream(out, deflater, size, close_out) {

  def this(out: OutputStream, size: Int, close_out: Boolean) = {
    this(out, new Deflater(Z_DEFAULT_COMPRESSION, 15 + 16), size, close_out)
    mydeflater = true
  }

  def this(out: OutputStream, size: Int) =
    this(out, size, true)

  def this(out: OutputStream) =
    this(out, DeflaterOutputStream.DEFAULT_BUFSIZE)

  private def check(): Unit = {
    if (deflater.dstate.status != 42 /*INIT_STATUS*/ )
      throw new GZIPException("header is already written.")
  }

  def setModifiedTime(mtime: Long): Unit = {
    check()
    deflater.dstate.getGZIPHeader().setModifiedTime(mtime)
  }

  def setOS(os: Int): Unit = {
    check()
    deflater.dstate.getGZIPHeader().setOS(os)
  }

  def setName(name: String): Unit = {
    check()
    deflater.dstate.getGZIPHeader().setName(name)
  }

  def setComment(comment: String): Unit = {
    check()
    deflater.dstate.getGZIPHeader().setComment(comment)
  }

  // Cached after finish() so getCRC() works even after close() clears dstate
  private var _crc: Long = -1L

  override def finish(): Unit = {
    super.finish()
    if (deflater.dstate != null && deflater.dstate.status == 666 /*FINISH_STATE*/ )
      _crc = deflater.dstate.getGZIPHeader().getCRC
  }

  def getCRC(): Long = {
    if (_crc >= 0) return _crc
    if (deflater.dstate == null || deflater.dstate.status != 666 /*FINISH_STATE*/ )
      throw new GZIPException("checksum is not calculated yet.")
    deflater.dstate.getGZIPHeader().getCRC
  }
}
