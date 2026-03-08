# Contributing to scala-zlib

Thank you for your interest in contributing to scala-zlib! This guide covers everything you need to get started, from setting up your development environment to submitting a pull request.

## Development Environment

### Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| **Java** | 17+ (21 recommended) | Build and run JVM tests |
| **Mill** | 0.12.11 (pinned in `.mill-version`) | Build tool |
| **Node.js** | 20+ | Run Scala.js and WASM tests |
| **Clang / LLVM** | System default | Compile Scala Native tests |
| **Git** | Any recent version | Version control |

### 1. Fork and clone

```bash
git clone --recurse-submodules https://github.com/<your-username>/scala-zlib.git
cd scala-zlib
```

The `--recurse-submodules` flag pulls the upstream jzlib source into `references/jzlib`.

### 2. Install Mill

The repository includes a `./mill` launcher script and a `.mill-version` file (0.12.11). On first run, it downloads the correct Mill version automatically:

```bash
./mill version   # Should print 0.12.11
```

Alternatively, install Mill via [coursier](https://get-coursier.io/), Homebrew, or your OS package manager.

### 3. Verify setup

```bash
./mill core[2.13.16].compile   # Should complete without errors
./mill core[2.13.16].test      # Should pass all tests
```

If Mill downloads dependencies on the first run, that is normal.

### Platform-specific setup

- **Scala.js tests**: Require Node.js. Install via `nvm`, Homebrew, or your OS package manager.
- **Scala Native tests**: Require Clang/LLVM. On Ubuntu: `sudo apt-get install clang libstdc++-12-dev zlib1g-dev`. On macOS, Xcode command-line tools provide Clang.
- **WASM tests**: Require Node.js (same as Scala.js). WASM support is experimental via the Scala.js WASM backend.

## Project Structure

```
scala-zlib/
├── build.sc                    # Mill build definition (modules, deps, cross-versions)
├── .scalafmt.conf              # Scalafmt code-formatting configuration
├── .mill-version               # Pins Mill version (0.12.11)
├── README.md                   # Project README
├── CONTRIBUTING.md             # This file
├── CLAUDE.md                   # AI-assistant guide
├── LICENSE.txt                 # BSD-style license
├── ChangeLog                   # Original jzlib changelog
├── references/jzlib/           # Upstream jzlib (git submodule)
├── core/                       # Cross-platform algorithm module (JVM + JS + Native + WASM)
│   ├── src/
│   │   └── com/jcraft/jzlib/
│   │       ├── JZlib.scala     # Constants and WrapperType enum
│   │       ├── ZStream.scala   # Low-level stream state (deprecated)
│   │       ├── Deflater.scala  # High-level compression
│   │       ├── Inflater.scala  # High-level decompression
│   │       ├── Deflate.scala   # Internal deflate algorithm
│   │       ├── Inflate.scala   # Internal inflate algorithm
│   │       ├── InfBlocks.scala # Inflate block decoder
│   │       ├── InfCodes.scala  # Inflate code decoder
│   │       ├── InfTree.scala   # Inflate Huffman tree builder
│   │       ├── Tree.scala      # Huffman tree
│   │       ├── StaticTree.scala# Static Huffman trees
│   │       ├── Adler32.scala   # Adler-32 checksum
│   │       ├── CRC32.scala     # CRC-32 checksum
│   │       ├── GZIPHeader.scala# GZIP header data
│   │       ├── GZIPException.scala     # Exception (extends Exception, NOT IOException)
│   │       └── ZStreamException.scala  # Exception (extends Exception, NOT IOException)
│   └── test/src/               # Cross-platform tests (munit)
├── jvm/                        # JVM-only stream wrappers
│   ├── src/
│   │   └── com/jcraft/jzlib/
│   │       ├── DeflaterOutputStream.scala
│   │       ├── InflaterInputStream.scala
│   │       ├── GZIPOutputStream.scala
│   │       ├── GZIPInputStream.scala
│   │       ├── ZOutputStream.scala     # Deprecated legacy stream
│   │       └── ZInputStream.scala      # Deprecated legacy stream
│   └── test/src/               # JVM-only tests (munit)
└── .github/
    └── workflows/
        └── ci.yml              # GitHub Actions CI configuration
```

### Module architecture

- **`core`** — Pure algorithm (`Deflater`, `Inflater`, `Adler32`, `CRC32`, `GZIPHeader`). NO `java.io` dependencies. Compiles to JVM, Scala.js, Scala Native, and WASM.
- **`jvm`** — `java.io` stream wrappers (`DeflaterOutputStream`, `InflaterInputStream`, etc.). JVM only. Depends on `core`.

## Building and Testing

### Compile

```bash
./mill core[2.13.16].compile         # JVM, Scala 2.13
./mill core[3.3.7].compile           # JVM, Scala 3
./mill jvm[2.13.16].compile          # JVM stream wrappers
./mill coreJS[2.13.16].compile       # Scala.js
./mill coreNative[2.13.16].compile   # Scala Native
./mill coreWASM[2.13.16].compile     # WASM (experimental)
./mill __.compile                    # All modules, all Scala versions
```

### Test

```bash
# ── By platform ──────────────────────────────────────────────────────────────
./mill core[2.13.16].test            # JVM core
./mill jvm[2.13.16].test             # JVM stream wrappers
./mill coreJS[2.13.16].test          # Scala.js (requires Node.js)
./mill coreNative[2.13.16].test      # Scala Native (requires Clang/LLVM)
./mill coreWASM[2.13.16].test        # WASM (requires Node.js)

# ── All platforms, all Scala versions ────────────────────────────────────────
./mill __.test

# ── Specific test suite ─────────────────────────────────────────────────────
./mill core[2.13.16].test.testOnly com.jcraft.jzlib.Adler32Suite
./mill core[3.3.7].test.testOnly com.jcraft.jzlib.Adler32Suite

# ── Specific test by name ───────────────────────────────────────────────────
./mill core[2.13.16].test.testOnly -- com.jcraft.jzlib.Adler32Suite '*combine*'
```

### Format

```bash
# Format all sources
./mill mill.scalalib.scalafmt.ScalafmtModule/reformatAll __.sources

# Check formatting without modifying files (what CI runs)
./mill mill.scalalib.scalafmt.ScalafmtModule/checkFormatAll __.sources
```

CI will fail if the format check fails. **Always run scalafmt before pushing.**

## Development Workflow

### 1. Create a feature branch

```bash
git checkout -b my-feature
```

### 2. Make your changes

Keep changes focused. One logical change per commit.

### 3. Run the tests

```bash
./mill __.test
```

### 4. Format your code

```bash
./mill mill.scalalib.scalafmt.ScalafmtModule/reformatAll __.sources
```

### 5. Verify formatting is clean

```bash
./mill mill.scalalib.scalafmt.ScalafmtModule/checkFormatAll __.sources
```

### 6. Write a commit message (see [Commit Message Format](#commit-message-format))

### 7. Push and open a pull request

```bash
git push origin my-feature
```

Then open a pull request on GitHub. Describe the motivation, what changed, and link to any related issues or upstream commits.

## Commit Message Format

Every commit must follow this format:

```
Short title (50 chars max)

Motivation:
Why this change was made — what problem it solves or what improvement it brings.

Modification:
What was changed — files, classes, methods, logic.

Result:
The observable effect or outcome of this change.

References:
- https://github.com/jruby/jzlib/commit/XXXXXXXX  (upstream jzlib commit, if applicable)
- Fixes #42 (GitHub issue, if applicable)

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
```

### Example

```
Port Adler32.combine from jzlib

Motivation:
The combine operation allows merging two independently computed Adler-32
checksums, which is needed for parallel compression.

Modification:
Added Adler32.combine(adler1: Long, adler2: Long, len2: Long): Long
in core/src/com/jcraft/jzlib/Adler32.scala.

Result:
Users can now combine Adler-32 values without recomputing from scratch.
JZlib.adler32_combine delegates to this method.

References:
- https://github.com/jruby/jzlib/commit/6900f5

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
```

## Tracking Upstream jzlib Commits

scala-zlib tracks [jzlib](https://github.com/jruby/jzlib) one commit at a time. The upstream source is available as a git submodule at `references/jzlib` for easy diffing.

When porting an upstream commit:

1. **Find the upstream commit**
   ```
   https://github.com/jruby/jzlib/commit/<SHA>
   ```
   Or browse the submodule: `references/jzlib/`

2. **Identify which files changed** and locate the corresponding Scala sources in `core/` or `jvm/`.

3. **Translate Java to idiomatic Scala**
   - Use `val` / `var` appropriately (algorithm state is `var`)
   - Replace Java arrays `int[]` → `Array[Int]`, `byte[]` → `Array[Byte]`
   - Preserve the imperative style in algorithm code (see [Code Style](#code-style))
   - Replace `throws IOException` with Scala's unchecked exceptions

4. **Update tests to munit format** (see [Testing](#testing))

5. **Run scalafmt**
   ```bash
   ./mill mill.scalalib.scalafmt.ScalafmtModule/reformatAll __.sources
   ```

6. **Run tests on all platforms**
   ```bash
   ./mill __.test
   ```

7. **Commit with the upstream SHA** in the `References:` section.

## Code Style

### General

- Follow the `.scalafmt.conf` configuration — always run scalafmt before committing.
- Keep the public API close to jzlib for compatibility with existing code that uses jzlib.
- Use `Array[Byte]` and `Array[Int]` (not `List`, `Vector`, `Seq`) for performance-critical data paths.

### Algorithm code — keep it imperative

The algorithm files (`Deflate`, `Inflate`, `InfBlocks`, `InfCodes`, `InfTree`, `Tree`, `StaticTree`) are **intentionally imperative with mutable state**. Do **not** refactor them to functional style. This is preserved from the original for:

1. **Performance** — no allocation overhead in the hot path
2. **Faithfulness** — easy diff against upstream jzlib Java source
3. **Correctness** — any deviation from the original algorithm risks subtle compression bugs

### ScalaDoc

Add ScalaDoc comments to all public API methods and classes:

```scala
/** Compresses data using the deflate algorithm.
  *
  * @param flush one of [[JZlib.Z_NO_FLUSH]], [[JZlib.Z_SYNC_FLUSH]], [[JZlib.Z_FINISH]], etc.
  * @return [[JZlib.Z_OK]], [[JZlib.Z_STREAM_END]], or an error code
  */
def deflate(flush: Int): Int = ...
```

### Cross-Platform Constraints

The `core` module must compile on JVM, Scala.js, Scala Native, and WASM. This means:

**Do NOT use in `core`:**
- `java.io.*` — use the `jvm` module for any stream classes
- `java.net.*`
- `java.util.zip.*`
- `java.lang.reflect.*`
- `sun.*` / `com.sun.*`
- Any reflection or JVM-specific API

**Safe to use in `core`:**
- `java.lang.*` (including `System.arraycopy`, `Math.*`, `Integer.*`)
- `java.nio.charset.StandardCharsets`
- Scala standard library (`scala.*`)

Stream classes (`DeflaterOutputStream`, `InflaterInputStream`, `GZIPOutputStream`, `GZIPInputStream`, `ZOutputStream`, `ZInputStream`) belong in the `jvm` module only.

### Exception Hierarchy

In the Scala port, exceptions are cross-platform and must **not** extend `java.io.IOException`:

```scala
// ✅ Correct — extends Exception for cross-platform compatibility
class GZIPException(msg: String) extends Exception(msg)
class ZStreamException(msg: String) extends Exception(msg)
```

JVM stream classes in the `jvm` module catch these and rethrow as `java.io.IOException` at the `java.io` boundary.

## Testing

### Framework

Tests use [munit](https://scalameta.org/munit/), which is cross-platform (JVM + JS + Native + WASM):

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
}
```

### Test Naming

Use descriptive names for `test(...)`:

```scala
test("deflate and inflate with Z_DEFAULT_COMPRESSION") { ... }
test("combine two Adler-32 checksums") { ... }
test("GZIPInputStream reads concatenated gzip streams") { ... }
```

### Test File Locations

- `core/test/src/` — Cross-platform tests (run on JVM, JS, Native, WASM)
- `jvm/test/src/` — JVM-only tests (stream wrappers)

### Coverage Expectations

- Every public API method should have at least one test.
- Algorithm changes (in `Deflate`, `Inflate`, etc.) should include a round-trip test.
- Bug fixes must include a regression test.

## Pull Request Review

Before your PR can be merged it must:

1. ✅ Compile on all platforms and Scala versions (`./mill __.compile`)
2. ✅ All tests pass (`./mill __.test`)
3. ✅ Scalafmt clean (`./mill mill.scalalib.scalafmt.ScalafmtModule/checkFormatAll __.sources`)
4. ✅ Include tests for new functionality or bug fixes
5. ✅ Follow the commit message format above
6. ✅ Reference the upstream jzlib commit if applicable
7. ✅ Not introduce `java.io` or other JVM-only imports in the `core` module

CI runs format checks, then tests on JVM (Scala 2.13 + 3), Scala.js (Scala 2.13 + 3), and Scala Native (Scala 2.13 + 3) in parallel.

## Questions

Open a GitHub issue or start a discussion. We are happy to help.
