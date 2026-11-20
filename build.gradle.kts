plugins {
  // Keep versions centralized here for a small repo.
  // Compose Multiplatform: https://github.com/JetBrains/compose-multiplatform
  kotlin("multiplatform") version "2.4.20" apply false
  kotlin("android") version "2.4.20" apply false
  id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
  id("org.jetbrains.compose") version "1.9.3" apply false
  id("com.android.application") version "8.13.2" apply false
  id("com.android.library") version "8.13.2" apply false
  kotlin("plugin.serialization") version "2.4.20" apply false
}

tasks.register("verifyNoAppLogging") {
  group = "verification"
  description = "Rejects application logging APIs in Android app source sets."

  doLast {
    val sourceRoots = listOf(
      file("androidApp/src"),
      file("shared/src/androidMain"),
    )
    val forbiddenPatterns = listOf(
      Regex("""\bAppLog\s*\."""),
      Regex("""\bandroid\.util\.Log\b"""),
      Regex("""\bLog\s*\.\s*(d|i|w|e|v|wtf)\s*\("""),
      Regex("""\bCrashFileLogger\b"""),
      Regex("""\bprintln\s*\("""),
      Regex("""\bSystem\s*\.\s*(out|err)\b"""),
    )
    val offenders = sourceRoots
      .asSequence()
      .filter { it.exists() }
      .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" } }
      .filterNot { source ->
        val path = source.relativeTo(projectDir).invariantSeparatorsPath
        path.contains("/androidTest/")
      }
      .flatMap { source ->
        source.readLines().asSequence().mapIndexedNotNull { index, line ->
          if (forbiddenPatterns.any { it.containsMatchIn(line) }) {
            "${source.relativeTo(projectDir)}:${index + 1}: ${line.trim()}"
          } else {
            null
          }
        }
      }
      .toList()

    if (offenders.isNotEmpty()) {
      throw GradleException(
        buildString {
          appendLine("Application logging is forbidden:")
          offenders.forEach { appendLine(it) }
        },
      )
    }
  }
}
