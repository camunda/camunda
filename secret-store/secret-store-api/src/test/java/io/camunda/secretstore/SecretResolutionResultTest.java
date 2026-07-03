/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SecretResolutionResultTest {

  @Test
  void shouldCarryResolvedValue() {
    // given / when
    final var result = new SecretResolutionResult.Resolved("hunter2");

    // then
    assertThat(result.value()).isEqualTo("hunter2");
  }

  @Test
  void shouldMaskResolvedToString() {
    // given
    final var result = new SecretResolutionResult.Resolved("hunter2");

    // when
    final var str = result.toString();

    // then — secret value must not appear in log output
    assertThat(str).doesNotContain("hunter2");
    assertThat(str).contains("***");
  }

  @Test
  void shouldCarryFailedCodeMessageAndCause() {
    // given
    final var cause = new RuntimeException("connection refused");

    // when
    final var result =
        new SecretResolutionResult.Failed(
            SecretErrorCode.STORE_UNAVAILABLE, "store is down", cause);

    // then
    assertThat(result.code()).isEqualTo(SecretErrorCode.STORE_UNAVAILABLE);
    assertThat(result.message()).isEqualTo("store is down");
    assertThat(result.cause()).isSameAs(cause);
  }

  @Test
  void shouldAcceptNullCauseInFailed() {
    // given / when
    final var result =
        new SecretResolutionResult.Failed(SecretErrorCode.NOT_FOUND, "missing", null);

    // then
    assertThat(result.cause()).isNull();
  }

  @Test
  void shouldSupportExhaustivePatternSwitch() {
    // given
    final SecretResolutionResult result = new SecretResolutionResult.Resolved("val");

    // when
    final String output =
        switch (result) {
          case SecretResolutionResult.Resolved r -> "resolved:" + r.value();
          case SecretResolutionResult.Failed f -> "failed:" + f.code();
        };

    // then
    assertThat(output).isEqualTo("resolved:val");
  }
}
