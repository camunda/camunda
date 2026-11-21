/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package buildlogic

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class MavenLicenseSettingsTest {
  @TempDir lateinit var root: File

  private fun pom(path: String, parent: String? = null, body: String = ""): File {
    val file = root.resolve(path)
    file.parentFile.mkdirs()
    val parentXml = parent?.let { "<parent><relativePath>$it</relativePath></parent>" } ?: ""
    file.writeText("<project>$parentXml$body</project>")
    return file
  }

  private fun settings(module: File) =
    readMavenLicenseSettings(module) { pom -> pom.takeIf { it.isFile }?.readText() }

  private fun header(value: String) =
    "<properties><license.header.file>$value</license.header.file></properties>"

  private val skipExisting =
    "<build><plugins><plugin><configuration>" +
      "<skipExistingHeaders>true</skipExistingHeaders>" +
      "</configuration></plugin></plugins></build>"

  @Test
  fun `should use the Camunda header when no POM overrides it`() {
    // given
    pom("module/pom.xml")

    // when
    val settings = settings(root.resolve("module/pom.xml"))

    // then
    assertEquals("parent/COPYING-HEADER.txt", settings.headerFile)
    assertFalse(settings.skipExistingHeaders)
  }

  @Test
  fun `should map a Mycila template to the copy in the repository`() {
    // given
    pom(
      "module/pom.xml",
      body = header("com/mycila/maven/plugin/license/templates/APACHE-2.txt"),
    )

    // when
    val settings = settings(root.resolve("module/pom.xml"))

    // then
    assertEquals("gradle/license/APACHE-2.txt", settings.headerFile)
  }

  @Test
  fun `should inherit settings from the parent chain`() {
    // given
    pom(
      "group/pom.xml",
      body = header("com/mycila/maven/plugin/license/templates/APACHE-2.txt") + skipExisting,
    )
    pom("group/module/pom.xml", parent = "../pom.xml")

    // when
    val settings = settings(root.resolve("group/module/pom.xml"))

    // then
    assertEquals("gradle/license/APACHE-2.txt", settings.headerFile)
    assertTrue(settings.skipExistingHeaders)
  }

  @Test
  fun `should prefer the closest POM over its parent`() {
    // given
    pom(
      "group/pom.xml",
      body = header("com/mycila/maven/plugin/license/templates/APACHE-2.txt"),
    )
    pom(
      "group/module/pom.xml",
      parent = "../pom.xml",
      body = header("\${maven.multiModuleProjectDirectory}/parent/COPYING-HEADER.txt"),
    )

    // when
    val settings = settings(root.resolve("group/module/pom.xml"))

    // then
    assertEquals("parent/COPYING-HEADER.txt", settings.headerFile)
  }

  @Test
  fun `should not check a module that pins a plugin too old for licenseSets`() {
    // given
    pom(
      "group/pom.xml",
      body = "<properties><plugin.version.license>5.1.2</plugin.version.license></properties>",
    )
    pom(
      "group/module/pom.xml",
      parent = "../pom.xml",
      body = "<properties><plugin.version.license>3.0</plugin.version.license></properties>",
    )
    pom("group/other/pom.xml", parent = "../pom.xml")

    // when
    val pinned = settings(root.resolve("group/module/pom.xml"))
    val inherited = settings(root.resolve("group/other/pom.xml"))

    // then
    assertFalse(pinned.checked)
    assertTrue(inherited.checked)
  }

  @Test
  fun `should reject a header file it cannot map`() {
    // given
    pom("module/pom.xml", body = header("somewhere/else.txt"))

    // when / then
    assertThrows(IllegalStateException::class.java) {
      settings(root.resolve("module/pom.xml"))
    }
  }
}
