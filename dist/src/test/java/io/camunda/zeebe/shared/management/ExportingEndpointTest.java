/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.from;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.gateway.admin.exporting.ExportingControlApi;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;

final class ExportingEndpointTest {
  @ParameterizedTest
  @ValueSource(strings = {ExportingEndpoint.PAUSE, ExportingEndpoint.RESUME})
  void pauseAndResumeFailsIfCallFailsDirectly(final String operation) {
    // given
    final var service = mock(ExportingControlApi.class);
    final var endpoint = new ExportingEndpoint(service);

    // when
    when(service.pauseExporting()).thenThrow(new RuntimeException());
    when(service.resumeExporting()).thenThrow(new RuntimeException());
    when(service.softPauseExporting()).thenThrow(new RuntimeException());

    // then
    assertThat(endpoint.post(operation, false))
        .returns(
            WebEndpointResponse.STATUS_INTERNAL_SERVER_ERROR, from(WebEndpointResponse::getStatus));
  }

  @ParameterizedTest
  @ValueSource(strings = {ExportingEndpoint.PAUSE, ExportingEndpoint.RESUME})
  void pauseAndResumeFailIfCallReturnsFailedFuture(final String operation) {
    // given
    final var service = mock(ExportingControlApi.class);
    final var endpoint = new ExportingEndpoint(service);

    // when
    when(service.pauseExporting())
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException()));
    when(service.resumeExporting())
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException()));
    when(service.softPauseExporting())
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException()));

    // then
    assertThat(endpoint.post(operation, false))
        .returns(
            WebEndpointResponse.STATUS_INTERNAL_SERVER_ERROR, from(WebEndpointResponse::getStatus));
  }

  @ParameterizedTest
  @ValueSource(strings = {ExportingEndpoint.PAUSE, ExportingEndpoint.RESUME})
  void pauseAndResumeCanSucceed(final String operation) {
    // given
    final var service = mock(ExportingControlApi.class);
    final var endpoint = new ExportingEndpoint(service);

    // when
    when(service.pauseExporting()).thenReturn(CompletableFuture.completedFuture(null));
    when(service.resumeExporting()).thenReturn(CompletableFuture.completedFuture(null));
    when(service.softPauseExporting()).thenReturn(CompletableFuture.completedFuture(null));

    // then
    assertThat(endpoint.post(operation, false))
        .returns(WebEndpointResponse.STATUS_NO_CONTENT, from(WebEndpointResponse::getStatus));
  }
}
