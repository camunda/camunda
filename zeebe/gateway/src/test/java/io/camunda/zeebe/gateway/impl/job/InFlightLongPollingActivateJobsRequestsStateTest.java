/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.gateway.metrics.LongPollingMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class InFlightLongPollingActivateJobsRequestsStateTest {

  private InFlightLongPollingActivateJobsRequestsState<Object> state;

  @BeforeEach
  void setUp() {
    state =
        new InFlightLongPollingActivateJobsRequestsState<>("testType", LongPollingMetrics.noop());
  }

  @Test
  void shouldFailAllPendingRequests() {
    final var request1 = mockOpenRequest();
    final var request2 = mockOpenRequest();
    state.enqueueRequest(request1);
    state.enqueueRequest(request2);
    final var error = new RuntimeException("cluster purged");

    state.failAllRequests(error);

    verify(request1).onError(error);
    verify(request2).onError(error);
  }

  @Test
  void shouldFailAllActiveRequests() {
    final var request = mockOpenRequest();
    state.addActiveRequest(request);
    final var error = new RuntimeException("cluster purged");

    state.failAllRequests(error);

    verify(request).onError(error);
  }

  @Test
  void shouldClearAllRequestsAfterFail() {
    final var pending = mockOpenRequest();
    final var active = mockOpenRequest();
    state.enqueueRequest(pending);
    state.addActiveRequest(active);

    state.failAllRequests(new RuntimeException("purged"));

    assertThat(state.hasActiveRequests()).isFalse();
    assertThat(state.getNextPendingRequest()).isNull();
  }

  @SuppressWarnings("unchecked")
  private InflightActivateJobsRequest<Object> mockOpenRequest() {
    final var request = mock(InflightActivateJobsRequest.class);
    when(request.isOpen()).thenReturn(true);
    return request;
  }
}
