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
- **Upstream zlib**: https://github.com/madler/zlib (git submodule at `references/zlib`) — bug fixes and improvements from zlib 1.2.x–1.3.x have been ported

## Architecture

### Module Structure

```
scala-zlib/
├── core/      All classes — algorithm + stream wrappers — JVM, JS, Native, WASM
├── jvm/       Test-only — JVM interop tests (compares with java.util.zip)
├── gzip/      CLI gzip compressor (JVM) — com.jcraft.jzlib.cli.GzipMain
├── gunzip/    CLI gunzip decompressor (JVM) — com.jcraft.jzlib.cli.GunzipMain
└── pigz/      CLI parallel gzip compressor (JVM) — com.jcraft.jzlib.cli.PigzMain
```

### Package: `com.jcraft.jzlib`

The `core` module provides all classes (algorithm + stream wrappers). The `jvm` module contains only JVM interop tests.

### Key Classes

| Class | Module | Description |
|-------|--------|-------------|
| `JZlib` | core | Constants (`Z_RLE`, `Z_FIXED`, etc.), `WrapperType` enum, `adler32_combine`, `crc32_combine`, `deflateBound`, `compressBound`, `compress`, `uncompress`, `uncompress2`, `getErrorDescription` |
| `ZStream` | core | Low-level stream state — **deprecated**, use `Deflater`/`Inflater` |
| `Deflater` | core | High-level compression API — `AutoCloseable`, `toString`, `getDictionary`, companion factories (`Deflater()`, `Deflater.gzip()`) |
| `Inflater` | core | High-level decompression API — `AutoCloseable`, `toString`, `getDictionary`, companion factories (`Inflater()`, `Inflater.auto()`, `Inflater.gzip()`) |
| `Deflate` | core | Internal deflate algorithm (not public API) |
| `Inflate` | core | Internal inflate algorithm (not public API) |
| `InfBlocks` | core | Inflate block decoder (internal) |
| `InfCodes` | core | Inflate code decoder (internal) |
| `InfTree` | core | Inflate Huffman tree builder (internal) |
| `Tree` | core | Huffman tree (internal) |
| `StaticTree` | core | Static Huffman trees (internal) |
| `Adler32` | core | Adler-32 checksum: `update`, `update(b: Int)`, `getValue`, `reset`, `copy`, `combine` |
| `CRC32` | core | CRC-32 checksum: `update`, `update(b: Int)`, `getValue`, `reset`, `copy`, `combine`, `combineGen`, `combineOp` (slicing-by-8) |
| `GZIPHeader` | core | GZIP header metadata (OS, filename, comment, mtime); companion object with `detectOS()` for OS auto-detection |
| `GZIPException` | core | Thrown on GZIP format errors — extends `Exception`, NOT `IOException` |
| `ZStreamException` | core | Thrown on zlib stream errors — extends `Exception`, NOT `IOException` |
| `DeflaterOutputStream` | core | Compressing `FilterOutputStream` |
| `InflaterInputStream` | core | Decompressing `FilterInputStream` |
| `GZIPOutputStream` | core | GZIP-format `DeflaterOutputStream` |
| `GZIPInputStream` | core | GZIP-format `InflaterInputStream` — supports concatenated/multi-member streams |
| `ZOutputStream` | core | Legacy compressing stream — **deprecated** |
| `ZInputStream` | core | Legacy decompressing stream — **deprecated** |
| `GzipMain` | gzip | CLI gzip compressor — GNU gzip-compatible, all standard flags |
| `GunzipMain` | gunzip | CLI gunzip decompressor — GNU gunzip-compatible, all standard flags |
| `PigzMain` | pigz | CLI parallel gzip compressor — multi-threaded block compression |

## Build System: Mill 1.1.2

### Mill Modules

| Module | Platforms | Description |
|--------|-----------|-------------|
| `core` | JVM | All classes — algorithm + stream wrappers (JVM build) |
| `coreJS` | Scala.js | All classes — algorithm + stream wrappers (JS build) |
| `coreNative` | Scala Native | All classes — algorithm + stream wrappers (Native build) |
| `coreWASM` | WASM | All classes — algorithm + stream wrappers (WASM build, experimental) |
| `jvm` | JVM | Test-only — JVM interop tests (compares with java.util.zip) |
| `gzip` | JVM | CLI gzip compressor (JVM) |
| `gunzip` | JVM | CLI gunzip decompressor (JVM) |
| `pigz` | JVM | CLI parallel gzip compressor (JVM) |
| `gzipNative` | Scala Native | CLI gzip compressor (native binary) |
| `gunzipNative` | Scala Native | CLI gunzip decompressor (native binary) |
| `pigzNative` | Scala Native | CLI parallel gzip compressor (native binary) |

### Source Sharing Architecture

All platform modules share the same source code from `core/src/`:

```scala
// In build.mill — coreNative, coreJS, coreWASM all use:
override def sources = core(crossScalaVersion).sources
```

- `core[*]` → JVM (canonical source directory: `core/src/`)
- `coreNative[*]` → Scala Native (shares `core/src/` via `override def sources`)
- `coreJS[*]` → Scala.js (shares `core/src/` via `override def sources`)
- `coreWASM[*]` → WASM (shares `core/src/` via `override def sources`)

Similarly, native CLI tools share their JVM counterpart's sources:
```scala
// gzipNative, gunzipNative, pigzNative use:
def sources = gzip.sources  // (gunzip.sources, pigz.sources respectively)
```

**Important**: Do NOT create separate source directories like `coreNative/src/` or `coreJS/src/`. All platform modules compile from `core/src/`.

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

# ── CLI Tools ────────────────────────────────────────────────────────────────
./mill gzip.compile                          # Compile gzip CLI (JVM)
./mill gunzip.compile                        # Compile gunzip CLI (JVM)
./mill pigz.compile                          # Compile pigz CLI (JVM)
./mill gzip.run -- -kv file.txt              # Run gzip CLI on JVM
./mill gunzip.run -- file.txt.gz             # Run gunzip CLI on JVM
./mill pigz.run -- -p 4 file.txt             # Run pigz CLI on JVM (4 threads)
./mill gzipNative[2.13.16].nativeLink        # Build native gzip binary
./mill gunzipNative[2.13.16].nativeLink      # Build native gunzip binary
./mill pigzNative[2.13.16].nativeLink        # Build native pigz binary
```

## Upstream Projects

- **jzlib URL**: https://github.com/jruby/jzlib
- **jzlib submodule**: `references/jzlib` (for easy diffing against upstream)
- **madler/zlib URL**: https://github.com/madler/zlib
- **madler/zlib submodule**: `references/zlib` (for diffing against original C zlib)
- This project tracks jzlib commits one-by-one, and selectively ports improvements from madler/zlib.
- Every commit in this repository records its upstream commit SHA in the `References:` section of the commit message.
- To view an upstream jzlib commit: `https://github.com/jruby/jzlib/commit/<SHA>`
- To view an upstream zlib commit: `https://github.com/madler/zlib/commit/<SHA>`

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

**Test source sharing caveat**: Test sources in `core/test/src/` use `java.util.zip.CRC32` and `java.util.zip.Adler32` for JVM interop verification. These CANNOT be shared with JS/WASM/Native test modules. Only the main library sources (`core/src/`) are shared across platforms — test sources remain JVM-only.

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
| `JZlib.deflateBound(sourceLen)` | Upper bound on compressed size for given input length (improved accuracy from madler/zlib) |
| `JZlib.compressBound(sourceLen)` | Alias for `deflateBound` |
| `JZlib.compress(data)` | One-shot compression (returns compressed byte array) |
| `JZlib.uncompress(data)` | One-shot decompression (returns decompressed byte array) |
| `JZlib.gzip(data)` | One-shot GZIP compression (returns GZIP-wrapped byte array) |
| `JZlib.gunzip(data)` | One-shot GZIP decompression (returns decompressed byte array) |
| `JZlib.compressBound(sourceLen, level)` | Upper bound on compressed size for given input length and compression level |
| `JZlib.uncompress2(data)` | One-shot decompression returning `UncompressResult(data, inputBytesUsed)` |
| `JZlib.getErrorDescription(code)` | Human-readable error message for a zlib return code |
| `Deflater.getDictionary(dict, len)` | Retrieve the current sliding window dictionary from an active deflater |
| `Inflater.getDictionary(dict, len)` | Retrieve the current dictionary from an active inflater |
| `CRC32.combineGen(len2)` | Pre-compute an operator for repeated CRC-32 combine with fixed length |
| `CRC32.combineOp(op, crc1, crc2)` | Apply a pre-computed combine operator to two CRC-32 values |

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
| `CRC32Suite` | core | CRC-32 checksum correctness, combine, `combineGen`/`combineOp`, and slicing-by-8 |
| `CompanionObjectSuite` | core | Companion object factory methods for `Deflater` and `Inflater` |
| `DeflateGetDictionarySuite` | core | `deflateGetDictionary` round-trip and edge cases |
| `DeflateInflateSuite` | core | Round-trip deflate/inflate across levels, strategies, and wrapper types |
| `ErrorDescriptionSuite` | core | `getErrorDescription` return values for all return codes |
| `GZIPMultiMemberSuite` | core | Concatenated/multi-member GZIP stream reading |
| `InflateGetDictionarySuite` | core | `inflateGetDictionary` round-trip and edge cases |
| `InputValidationSuite` | core | Null checks, negative lengths, state validation, `windowBits=8` handling |
| `JZlibUtilSuite` | core | `deflateBound`, `compressBound`, `compress`, `uncompress`, `uncompress2` |
| `StrategyConstantsSuite` | core | `Z_RLE` and `Z_FIXED` strategy constants |
| `StreamEdgeCaseSuite` | core | Stream edge cases — close idempotency, empty input, syncFlush, and more (7 tests) |
| `WrapperTypeSuite` | core | Wrapper type edge cases |
| `DeflaterInflaterStreamSuite` | jvm | JVM `DeflaterOutputStream` / `InflaterInputStream` interop with `java.util.zip` |
| `GZIPIOStreamSuite` | jvm | JVM GZIP stream interop with `java.util.zip` |
| `JRubyCompatSuite` | jvm | API compatibility with upstream jruby/jzlib |
| `ZIOStreamSuite` | jvm | Legacy `ZOutputStream` / `ZInputStream` interop |
| `CLIIntegrationSuite` | jvm | CLI-style GZIP round-trip, parallel blocks, compression levels, OS header |

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
| CLI gzip compressor | `gzip/src/com/jcraft/jzlib/cli/GzipMain.scala` |
| CLI gunzip decompressor | `gunzip/src/com/jcraft/jzlib/cli/GunzipMain.scala` |
| CLI parallel gzip (pigz) | `pigz/src/com/jcraft/jzlib/cli/PigzMain.scala` |
| Upstream jzlib source | `references/jzlib/` (git submodule) |
| Upstream zlib (madler) source | `references/zlib/` (git submodule) |
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
