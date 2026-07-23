/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OidcClaimExtractorTest {

  @Test
  void shouldReturnExtractedValueOnHappyPath() {
    assertThat(OidcClaimExtractor.extractOrFallback(() -> "value", "fallback", "test"))
        .isEqualTo("value");
  }

  @Test
  void shouldReturnFallbackWhenExtractionThrowsIllegalArgumentException() {
    assertThat(
            OidcClaimExtractor.extractOrFallback(
                () -> {
                  throw new IllegalArgumentException("not a string array");
                },
                "fallback",
                "test"))
        .isEqualTo("fallback");
  }

  @Test
  void shouldReturnFallbackWhenExtractionThrowsUnrelatedRuntimeException() {
    // proves the catch is broad enough to cover more than just IllegalArgumentException; this is
    // the test that fails if the catch were narrowed to IllegalArgumentException only
    assertThat(
            OidcClaimExtractor.extractOrFallback(
                () -> {
                  throw new NullPointerException("unrelated failure");
                },
                "fallback",
                "test"))
        .isEqualTo("fallback");
  }

  @Test
  void shouldReturnFallbackWhenExtractionReturnsNull() {
    // extraction legitimately returns null when the underlying claim isn't configured
    assertThat(OidcClaimExtractor.extractOrFallback(() -> null, "fallback", "test"))
        .isEqualTo("fallback");
  }
}
