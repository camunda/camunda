/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package buildlogic

import io.camunda.gradle.pom.PomResolver
import java.io.File

/**
 * What Maven's `license-maven-plugin` is configured to check for one module.
 *
 * @property headerFile the header text, relative to the repository root
 * @property skipExistingHeaders accept any file that already starts with a block comment
 */
internal data class MavenLicenseSettings(
  val headerFile: String,
  val skipExistingHeaders: Boolean,
)

private const val MULTI_MODULE_DIR = "\${maven.multiModuleProjectDirectory}/"
private const val DEFAULT_HEADER_FILE = MULTI_MODULE_DIR + "parent/COPYING-HEADER.txt"
private const val MYCILA_TEMPLATES = "com/mycila/maven/plugin/license/templates/"

/**
 * Reads the license settings of [modulePom] the way Maven inherits them: the `license.header.file`
 * property and the plugin's `skipExistingHeaders` setting of the closest POM that declares them,
 * walking up the `<parent>` chain.
 *
 * Maven names Mycila's bundled templates by classpath location, which Gradle cannot read, so those
 * map to the copy under `gradle/license/`.
 *
 * @param readPom returns the text of a POM file, or null when it does not exist
 */
internal fun readMavenLicenseSettings(
  modulePom: File,
  readPom: (File) -> String?,
): MavenLicenseSettings {
  var headerFile: String? = null
  var skipExistingHeaders: Boolean? = null
  var next: File? = modulePom
  while (next != null) {
    val pom: File = next
    val resolver = PomResolver(readPom(pom) ?: break)
    headerFile = headerFile ?: resolver.properties()["license.header.file"]
    skipExistingHeaders =
      skipExistingHeaders ?: resolver.elementText("skipExistingHeaders")?.toBoolean()
    next = resolver.parentRelativePath()?.let { toPomFile(pom.parentFile.resolve(it).normalize()) }
  }
  return MavenLicenseSettings(
    headerFile = headerFileInRepository(headerFile ?: DEFAULT_HEADER_FILE),
    skipExistingHeaders = skipExistingHeaders ?: false,
  )
}

private fun toPomFile(path: File): File = if (path.isDirectory) path.resolve("pom.xml") else path

private fun headerFileInRepository(headerFile: String): String =
  when {
    headerFile.startsWith(MULTI_MODULE_DIR) -> headerFile.removePrefix(MULTI_MODULE_DIR)
    headerFile.startsWith(MYCILA_TEMPLATES) ->
      "gradle/license/" + headerFile.removePrefix(MYCILA_TEMPLATES)
    else -> error("Unsupported license.header.file in the Maven build: $headerFile")
  }
