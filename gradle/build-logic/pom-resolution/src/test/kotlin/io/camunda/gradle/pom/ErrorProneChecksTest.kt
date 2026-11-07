/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gradle.pom

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ErrorProneChecksTest {
  @Test
  fun `should extract checks promoted to errors`() {
    // given
    val compilerArgs =
      "-XepDisableAllWarnings -Xep:LockNotBeforeTry:ERROR -Xep:HashCodeInObjectsHash:ERROR"

    // when
    val checks = promotedErrorProneChecks(compilerArgs)

    // then
    assertEquals(listOf("LockNotBeforeTry", "HashCodeInObjectsHash"), checks)
  }

  @Test
  fun `should ignore checks that are not promoted to errors`() {
    // given
    val compilerArgs =
      "-XepDisableAllWarnings -Xep:ObjectsHashCodePrimitive:WARN -Xep:NullAway:OFF " +
        "-Xep:HashCodeInObjectsHash:ERROR"

    // when
    val checks = promotedErrorProneChecks(compilerArgs)

    // then
    assertEquals(listOf("HashCodeInObjectsHash"), checks)
  }

  @Test
  fun `should resolve the checks property before parsing it`() {
    // given
    val pomXml =
      """
      <project>
        <properties>
          <promotedChecks>HashCodeInObjectsHash</promotedChecks>
          <error-prone.checks>
            -XepDisableAllWarnings -Xep:${'$'}{promotedChecks}:ERROR -Xep:LockNotBeforeTry:ERROR
          </error-prone.checks>
        </properties>
      </project>
      """
        .trimIndent()

    // when
    val checks = PomResolver(pomXml).promotedErrorProneChecks()

    // then
    assertEquals(listOf("HashCodeInObjectsHash", "LockNotBeforeTry"), checks)
  }
}
