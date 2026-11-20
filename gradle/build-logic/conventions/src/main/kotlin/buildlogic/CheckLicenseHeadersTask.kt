/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction

/**
 * Fails when a source file does not start with the license header, like the `check` goal of Maven's
 * `license-maven-plugin` with `SLASHSTAR_STYLE`: the header text is rendered as a block comment and
 * must be the first thing in the file.
 *
 * Existing third-party plugins either need the comment syntax pre-rendered in the header file or
 * have no equivalent of Maven's `skipExistingHeaders`, which several modules rely on to keep the
 * original ONF headers of moved code.
 */
@CacheableTask
abstract class CheckLicenseHeadersTask : DefaultTask() {
  /** Plain header text, shared with Maven (`parent/COPYING-HEADER.txt`). */
  @get:InputFile
  @get:PathSensitive(PathSensitivity.NONE)
  abstract val headerFile: RegularFileProperty

  @get:InputFiles
  @get:SkipWhenEmpty
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val sources: ConfigurableFileCollection

  /** Accept any file that already starts with a block comment, as Maven's `skipExistingHeaders`. */
  @get:Input abstract val skipExistingHeaders: Property<Boolean>

  /** Only used to print paths relative to the module. */
  @get:Internal abstract val baseDir: DirectoryProperty

  /** Only gives Gradle an output to cache; written when every file is compliant. */
  @get:OutputFile abstract val marker: RegularFileProperty

  @TaskAction
  fun check() {
    val expected = expectedHeader(headerFile.get().asFile.readLines())
    val skipExisting = skipExistingHeaders.get()
    val missing =
      sources.files
        .filter { file ->
          val lines = file.useLines { it.take(expected.size + 1).toList() }
          !hasHeader(lines, expected, skipExisting)
        }
        .map { it.relativeTo(baseDir.get().asFile).path }
        .sorted()
    if (missing.isNotEmpty()) {
      throw GradleException(
        "Missing or wrong license header in ${missing.size} file(s); " +
          "the header must be the file's first lines (see parent/COPYING-HEADER.txt):\n" +
          missing.joinToString("\n") { "  $it" },
      )
    }
    marker.get().asFile.writeText("Header is present in all files\n")
  }

  internal companion object {
    fun expectedHeader(headerText: List<String>): List<String> =
      listOf("/*") + headerText.map { if (it.isBlank()) " *" else " * $it" } + " */"

    fun hasHeader(
      fileStart: List<String>,
      expected: List<String>,
      skipExistingHeaders: Boolean,
    ): Boolean {
      if (skipExistingHeaders && fileStart.firstOrNull()?.trimStart()?.startsWith("/*") == true) {
        return true
      }
      return fileStart.size >= expected.size &&
        expected.indices.all {
          normalizeYear(fileStart[it].trimEnd()) == normalizeYear(expected[it])
        }
    }

    private val templateYear = Regex("""\$\{year}""")
    private val yearOrRange = Regex("""\b\d{4}(-\d{4})?\b""")

    /**
     * Like Maven's check, the copyright year (`${year}` in templates) matches any year or range.
     */
    private fun normalizeYear(line: String): String =
      line.replace(templateYear, "YEAR").replace(yearOrRange, "YEAR")
  }
}
