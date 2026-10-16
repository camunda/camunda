/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.zeebe.operation.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;

class ServicesBasedAdapterTest {

  private final ServicesBasedAdapter adapter =
      new ServicesBasedAdapter(null, null, null, null, null, null, null);

  @Test
  void shouldReportOutcomeUnknownWhenDeadlineExceeded() {
    // given
    final var error = new ServiceException("timeout", Status.DEADLINE_EXCEEDED);

    // when
    final var outcomeUnknown = adapter.isOutcomeUnknown(error);

    // then
    assertThat(outcomeUnknown).isTrue();
  }

  @Test
  void shouldUnwrapCompletionAndExecutionExceptions() {
    // given
    final var timeout = new ServiceException("timeout", Status.DEADLINE_EXCEEDED);
    final var completion = new CompletionException(timeout);
    final var execution = new ExecutionException(completion);

    // when / then
    assertThat(adapter.isOutcomeUnknown(completion)).isTrue();
    assertThat(adapter.isOutcomeUnknown(execution)).isTrue();
  }

  @Test
  void shouldNotReportOutcomeUnknownWhenRejectedBeforeProcessing() {
    // given
    final var unavailable = new ServiceException("unavailable", Status.UNAVAILABLE);
    final var exhausted = new ServiceException("exhausted", Status.RESOURCE_EXHAUSTED);

    // when / then
    assertThat(adapter.isOutcomeUnknown(unavailable)).isFalse();
    assertThat(adapter.isOutcomeUnknown(exhausted)).isFalse();
  }

  @Test
  void shouldNotReportOutcomeUnknownForNullOrUnrelatedErrors() {
    // when / then
    assertThat(adapter.isOutcomeUnknown(null)).isFalse();
    assertThat(adapter.isOutcomeUnknown(new IllegalStateException("boom"))).isFalse();
  }
}
