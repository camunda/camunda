/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

class ErrorTypeTest {

  @Test
  void shouldDetectWrappedBackpressure() {
    // given
    final var exhausted =
        new CompletionException(new StatusRuntimeException(Status.RESOURCE_EXHAUSTED));

    // when / then
    assertThat(ErrorType.isBackpressure(exhausted)).isTrue();
  }

  @Test
  void shouldNotTreatOtherFailuresAsBackpressure() {
    // given
    final var unauthenticated = new StatusRuntimeException(Status.UNAUTHENTICATED);
    final var unavailable = new StatusRuntimeException(Status.UNAVAILABLE);

    // when / then
    assertThat(ErrorType.isBackpressure(unauthenticated)).isFalse();
    assertThat(ErrorType.isBackpressure(unavailable)).isFalse();
    assertThat(ErrorType.isBackpressure(new RuntimeException("boom"))).isFalse();
  }
}
