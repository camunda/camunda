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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class SpotBugsEngineVersionTest {
  @Test
  fun `should drop the plugin patch level to get the engine version`() {
    // when
    val engine = spotbugsEngineVersion("4.10.4.1")

    // then
    assertEquals("4.10.4", engine)
  }

  @Test
  fun `should reject a plugin version that is not x_y_z_w`() {
    // when / then
    listOf("4.10.4", "4.10", "4.10.4.1-SNAPSHOT", "4.10.4.x").forEach {
      assertThrows(IllegalStateException::class.java) { spotbugsEngineVersion(it) }
    }
  }

  @Test
  fun `should derive an engine version from the plugin version in parent pom`() {
    // given tests run in gradle/build-logic/conventions
    val parentPom = File("../../../parent/pom.xml").readText()

    // when
    val pluginVersion = PomResolver(parentPom).properties().getValue("plugin.version.spotbugs")

    // then
    spotbugsEngineVersion(pluginVersion)
  }
}
