/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.from;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.gateway.admin.exporting.ExportingControlApi;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
    when(service.pauseExporting(DEFAULT_PHYSICAL_TENANT_ID)).thenThrow(new RuntimeException());
    when(service.resumeExporting(DEFAULT_PHYSICAL_TENANT_ID)).thenThrow(new RuntimeException());

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
    when(service.pauseExporting(DEFAULT_PHYSICAL_TENANT_ID))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException()));
    when(service.resumeExporting(DEFAULT_PHYSICAL_TENANT_ID))
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
    when(service.pauseExporting(DEFAULT_PHYSICAL_TENANT_ID))
        .thenReturn(CompletableFuture.completedFuture(null));
    when(service.resumeExporting(DEFAULT_PHYSICAL_TENANT_ID))
        .thenReturn(CompletableFuture.completedFuture(null));

    // then
    assertThat(endpoint.post(operation, false))
        .returns(WebEndpointResponse.STATUS_NO_CONTENT, from(WebEndpointResponse::getStatus));
  }

  @ParameterizedTest
  @MethodSource("exceptionSource")
  void shouldReturnResponseCorrectlyWhenExceptionIsThrown(
      final String operation, final String message) {
    // given
    final var service = mock(ExportingControlApi.class);
    final var endpoint = new ExportingEndpoint(service);
    final var exception = new RuntimeException(message);

    // when
    when(service.pauseExporting(DEFAULT_PHYSICAL_TENANT_ID))
        .thenReturn(CompletableFuture.failedFuture(exception));
    when(service.resumeExporting(DEFAULT_PHYSICAL_TENANT_ID))
        .thenReturn(CompletableFuture.failedFuture(new CompletionException(exception)));

    // then
    assertThat(endpoint.post(operation, false))
        .returns(
            WebEndpointResponse.STATUS_INTERNAL_SERVER_ERROR, from(WebEndpointResponse::getStatus))
        .satisfies(
            resp -> {
              if (message != null) {
                assertThat(resp.getBody())
                    .isInstanceOf(String.class)
                    .asString()
                    .contains(exception.getMessage());
              } else {
                assertThat(resp.getBody()).isNull();
              }
            });
  }

  private static Stream<Arguments> exceptionSource() {
    return Stream.of(ExportingEndpoint.PAUSE, ExportingEndpoint.RESUME)
        .flatMap(
            operation ->
                Stream.of("expected error", null).map(str -> Arguments.of(operation, str)));
  }
}
