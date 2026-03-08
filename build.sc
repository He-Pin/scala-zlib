import mill._
import mill.scalalib._
import mill.scalajslib._
import mill.scalanativelib._
import mill.scalalib.scalafmt.ScalafmtModule
import mill.scalalib.publish._

val scalaVersions = Seq("2.13.16", "3.3.7")

/** Shared compiler options and formatting for all modules. */
trait ZlibModule extends ScalaModule with ScalafmtModule {
  def publishVersion = "1.1.5"
  def organization   = "com.github.scala-zlib"

  def scalacOptions = T {
    super.scalacOptions() ++ Seq(
      "-deprecation",
      "-encoding",
      "UTF-8",
      "-feature",
      "-unchecked",
    ) ++ (if (scalaVersion().startsWith("2."))
      Seq("-Xlint", "-Ywarn-dead-code", "-Ywarn-numeric-widen")
    else
      Seq("-Wunused:all", "-source:3.0-migration"))
  }
}

/** Cross-platform core algorithm module — runs on JVM, JS, Native, and WASM.
  * No java.io dependencies; pure byte-array algorithm only.
  */
trait CoreModule extends ZlibModule {
  def artifactName = "scala-zlib-core"

  trait CoreTests extends ScalaTests with TestModule.Munit {
    def ivyDeps = Agg(ivy"org.scalameta::munit::1.0.3")
  }
}

/** JVM-only stream-wrapper module — extends java.io FilterStream classes. */
trait JvmStreamsModule extends ZlibModule {
  def artifactName = "scala-zlib-jvm"

  trait JvmTests extends ScalaTests with TestModule.Munit {
    def ivyDeps = Agg(ivy"org.scalameta::munit::1.0.3")
  }
}

// ─── JVM ────────────────────────────────────────────────────────────────────

object core extends Cross[CoreJvmModule](scalaVersions)
trait CoreJvmModule extends CrossScalaModule with CoreModule {
  object test extends CoreTests
}

object jvm extends Cross[JvmStreamsJvmModule](scalaVersions)
trait JvmStreamsJvmModule extends CrossScalaModule with JvmStreamsModule {
  def moduleDeps = Seq(core(crossScalaVersion))
  object test extends JvmTests {
    def moduleDeps = super.moduleDeps ++ Seq(core(crossScalaVersion))
  }
}

// ─── Scala.js ───────────────────────────────────────────────────────────────

object coreJS extends Cross[CoreJsModule](scalaVersions)
trait CoreJsModule extends CrossScalaModule with CoreModule with ScalaJSModule {
  def scalaJSVersion = "1.17.0"
  object test extends ScalaJSTests with TestModule.Munit {
    def ivyDeps = Agg(ivy"org.scalameta::munit::1.0.3")
  }
}

// ─── Scala Native ───────────────────────────────────────────────────────────

object coreNative extends Cross[CoreNativeModule](scalaVersions)
trait CoreNativeModule extends CrossScalaModule with CoreModule with ScalaNativeModule {
  def scalaNativeVersion = "0.5.5"
  object test extends ScalaNativeTests with TestModule.Munit {
    def ivyDeps = Agg(ivy"org.scalameta::munit::1.0.3")
  }
}
