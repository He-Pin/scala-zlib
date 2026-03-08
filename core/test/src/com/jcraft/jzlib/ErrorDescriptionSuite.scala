package com.jcraft.jzlib

class ErrorDescriptionSuite extends munit.FunSuite {
  test("getErrorDescription returns meaningful messages") {
    assert(JZlib.getErrorDescription(JZlib.Z_OK).contains("OK"))
    assert(JZlib.getErrorDescription(JZlib.Z_STREAM_END).contains("end"))
    assert(JZlib.getErrorDescription(JZlib.Z_NEED_DICT).contains("dictionary"))
    assert(JZlib.getErrorDescription(JZlib.Z_DATA_ERROR).contains("corrupted"))
    assert(JZlib.getErrorDescription(JZlib.Z_BUF_ERROR).contains("progress"))
    assert(JZlib.getErrorDescription(999).contains("Unknown"))
  }
}
