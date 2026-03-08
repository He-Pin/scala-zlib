# scala-zlib

[![Scala 2.13](https://img.shields.io/badge/Scala-2.13.16-red.svg)](https://www.scala-lang.org/)
[![Scala 3](https://img.shields.io/badge/Scala-3.3.7-red.svg)](https://www.scala-lang.org/)
[![License: BSD](https://img.shields.io/badge/License-BSD-blue.svg)](LICENSE.txt)
[![CI](https://github.com/He-Pin/scala-zlib/actions/workflows/ci.yml/badge.svg)](https://github.com/He-Pin/scala-zlib/actions)
[![JVM](https://img.shields.io/badge/Platform-JVM-blue.svg)](#platform-support)
[![Scala.js](https://img.shields.io/badge/Platform-Scala.js_1.20.0-blue.svg)](#platform-support)
[![Scala Native](https://img.shields.io/badge/Platform-Scala_Native_0.5.10-blue.svg)](#platform-support)
[![WASM](https://img.shields.io/badge/Platform-WASM_(experimental)-blue.svg)](#platform-support)

**scala-zlib** is a pure Scala port of [jzlib](https://github.com/jruby/jzlib) — a Java re-implementation of zlib (RFC 1950/1951/1952). It provides deflate/inflate compression, GZIP support, streaming I/O wrappers, and checksums (Adler-32 and CRC-32) across all Scala platforms with no native dependencies.

## Why scala-zlib?

| Need | Solution |
|------|----------|
| Compression on Scala.js | ✅ Works — pure Scala algorithm, no JNI |
| Compression on Scala Native | ✅ Works — no system zlib dependency required |
| Compression on WASM | ✅ Works — via Scala.js WASM backend (experimental) |
| `Z_PARTIAL_FLUSH` / `Z_SYNC_FLUSH` | ✅ Full flush-mode support (unlike `java.util.zip`) |
| GZIP read/write | ✅ Built-in GZIPInputStream / GZIPOutputStream |
| Adler-32 / CRC-32 combining | ✅ `adler32_combine` / `crc32_combine` |
| Preset dictionary compression | ✅ Full zlib dictionary support |

## Standards Compliance

scala-zlib fully implements these compression standards:

| Standard | Description | Status |
|----------|-------------|--------|
| [RFC 1950](https://www.rfc-editor.org/rfc/rfc1950) | ZLIB Compressed Data Format | ✅ Full |
| [RFC 1951](https://www.rfc-editor.org/rfc/rfc1951) | DEFLATE Compressed Data Format | ✅ Full |
| [RFC 1952](https://www.rfc-editor.org/rfc/rfc1952) | GZIP File Format | ✅ Full |

The implementation is based on zlib 1.1.3 algorithms via the [jzlib](https://github.com/jruby/jzlib) Java port.

## jruby Compatibility

scala-zlib uses the same package name (`com.jcraft.jzlib`), class names, and method signatures as the original jzlib library used by jruby. It is a binary-compatible drop-in replacement.

## Platform Support

| Feature | JVM | Scala.js | Scala Native | WASM |
|---------|:---:|:--------:|:------------:|:----:|
| Deflate / Inflate | ✅ | ✅ | ✅ | ✅ |
| GZIP format | ✅ | ✅ | ✅ | ✅ |
| Adler-32 / CRC-32 | ✅ | ✅ | ✅ | ✅ |
| Preset dictionary | ✅ | ✅ | ✅ | ✅ |
| DeflaterOutputStream | ✅ | ✅ | ✅ | ✅ |
| InflaterInputStream | ✅ | ✅ | ✅ | ✅ |
| GZIPOutputStream | ✅ | ✅ | ✅ | ✅ |
| GZIPInputStream | ✅ | ✅ | ✅ | ✅ |
| ZOutputStream (legacy) | ✅ | ✅ | ✅ | ✅ |
| ZInputStream (legacy) | ✅ | ✅ | ✅ | ✅ |

**Requirements**: Java 17+ · Scala 2.13.16 or 3.3.7 · Scala.js 1.20.0 · Scala Native 0.5.10

All classes are available on all platforms. Stream classes extend `java.io.FilterInputStream`/`java.io.FilterOutputStream`, which are supported across JVM, Scala.js, Scala Native, and WASM. Scala Native is CI-tested on both Linux and Windows.

## Upstream Project

scala-zlib is a direct Scala port of **jzlib**, originally authored by ymnk at JCraft, Inc. and currently maintained by the JRuby team:

> https://github.com/jruby/jzlib

The upstream source is tracked as a git submodule at `references/jzlib`. Each commit in this repository tracks one upstream jzlib commit, with the upstream SHA recorded in the `References:` section of the commit message.

## Features

- **Deflate / Inflate**: all compression levels (0–9), all flush modes (`Z_NO_FLUSH`, `Z_PARTIAL_FLUSH`, `Z_SYNC_FLUSH`, `Z_FULL_FLUSH`, `Z_FINISH`)
- **GZIP format**: read and write RFC 1952 GZIP streams including headers
- **Streaming API**: `DeflaterOutputStream`, `InflaterInputStream`, `GZIPOutputStream`, `GZIPInputStream` (all platforms)
- **Checksums**: Adler-32 and CRC-32, with combine operations
- **Preset dictionaries**: full support for zlib dictionary-based compression
- **Cross-platform**: JVM, Scala.js, Scala Native, WASM — no JNI, no native dependencies

## Module Structure

```
scala-zlib/
├── core/    All classes — algorithm + stream wrappers — JVM, JS, Native, WASM
└── jvm/     Test-only — JVM interop tests (compares with java.util.zip)
```

- **`core`** — All classes: algorithm (`Deflater`, `Inflater`, `Adler32`, `CRC32`, `GZIPHeader`) and stream wrappers (`DeflaterOutputStream`, `InflaterInputStream`, `GZIPOutputStream`, `GZIPInputStream`). Compiles on all platforms.
- **`jvm`** — Test-only. JVM interop tests that compare scala-zlib output with `java.util.zip`.

## Installation

### Mill

```scala
// All platforms — JVM, JS, Native, WASM
def ivyDeps = Agg(ivy"com.github.scala-zlib::scala-zlib-core::1.1.5")
```

### sbt

```scala
// All platforms — JVM, JS, Native, WASM
libraryDependencies += "com.github.scala-zlib" %%% "scala-zlib-core" % "1.1.5"
```

### Gradle

```gradle
// Gradle (Kotlin DSL)
implementation("com.github.scala-zlib:scala-zlib-core_2.13:1.1.5")
// Or for Scala 3:
implementation("com.github.scala-zlib:scala-zlib-core_3:1.1.5")
```

## Quick Start

### Deflate and Inflate

```scala
import com.jcraft.jzlib._
import JZlib._

// Compress
val deflater = new Deflater
deflater.init(Z_DEFAULT_COMPRESSION)

val input  = "hello, hello!".getBytes
val output = new Array[Byte](1024)

deflater.setInput(input)
deflater.setOutput(output)
deflater.deflate(Z_FINISH)
val compressedLen = deflater.total_out.toInt
deflater.end()

// Decompress
val inflater = new Inflater
inflater.init()

val decompressed = new Array[Byte](1024)
inflater.setInput(output, 0, compressedLen)
inflater.setOutput(decompressed)
inflater.inflate(Z_NO_FLUSH)
val result = new String(decompressed, 0, inflater.total_out.toInt)
inflater.end()

println(result) // hello, hello!
```

### GZIP with Streams

```scala
import com.jcraft.jzlib._
import java.io._

// Write GZIP
val baos = new ByteArrayOutputStream
val gzOut = new GZIPOutputStream(baos)
gzOut.write("hello, world!".getBytes)
gzOut.finish()

// Read GZIP
val gzIn  = new GZIPInputStream(new ByteArrayInputStream(baos.toByteArray))
val bytes = gzIn.readAllBytes()
println(new String(bytes)) // hello, world!
```

### DeflaterOutputStream / InflaterInputStream

```scala
import com.jcraft.jzlib._
import java.io._

val buf    = new ByteArrayOutputStream
val defOut = new DeflaterOutputStream(buf)
defOut.write("hello, scala!".getBytes)
defOut.finish()

val infIn  = new InflaterInputStream(new ByteArrayInputStream(buf.toByteArray))
val result = new String(infIn.readAllBytes())
println(result) // hello, scala!
```

### Checksums

```scala
import com.jcraft.jzlib._

val adler = new Adler32
adler.update("hello".getBytes, 0, 5)
println(adler.getValue)   // Adler-32 checksum

val crc = new CRC32
crc.update("hello".getBytes, 0, 5)
println(crc.getValue)     // CRC-32 checksum

// Combine two independent checksums
val combined = JZlib.adler32_combine(adler1, adler2, len2)
```

### Preset Dictionary

```scala
import com.jcraft.jzlib._
import JZlib._

val dict = "hello, hello!".getBytes

val deflater = new Deflater
deflater.init(Z_DEFAULT_COMPRESSION)
deflater.setDictionary(dict, dict.length)
val dictId = deflater.getAdler

deflater.setInput("hello".getBytes)
deflater.setOutput(new Array[Byte](1024))
deflater.deflate(Z_FINISH)
deflater.end()

// Inflate with the same dictionary
val inflater = new Inflater
inflater.init()
// ... when inflate returns Z_NEED_DICT, supply the dictionary by dictId
```

## Cross-Platform Usage

All classes work on every platform. Here is a low-level example using `Deflater`/`Inflater` directly:

```scala
// Works on all platforms
import com.jcraft.jzlib._
import JZlib._

def compress(data: Array[Byte]): Array[Byte] = {
  val deflater = new Deflater
  deflater.init(Z_DEFAULT_COMPRESSION)
  val buf = new Array[Byte](data.length + 64)
  deflater.setInput(data)
  deflater.setOutput(buf)
  deflater.deflate(Z_FINISH)
  val n = deflater.total_out.toInt
  deflater.end()
  buf.take(n)
}

def decompress(data: Array[Byte], maxLen: Int = 65536): Array[Byte] = {
  val inflater = new Inflater
  inflater.init()
  val buf = new Array[Byte](maxLen)
  inflater.setInput(data)
  inflater.setOutput(buf)
  inflater.inflate(Z_NO_FLUSH)
  val n = inflater.total_out.toInt
  inflater.end()
  buf.take(n)
}
```

## API Overview

### `core` module — all platforms

| Class | Description |
|-------|-------------|
| `JZlib` | Constants (`Z_OK`, `Z_FINISH`, …), `WrapperType` enum, `adler32_combine`, `crc32_combine` |
| `Deflater` | High-level compression. Call `init`, `setInput`, `setOutput`, `deflate`, `end`. |
| `Inflater` | High-level decompression. Call `init`, `setInput`, `setOutput`, `inflate`, `end`. |
| `Adler32` | Adler-32 checksum: `update`, `getValue`, `reset`, `copy`, `combine` |
| `CRC32` | CRC-32 checksum: `update`, `getValue`, `reset`, `copy`, `combine` |
| `GZIPHeader` | GZIP header metadata (OS, filename, comment, modification time) |
| `GZIPException` | Thrown on GZIP format errors (extends `Exception`) |
| `ZStreamException` | Thrown on zlib stream errors (extends `Exception`) |
| `ZStream` | Low-level stream state — **deprecated**, use `Deflater`/`Inflater` instead |
| `Deflate` | Internal deflate algorithm (not part of public API) |
| `Inflate` | Internal inflate algorithm (not part of public API) |

### Stream wrappers (also in `core` — all platforms)

| Class | Description |
|-------|-------------|
| `DeflaterOutputStream` | Compressing `FilterOutputStream` |
| `InflaterInputStream` | Decompressing `FilterInputStream` |
| `GZIPOutputStream` | GZIP-format `DeflaterOutputStream` |
| `GZIPInputStream` | GZIP-format `InflaterInputStream` |
| `ZOutputStream` | Legacy compressing stream — **deprecated**, use `DeflaterOutputStream` |
| `ZInputStream` | Legacy decompressing stream — **deprecated**, use `InflaterInputStream` |

### Utility Functions

```scala
import com.jcraft.jzlib._

// Pre-allocate output buffer
val maxSize = JZlib.deflateBound(inputData.length)
val buffer = new Array[Byte](maxSize.toInt)

// One-shot compression/decompression
val compressed = JZlib.compress(inputData)
val decompressed = JZlib.uncompress(compressed)

// Human-readable error messages
val msg = JZlib.getErrorDescription(JZlib.Z_DATA_ERROR)
// => "invalid or incomplete deflate data"
```

### `JZlib.WrapperType`

| Value | Meaning |
|-------|---------|
| `W_NONE` / `NONE` | Raw deflate (no header) |
| `W_ZLIB` / `ZLIB` | zlib wrapper (RFC 1950) |
| `W_GZIP` / `GZIP` | GZIP wrapper (RFC 1952) |
| `W_ANY` / `ANY` | Auto-detect on inflate |

## Development

### Prerequisites

- **Java 17+** (Java 21 recommended; Java 25 supported; used in CI)
- [Mill](https://mill-build.com/) 1.1.2 (pinned in `.mill-version`)

### Build Commands

```bash
# ── Compile ──────────────────────────────────────────────────────────────────
./mill core[2.13.16].compile         # JVM, Scala 2.13
./mill core[3.3.7].compile           # JVM, Scala 3
./mill jvm[2.13.16].compile          # JVM interop tests module
./mill coreJS[2.13.16].compile       # Scala.js
./mill coreNative[2.13.16].compile   # Scala Native
./mill coreWASM[2.13.16].compile     # WASM (experimental)
./mill __.compile                    # All modules, all versions

# ── Test ─────────────────────────────────────────────────────────────────────
./mill core[2.13.16].test            # JVM
./mill jvm[2.13.16].test             # JVM interop tests
./mill coreJS[2.13.16].test          # Scala.js (requires Node.js)
./mill coreNative[2.13.16].test      # Scala Native (requires Clang/LLVM)
./mill coreWASM[2.13.16].test        # WASM (requires Node.js)
./mill __.test                       # All platforms, all versions

# ── Specific test suite ─────────────────────────────────────────────────────
./mill core[2.13.16].test.testOnly com.jcraft.jzlib.Adler32Suite

# ── Format ───────────────────────────────────────────────────────────────────
./mill mill.scalalib.scalafmt.ScalafmtModule/reformatAll __.sources
./mill mill.scalalib.scalafmt.ScalafmtModule/checkFormatAll __.sources
```

## Migration from Deprecated APIs

### ZStream → Deflater / Inflater

The `ZStream` class is deprecated. Use `Deflater` for compression and `Inflater` for decompression.

**Before (deprecated):**

```scala
val stream = new ZStream()
stream.deflateInit(JZlib.Z_DEFAULT_COMPRESSION)
stream.next_in = inputData
stream.next_in_index = 0
stream.avail_in = inputData.length
stream.next_out = outputData
stream.next_out_index = 0
stream.avail_out = outputData.length
stream.deflate(JZlib.Z_FINISH)
stream.deflateEnd()
```

**After (recommended):**

```scala
val deflater = new Deflater()
deflater.init(JZlib.Z_DEFAULT_COMPRESSION)
deflater.setInput(inputData)
deflater.setOutput(outputData)
deflater.deflate(JZlib.Z_FINISH)
deflater.end()
```

### ZOutputStream → DeflaterOutputStream

**Before (deprecated):**

```scala
val zos = new ZOutputStream(outputStream, JZlib.Z_DEFAULT_COMPRESSION)
zos.write(data)
zos.close()
```

**After (recommended):**

```scala
val dos = new DeflaterOutputStream(outputStream)
dos.write(data)
dos.finish()
dos.close()
```

### ZInputStream → InflaterInputStream

**Before (deprecated):**

```scala
val zis = new ZInputStream(inputStream)
val data = zis.readAllBytes()
zis.close()
```

**After (recommended):**

```scala
val iis = new InflaterInputStream(inputStream)
val data = iis.readAllBytes()
iis.close()
```

### GZIPInputStream: getModifiedtime → getModifiedTime

The `getModifiedtime()` method (lowercase 't') is deprecated. Use `getModifiedTime()` (uppercase 'T') instead.

**Before (deprecated):**

```scala
val gzIn = new GZIPInputStream(inputStream)
val mtime = gzIn.getModifiedtime()
```

**After (recommended):**

```scala
val gzIn = new GZIPInputStream(inputStream)
val mtime = gzIn.getModifiedTime()
```

## Performance

scala-zlib includes a comprehensive JMH benchmark suite covering all public APIs.

### Running Benchmarks

```bash
# Run all benchmarks
./mill bench.runJmh

# Run specific benchmark class
./mill bench.runJmh -- "DeflateInflateBenchmark"

# Quick smoke test (1 iteration)
./mill bench.runJmh -- -wi 1 -i 1 -f 1
```

Performance is comparable to the original Java jzlib implementation since the algorithm code is a faithful port preserving the same data structures and control flow.

## Contributing

Please read [CONTRIBUTING.md](CONTRIBUTING.md) before submitting a pull request.

## License

BSD-style license — same as the original jzlib. See [LICENSE.txt](LICENSE.txt).

## Credits

- **jzlib** originally developed by [ymnk](https://github.com/ymnk) at [JCraft, Inc.](http://www.jcraft.com/jzlib/)
- Currently maintained by the [JRuby team](https://github.com/jruby/jzlib)
- Underlying algorithm by Jean-loup Gailly and Mark Adler ([zlib](https://zlib.net/))
- Scala port by the scala-zlib contributors
