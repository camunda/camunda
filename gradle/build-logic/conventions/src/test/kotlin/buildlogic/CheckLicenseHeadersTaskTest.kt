/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package buildlogic

import buildlogic.CheckLicenseHeadersTask.Companion.expectedHeader
import buildlogic.CheckLicenseHeadersTask.Companion.hasHeader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CheckLicenseHeadersTaskTest {
  private val expected = expectedHeader(listOf("Line one", "", "Line three"))

  @Test
  fun shouldRenderHeaderAsBlockComment() {
    // given - when - then
    assertEquals(listOf("/*", " * Line one", " *", " * Line three", " */"), expected)
  }

  @Test
  fun shouldAcceptFileStartingWithHeader() {
    // given
    val file = expected + "package a;"

    // when - then
    assertTrue(hasHeader(file, expected, skipExistingHeaders = false))
  }

  @Test
  fun shouldRejectFileWithoutHeader() {
    // given
    val file = listOf("package a;", "", "class A {}")

    // when - then
    assertFalse(hasHeader(file, expected, skipExistingHeaders = false))
  }

  @Test
  fun shouldRejectFileWithHeaderNotOnFirstLine() {
    // given
    val file = listOf("") + expected

    // when - then
    assertFalse(hasHeader(file, expected, skipExistingHeaders = false))
  }

  @Test
  fun shouldRejectFileShorterThanHeader() {
    // given
    val file = expected.take(2)

    // when - then
    assertFalse(hasHeader(file, expected, skipExistingHeaders = false))
  }

  @Test
  fun shouldAcceptAnyLeadingBlockCommentWhenSkippingExistingHeaders() {
    // given
    val file = listOf("/*", " * Copyright ONF", " */", "package a;")

    // when - then
    assertTrue(hasHeader(file, expected, skipExistingHeaders = true))
    assertFalse(hasHeader(file, expected, skipExistingHeaders = false))
  }

  @Test
  fun shouldRejectMissingHeaderEvenWhenSkippingExistingHeaders() {
    // given
    val file = listOf("package a;")

    // when - then
    assertFalse(hasHeader(file, expected, skipExistingHeaders = true))
  }

  @Test
  fun shouldMatchAnyYearOfCopyrightTemplate() {
    // given
    val template = expectedHeader(listOf("Copyright © \${year} acme"))

    // when - then
    assertTrue(hasHeader(listOf("/*", " * Copyright © 2017 acme", " */"), template, false))
    assertTrue(
      hasHeader(listOf("/*", " * Copyright © 2017-2021 acme", " */"), template, false),
    )
    assertFalse(hasHeader(listOf("/*", " * Copyright © 2017 other", " */"), template, false))
  }
}
