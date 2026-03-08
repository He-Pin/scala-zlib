# scala-zlib — AI Assistant Guide

## Project Overview

scala-zlib is a pure Scala port of [jzlib](https://github.com/jruby/jzlib), a Java re-implementation of zlib compression (RFC 1950/1951/1952) originally authored by ymnk at JCraft, Inc. It supports **JVM, Scala.js, Scala Native, and WASM** with no native dependencies or JNI.

- **Package**: `com.jcraft.jzlib`
- **Scala versions**: 2.13.x and 3.3.x
- **Build tool**: Mill 0.11.12
- **Test framework**: munit (cross-platform)
- **License**: BSD-style (same as jzlib)

## Architecture

### Module Structure

```
scala-zlib/
├── core/    Cross-platform zlib algorithm — JVM, JS, Native, WASM
│            NO java.io dependencies allowed here.
└── jvm/     JVM-only stream wrappers (extends java.io.Filter* streams)
             Depends on core.
```

### Package: `com.jcraft.jzlib`

Both modules use the same package name. The `core` module provides the algorithm; the `jvm` module provides stream wrappers for the JVM.

### Key Classes

| Class | Module | Description |
|-------|--------|-------------|
| `JZlib` | core | Constants, `WrapperType` enum, `adler32_combine`, `crc32_combine` |
| `ZStream` | core | Low-level stream state — **deprecated**, use `Deflater`/`Inflater` |
| `Deflater` | core | High-level compression API |
| `Inflater` | core | High-level decompression API |
| `Deflate` | core | Internal deflate algorithm (not public API) |
| `Inflate` | core | Internal inflate algorithm (not public API) |
| `InfBlocks` | core | Inflate block decoder (internal) |
| `InfCodes` | core | Inflate code decoder (internal) |
| `InfTree` | core | Inflate Huffman tree builder (internal) |
| `Tree` | core | Huffman tree (internal) |
| `StaticTree` | core | Static Huffman trees (internal) |
| `Adler32` | core | Adler-32 checksum: `update`, `getValue`, `reset`, `copy`, `combine` |
| `CRC32` | core | CRC-32 checksum: `update`, `getValue`, `reset`, `copy`, `combine` |
| `GZIPHeader` | core | GZIP header metadata (OS, filename, comment, mtime) |
| `GZIPException` | core | Thrown on GZIP format errors — extends `Exception`, NOT `IOException` |
| `ZStreamException` | core | Thrown on zlib stream errors — extends `Exception`, NOT `IOException` |
| `DeflaterOutputStream` | jvm | Compressing `FilterOutputStream` |
| `InflaterInputStream` | jvm | Decompressing `FilterInputStream` |
| `GZIPOutputStream` | jvm | GZIP-format `DeflaterOutputStream` |
| `GZIPInputStream` | jvm | GZIP-format `InflaterInputStream` |
| `ZOutputStream` | jvm | Legacy compressing stream — **deprecated** |
| `ZInputStream` | jvm | Legacy decompressing stream — **deprecated** |

## Build System: Mill 0.11.12

### Common Commands

```bash
# Compile
./mill core[2.13.16].compile
./mill core[3.3.7].compile
./mill jvm[2.13.16].compile
./mill __.compile                  # all modules, all Scala versions

# Test
./mill core[2.13.16].test
./mill core[3.3.7].test
./mill jvm[2.13.16].test
./mill __.test                     # all modules, all Scala versions

# Run a specific test suite
./mill core[2.13.16].test.testOnly com.jcraft.jzlib.Adler32Suite

# Format all sources with scalafmt
./mill mill.scalalib.scalafmt.ScalafmtModule/reformatAll __.sources

# Check formatting (what CI runs — fails if unformatted)
./mill mill.scalalib.scalafmt.ScalafmtModule/checkFormatAll __.sources
```

## Upstream Project

- **URL**: https://github.com/jruby/jzlib
- This project tracks jzlib commits one-by-one.
- Every commit in this repository records its upstream jzlib commit SHA in the `References:` section of the commit message.
- To view an upstream commit: `https://github.com/jruby/jzlib/commit/<SHA>`

## Important Notes for AI Assistants

### Algorithm Code Style

The algorithm files (`Deflate`, `Inflate`, `InfBlocks`, `InfCodes`, `InfTree`, `Tree`, `StaticTree`) are **intentionally imperative with mutable state**. Do not refactor them to be functional. This is preserved from the original for three reasons:

1. **Performance** — no allocation overhead in the hot path
2. **Faithfulness** — easy diff against upstream jzlib Java source
3. **Correctness** — any deviation from the original algorithm risks subtle compression bugs

When making changes to algorithm files, stay as close as possible to the corresponding upstream Java code.

### Cross-Platform Constraints

The `core` module MUST NOT use any of the following:

```
java.io.*          (use jvm module for streams)
java.net.*
java.util.zip.*
java.lang.reflect.*
sun.*
com.sun.*
```

Safe to use anywhere in `core`:

```
java.lang.*                          (System.arraycopy, Math.*, Integer.*)
java.nio.charset.StandardCharsets    (available in Scala.js/Native)
scala.*                              (standard library)
```

If you need to add a stream class, it goes in the `jvm` module only.

### Exception Hierarchy

In the Scala port, exceptions are cross-platform:

```scala
// core module — extends Exception (NOT IOException)
class GZIPException(msg: String) extends Exception(msg)
class ZStreamException(msg: String) extends Exception(msg)
```

The original Java jzlib has `GZIPException extends java.io.IOException` and `ZStreamException extends java.io.IOException`. The Scala port changes this to `Exception` for cross-platform compatibility.

JVM stream classes in the `jvm` module catch these and rethrow as `java.io.IOException` at the `java.io` boundary.

### Testing with munit

Tests use [munit](https://scalameta.org/munit/):

```scala
class Adler32Suite extends munit.FunSuite {

  test("compatible with java.util.zip.Adler32") {
    val data = Array.fill[Byte](1024)(42)
    val juza = new java.util.zip.Adler32
    juza.update(data)
    val expected = juza.getValue

    val adler = new Adler32
    adler.update(data, 0, data.length)
    assertEquals(adler.getValue, expected)
  }

  test("combine two checksums") {
    val buf1 = Array.fill[Byte](512)(1)
    val buf2 = Array.fill[Byte](512)(2)
    val a1 = { val a = new Adler32; a.update(buf1, 0, buf1.length); a.getValue }
    val a2 = { val a = new Adler32; a.update(buf2, 0, buf2.length); a.getValue }
    val expected = {
      val a = new Adler32
      a.update(buf1, 0, buf1.length)
      a.update(buf2, 0, buf2.length)
      a.getValue
    }
    assertEquals(Adler32.combine(a1, a2, buf2.length), expected)
  }
}
```

Test files live in `core/test/src/` and `jvm/test/src/`. Use descriptive names in backtick strings:

```scala
test("deflate and inflate with Z_DEFAULT_COMPRESSION") { ... }
test("GZIPInputStream reads concatenated gzip streams") { ... }
```

### Commit Format

Every commit must use this format exactly:

```
Short title (50 chars max)

Motivation:
Why this change was made.

Modification:
What was changed (files, classes, methods).

Result:
The observable effect or outcome.

References:
- https://github.com/jruby/jzlib/commit/<SHA>  (upstream jzlib commit)
- Fixes #42                                     (GitHub issue, if applicable)

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
```

### WrapperType Values

```scala
JZlib.W_NONE  // raw deflate, no header/trailer
JZlib.W_ZLIB  // zlib wrapper (RFC 1950): 2-byte header + Adler-32 trailer
JZlib.W_GZIP  // GZIP wrapper (RFC 1952): GZIP header + CRC-32 trailer
JZlib.W_ANY   // auto-detect on inflate (accepts ZLIB or GZIP)
```

For GZIP with `Deflater`/`Inflater` directly, use the `wbits` convention:

```scala
deflater.init(Z_DEFAULT_COMPRESSION, 15 + 16)  // 15+16 = GZIP
inflater.init(15 + 32)                          // 15+32 = auto-detect ZLIB or GZIP
```

### Flush Modes

| Constant | Value | Meaning |
|----------|-------|---------|
| `Z_NO_FLUSH` | 0 | Normal operation — allow deflate to decide when to flush |
| `Z_PARTIAL_FLUSH` | 1 | Flush pending output (for streaming protocols) |
| `Z_SYNC_FLUSH` | 2 | Flush to byte boundary, aligns for safe splitting |
| `Z_FULL_FLUSH` | 3 | Like SYNC_FLUSH + reset compression state |
| `Z_FINISH` | 4 | Produce final block, must call until `Z_STREAM_END` |

### Return Codes

| Constant | Value | Meaning |
|----------|-------|---------|
| `Z_OK` | 0 | Success |
| `Z_STREAM_END` | 1 | End of compressed data reached |
| `Z_NEED_DICT` | 2 | Preset dictionary required |
| `Z_ERRNO` | -1 | File error |
| `Z_STREAM_ERROR` | -2 | Stream state inconsistency |
| `Z_DATA_ERROR` | -3 | Invalid or corrupted data |
| `Z_MEM_ERROR` | -4 | Insufficient memory |
| `Z_BUF_ERROR` | -5 | No progress possible |
| `Z_VERSION_ERROR` | -6 | Version mismatch |

## Development Workflow

When porting an upstream jzlib commit or adding a feature:

1. Read the upstream jzlib commit at `https://github.com/jruby/jzlib/commit/<SHA>`
2. Translate the Java changes to Scala, placing code in `core/` or `jvm/` as appropriate
3. Update or add tests in munit format
4. Run `./mill mill.scalalib.scalafmt.ScalafmtModule/reformatAll __.sources`
5. Run `./mill __.test` and ensure all tests pass
6. Commit using the format above, including the upstream SHA in `References:`

## File Locations Quick Reference

| What you are looking for | Where to find it |
|--------------------------|------------------|
| Compression constants | `core/src/com/jcraft/jzlib/JZlib.scala` |
| High-level compress API | `core/src/com/jcraft/jzlib/Deflater.scala` |
| High-level decompress API | `core/src/com/jcraft/jzlib/Inflater.scala` |
| Deflate algorithm | `core/src/com/jcraft/jzlib/Deflate.scala` |
| Inflate algorithm | `core/src/com/jcraft/jzlib/Inflate.scala` |
| Adler-32 checksum | `core/src/com/jcraft/jzlib/Adler32.scala` |
| CRC-32 checksum | `core/src/com/jcraft/jzlib/CRC32.scala` |
| GZIP header | `core/src/com/jcraft/jzlib/GZIPHeader.scala` |
| JVM output stream | `jvm/src/com/jcraft/jzlib/DeflaterOutputStream.scala` |
| JVM input stream | `jvm/src/com/jcraft/jzlib/InflaterInputStream.scala` |
| JVM GZIP output | `jvm/src/com/jcraft/jzlib/GZIPOutputStream.scala` |
| JVM GZIP input | `jvm/src/com/jcraft/jzlib/GZIPInputStream.scala` |
| Mill build definition | `build.sc` |
| Scalafmt config | `.scalafmt.conf` |
| CI workflow | `.github/workflows/ci.yml` |
