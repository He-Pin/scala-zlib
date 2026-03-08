# Changelog

All notable changes to scala-zlib will be documented in this file.
This project is a Scala port of [jzlib](https://github.com/jruby/jzlib).

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added
- Cross-platform stream classes — all stream wrappers now available on JVM, Scala.js, Scala Native, and WASM
- `JZlib.deflateBound()` / `compressBound()` — upper bound on compressed size
- `JZlib.compress()` / `uncompress()` — one-shot compression utilities
- `JZlib.getErrorDescription()` — human-readable error messages
- JMH benchmark suite expanded to 30 methods (100% API coverage)
- jruby API compatibility test suite
- Java 25 support via Mill 1.1.2

### Changed
- Upgraded Mill from 0.12.11 to 1.1.2
- Build file renamed from `build.sc` to `build.mill`
- CI: Java 25 now tested natively (not just bytecode compat)

## [1.1.5] - 2026-03-08

### Added
- Pure Scala port of jzlib, supporting JVM, Scala.js, Scala Native, and WASM
- Cross-compilation for Scala 2.13.16 and 3.3.7
- Scala.js 1.20.0 support with WASM experimental backend
- Scala Native 0.5.10 support
- munit test suite with 71 tests across all platforms
- JMH benchmark suite for performance regression testing
- GitHub Actions CI for all platforms (JVM, JS, Native, WASM)
- Comprehensive documentation (README, CONTRIBUTING, CLAUDE.md)

### Changed
- Exception hierarchy: `GZIPException` and `ZStreamException` extend `Exception`
  instead of `java.io.IOException` for cross-platform compatibility
- `WrapperType` implemented as sealed abstract class (was Java enum)
- Java `switch` fall-through translated to while+match state machines
- `do-while` replaced with `while({ body; cond })()` for Scala 3 compatibility

### Upstream jzlib changes incorporated
- All 65 upstream jzlib commits through 1.1.5 are incorporated
- Thread-safe `gen_codes()` (upstream 26fa95d)
- `Tree.d_code` ArrayIndexOutOfBoundsException fix (upstream 8b205d6)
- Performance-optimized `Adler32.update()` with NMAX batching (upstream 6900f5d)
- `l_buf` as separate byte array for better cache locality (upstream e2fee8f)
- `GZIPInputStream.getModifiedTime()` with deprecated `getModifiedtime()` (upstream 1a5fb10)
- Format auto-detection via `W_ANY` / `DEF_WBITS+32` (upstream 99d6334, 389af32)
- Concatenated gzip stream revert matches upstream final state (upstream e39056b)

## Upstream History

For changes prior to the Scala port, see the original
[jzlib ChangeLog](https://github.com/jruby/jzlib/blob/master/ChangeLog).
