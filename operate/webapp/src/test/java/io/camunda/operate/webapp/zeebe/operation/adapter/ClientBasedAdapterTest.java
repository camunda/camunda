/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.zeebe.operation.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

class ClientBasedAdapterTest {

  private final ClientBasedAdapter adapter = new ClientBasedAdapter(null, null);

  @Test
  void shouldReportOutcomeUnknownWhenDeadlineExceeded() {
    // given
    final var error = clientError(Status.DEADLINE_EXCEEDED);

    // when
    final var outcomeUnknown = adapter.isOutcomeUnknown(error);

    // then
    assertThat(outcomeUnknown).isTrue();
  }

  @Test
  void shouldNotReportOutcomeUnknownWhenRejectedBeforeProcessing() {
    // given
    final var unavailable = clientError(Status.UNAVAILABLE);
    final var exhausted = clientError(Status.RESOURCE_EXHAUSTED);

    // when / then
    assertThat(adapter.isOutcomeUnknown(unavailable)).isFalse();
    assertThat(adapter.isOutcomeUnknown(exhausted)).isFalse();
  }

  @Test
  void shouldNotReportOutcomeUnknownForNonGrpcError() {
    // given
    final var error = new CompletionException(new IllegalStateException("boom"));

    // when
    final var outcomeUnknown = adapter.isOutcomeUnknown(error);

    // then
    assertThat(outcomeUnknown).isFalse();
  }

  private static Throwable clientError(final Status status) {
    return new CompletionException(new StatusRuntimeException(status));
  }
}
