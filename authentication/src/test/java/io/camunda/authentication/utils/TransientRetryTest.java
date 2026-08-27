/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.utils;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.exception.CamundaSearchException.Reason;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class TransientRetryTest {

  @ParameterizedTest
  @EnumSource(
      value = Reason.class,
      names = {"CONNECTION_FAILED", "SEARCH_CLIENT_FAILED", "SEARCH_SERVER_FAILED"})
  void shouldClassifyInfrastructureFailuresAsTransient(final Reason reason) {
    assertThat(TransientRetry.isTransient(new CamundaSearchException("boom", reason))).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = Reason.class,
      names = {"CONNECTION_FAILED", "SEARCH_CLIENT_FAILED", "SEARCH_SERVER_FAILED"},
      mode = EnumSource.Mode.EXCLUDE)
  void shouldClassifyEveryOtherReasonAsNonTransient(final Reason reason) {
    assertThat(TransientRetry.isTransient(new CamundaSearchException("boom", reason))).isFalse();
  }

  @ParameterizedTest
  @EnumSource(
      value = Reason.class,
      names = {"CONNECTION_FAILED", "SEARCH_CLIENT_FAILED", "SEARCH_SERVER_FAILED"})
  void shouldSeeThroughTheServiceExceptionTranslation(final Reason reason) {
    // given — the shape callers of the *Services API actually get: ErrorMapper rewraps the search
    // failure, and its Status collapses SEARCH_CLIENT_FAILED, SEARCH_SERVER_FAILED and the
    // catch-all onto INTERNAL, so only the preserved cause still carries the reason
    final ServiceException translated =
        ErrorMapper.mapSearchError(new CamundaSearchException("boom", reason));

    assertThat(TransientRetry.isTransient(translated)).isTrue();
  }

  @Test
  void shouldNotTreatTranslatedNonTransientFailureAsTransient() {
    // given — INVALID_ARGUMENT and SEARCH_CLIENT_FAILED are indistinguishable by Status alone once
    // translated, so this is what proves the classification reads the cause rather than the Status
    final ServiceException translated =
        ErrorMapper.mapSearchError(
            new CamundaSearchException("bad query", Reason.INVALID_ARGUMENT));

    assertThat(TransientRetry.isTransient(translated)).isFalse();
  }

  @Test
  void shouldFindSearchFailureNestedDeeperInTheCauseChain() {
    final var nested =
        new CompletionException(
            ErrorMapper.mapSearchError(
                new CamundaSearchException("boom", Reason.CONNECTION_FAILED)));

    assertThat(TransientRetry.isTransient(nested)).isTrue();
  }

  @Test
  void shouldNotRetryServiceExceptionWithoutASearchCause() {
    // an internal error raised outside the search layer shares Status.INTERNAL but is not retryable
    assertThat(TransientRetry.isTransient(new ServiceException("boom", Status.INTERNAL))).isFalse();
  }

  @Test
  void shouldNotRetryPlainRuntimeException() {
    assertThat(TransientRetry.isTransient(new IllegalStateException("programming error")))
        .isFalse();
  }
}
