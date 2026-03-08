# scala-zlib — AI Assistant Guide

## Project Overview

scala-zlib is a pure Scala port of [jzlib](https://github.com/jruby/jzlib), a Java re-implementation of zlib compression (RFC 1950/1951/1952) originally authored by ymnk at JCraft, Inc. It supports **JVM, Scala.js 1.20.0, Scala Native 0.5.10, and WASM** (experimental via Scala.js) with no native dependencies or JNI.

- **Package**: `com.jcraft.jzlib`
- **Scala versions**: 2.13.16 and 3.3.7
- **Build tool**: Mill 1.1.2 (pinned in `.mill-version`)
- **Test framework**: munit (cross-platform)
- **Java**: 17+ (21 recommended, 25 supported)
- **License**: BSD-style (same as jzlib)
- **Upstream**: https://github.com/jruby/jzlib (git submodule at `references/jzlib`)

## Architecture

### Module Structure

```
scala-zlib/
├── core/    All classes — algorithm + stream wrappers — JVM, JS, Native, WASM
└── jvm/     Test-only — JVM interop tests (compares with java.util.zip)
```

### Package: `com.jcraft.jzlib`

The `core` module provides all classes (algorithm + stream wrappers). The `jvm` module contains only JVM interop tests.

### Key Classes

| Class | Module | Description |
|-------|--------|-------------|
| `JZlib` | core | Constants (`Z_RLE`, `Z_FIXED`, etc.), `WrapperType` enum, `adler32_combine`, `crc32_combine`, `deflateBound`, `compressBound`, `compress`, `uncompress`, `getErrorDescription` |
| `ZStream` | core | Low-level stream state — **deprecated**, use `Deflater`/`Inflater` |
| `Deflater` | core | High-level compression API — `AutoCloseable`, `toString`, companion factories (`Deflater()`, `Deflater.gzip()`) |
| `Inflater` | core | High-level decompression API — `AutoCloseable`, `toString`, companion factories (`Inflater()`, `Inflater.auto()`, `Inflater.gzip()`) |
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
| `DeflaterOutputStream` | core | Compressing `FilterOutputStream` |
| `InflaterInputStream` | core | Decompressing `FilterInputStream` |
| `GZIPOutputStream` | core | GZIP-format `DeflaterOutputStream` |
| `GZIPInputStream` | core | GZIP-format `InflaterInputStream` — supports concatenated/multi-member streams |
| `ZOutputStream` | core | Legacy compressing stream — **deprecated** |
| `ZInputStream` | core | Legacy decompressing stream — **deprecated** |

## Build System: Mill 1.1.2

### Mill Modules

| Module | Platforms | Description |
|--------|-----------|-------------|
| `core` | JVM | All classes — algorithm + stream wrappers (JVM build) |
| `coreJS` | Scala.js | All classes — algorithm + stream wrappers (JS build) |
| `coreNative` | Scala Native | All classes — algorithm + stream wrappers (Native build) |
| `coreWASM` | WASM | All classes — algorithm + stream wrappers (WASM build, experimental) |
| `jvm` | JVM | Test-only — JVM interop tests (compares with java.util.zip) |

### Common Commands

```bash
# ── Compile ──────────────────────────────────────────────────────────────────
./mill core[2.13.16].compile         # JVM, Scala 2.13
./mill core[3.3.7].compile           # JVM, Scala 3
./mill jvm[2.13.16].compile          # JVM interop tests module
./mill coreJS[2.13.16].compile       # Scala.js
./mill coreJS[3.3.7].compile         # Scala.js, Scala 3
./mill coreNative[2.13.16].compile   # Scala Native
./mill coreNative[3.3.7].compile     # Scala Native, Scala 3
./mill coreWASM[2.13.16].compile     # WASM (experimental)
./mill coreWASM[3.3.7].compile       # WASM, Scala 3
./mill __.compile                    # All modules, all Scala versions

# ── Test ─────────────────────────────────────────────────────────────────────
./mill core[2.13.16].test            # JVM core
./mill core[3.3.7].test              # JVM core, Scala 3
./mill jvm[2.13.16].test             # JVM interop tests
./mill jvm[3.3.7].test               # JVM interop tests, Scala 3
./mill coreJS[2.13.16].test          # Scala.js (requires Node.js)
./mill coreNative[2.13.16].test      # Scala Native (requires Clang/LLVM)
./mill coreWASM[2.13.16].test        # WASM (requires Node.js)
./mill __.test                       # All modules, all Scala versions

# ── Specific test suite ─────────────────────────────────────────────────────
./mill core[2.13.16].test.testOnly com.jcraft.jzlib.Adler32Suite

# ── Format ───────────────────────────────────────────────────────────────────
./mill mill.scalalib.scalafmt.ScalafmtModule/reformatAll __.sources
./mill mill.scalalib.scalafmt.ScalafmtModule/checkFormatAll __.sources
```

## Upstream Project

- **URL**: https://github.com/jruby/jzlib
- **Submodule**: `references/jzlib` (for easy diffing against upstream)
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
java.io.FilterInputStream           (available on all platforms)
java.io.FilterOutputStream          (available on all platforms)
java.io.IOException                 (available on all platforms)
scala.*                              (standard library)
```

### Exception Hierarchy

In the Scala port, exceptions are cross-platform:

```scala
// core module — extends Exception (NOT IOException)
class GZIPException(msg: String) extends Exception(msg)
class ZStreamException(msg: String) extends Exception(msg)
```

The original Java jzlib has `GZIPException extends java.io.IOException` and `ZStreamException extends java.io.IOException`. The Scala port changes this to `Exception` for cross-platform compatibility.

JVM stream classes in the `core` module catch these and rethrow as `java.io.IOException` at the `java.io` boundary.

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

Test files live in `core/test/src/` and `jvm/test/src/`. Use descriptive names:

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

Use `JZlib.getErrorDescription(code)` to get a human-readable error message for any return code.

### Utility Functions

| Function | Description |
|----------|-------------|
| `JZlib.deflateBound(sourceLen)` | Upper bound on compressed size for given input length |
| `JZlib.compressBound(sourceLen)` | Alias for `deflateBound` |
| `JZlib.compress(data)` | One-shot compression (returns compressed byte array) |
| `JZlib.uncompress(data)` | One-shot decompression (returns decompressed byte array) |
| `JZlib.getErrorDescription(code)` | Human-readable error message for a zlib return code |

### Companion Object Factories

Convenience factories that combine construction and initialization into a single call:

| Factory | Description |
|---------|-------------|
| `Deflater()` | Default compression with zlib wrapping |
| `Deflater(level)` | Specified compression level with zlib wrapping |
| `Deflater(level, wrapperType)` | Full control with wrapper type selection |
| `Deflater.gzip(level)` | GZIP output (default compression if level omitted) |
| `Inflater()` | Default zlib-wrapped input |
| `Inflater(wrapperType)` | Specified wrapper type |
| `Inflater.gzip()` | GZIP input decompression |
| `Inflater.auto()` | Auto-detects ZLIB or GZIP format |

### AutoCloseable / Resource Management

`Deflater` and `Inflater` implement `AutoCloseable`. The `close()` method delegates to `end()`, enabling:

```scala
import scala.util.Using

Using(Deflater(JZlib.Z_DEFAULT_COMPRESSION)) { deflater =>
  deflater.setInput(data)
  deflater.setOutput(output)
  deflater.deflate(JZlib.Z_FINISH)
}
```

### Performance Annotations

Hot-path methods in `Deflate.scala` are annotated with `@inline`:
- `smaller`, `put_byte`, `put_short`, `send_code`, `bi_flush`

### Strategy Constants

| Constant | Value | Description |
|----------|-------|-------------|
| `Z_DEFAULT_STRATEGY` | 0 | Default — balanced compression |
| `Z_FILTERED` | 1 | Optimized for data produced by a filter (or predictor) |
| `Z_HUFFMAN_ONLY` | 2 | Huffman encoding only, no string matching |
| `Z_RLE` | 3 | Run-length encoding — fast, good for images with many repeated bytes |
| `Z_FIXED` | 4 | Use fixed Huffman codes — prevents dynamic tree generation |

### Test Suites

| Suite | Module | Description |
|-------|--------|-------------|
| `Adler32Suite` | core | Adler-32 checksum correctness and combine |
| `CRC32Suite` | core | CRC-32 checksum correctness and combine |
| `CompanionObjectSuite` | core | Companion object factory methods for `Deflater` and `Inflater` |
| `DeflateInflateSuite` | core | Round-trip deflate/inflate across levels, strategies, and wrapper types |
| `ErrorDescriptionSuite` | core | `getErrorDescription` return values for all return codes |
| `GZIPMultiMemberSuite` | core | Concatenated/multi-member GZIP stream reading |
| `JZlibUtilSuite` | core | `deflateBound`, `compressBound`, `compress`, `uncompress` |
| `StrategyConstantsSuite` | core | `Z_RLE` and `Z_FIXED` strategy constants |
| `WrapperTypeSuite` | core | Wrapper type edge cases |
| `DeflaterInflaterStreamSuite` | jvm | JVM `DeflaterOutputStream` / `InflaterInputStream` interop with `java.util.zip` |
| `GZIPIOStreamSuite` | jvm | JVM GZIP stream interop with `java.util.zip` |
| `JRubyCompatSuite` | jvm | API compatibility with upstream jruby/jzlib |
| `ZIOStreamSuite` | jvm | Legacy `ZOutputStream` / `ZInputStream` interop |

## Development Workflow

When porting an upstream jzlib commit or adding a feature:

1. Read the upstream jzlib commit at `https://github.com/jruby/jzlib/commit/<SHA>`
2. Translate the Java changes to Scala, placing code in `core/`
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
| Output stream | `core/src/com/jcraft/jzlib/DeflaterOutputStream.scala` |
| Input stream | `core/src/com/jcraft/jzlib/InflaterInputStream.scala` |
| GZIP output | `core/src/com/jcraft/jzlib/GZIPOutputStream.scala` |
| GZIP input | `core/src/com/jcraft/jzlib/GZIPInputStream.scala` |
| Upstream jzlib source | `references/jzlib/` (git submodule) |
| Mill build definition | `build.mill` |
| Mill version | `.mill-version` (1.1.2) |
| Scalafmt config | `.scalafmt.conf` |
| CI workflow | `.github/workflows/ci.yml` |
| Release workflow | `.github/workflows/release.yml` |

## CI / Release

- **CI** tests: format check, JVM (Java 17/21/25), Scala.js, Scala Native (Linux + Windows), WASM
- **Windows Scala Native** is CI-tested on every push/PR
- **Caching**: platform and Scala version specific cache keys for Mill and Coursier
- **Release workflow**: publishes to Maven Central and uploads JAR/source/doc artifacts to GitHub Releases
