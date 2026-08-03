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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationChangeAwaiter;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationChangeResponse;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ExportingStateChangeRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequestSender;
import io.camunda.zeebe.dynamic.config.api.ErrorResponse;
import io.camunda.zeebe.dynamic.config.state.ExportingState;
import io.camunda.zeebe.util.Either;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;

final class ExportingEndpointTest {

  private final ClusterConfigurationManagementRequestSender requestSender =
      mock(ClusterConfigurationManagementRequestSender.class);
  private final ExportingEndpoint endpoint =
      new ExportingEndpoint(
          requestSender,
          new ClusterConfigurationChangeAwaiter(
              requestSender, Duration.ofMillis(1), Duration.ofSeconds(10)));

  @ParameterizedTest
  @ValueSource(strings = {ExportingEndpoint.PAUSE, ExportingEndpoint.RESUME})
  void pauseAndResumeMapToExpectedState(final String operation) {
    // given
    final var captor = ArgumentCaptor.forClass(ExportingStateChangeRequest.class);
    when(requestSender.changeExporterState(captor.capture())).thenReturn(emptyPlan());

    // when
    final var response = endpoint.post(operation, false);

    // then
    assertThat(response)
        .returns(WebEndpointResponse.STATUS_NO_CONTENT, from(WebEndpointResponse::getStatus));
    final var expectedState =
        operation.equals(ExportingEndpoint.RESUME)
            ? ExportingState.EXPORTING
            : ExportingState.PAUSED;
    assertThat(captor.getValue().state()).isEqualTo(expectedState);
  }

  @Test
  void pauseWithSoftFlagMapsToSoftPaused() {
    // given
    final var captor = ArgumentCaptor.forClass(ExportingStateChangeRequest.class);
    when(requestSender.changeExporterState(captor.capture())).thenReturn(emptyPlan());

    // when
    endpoint.post(ExportingEndpoint.PAUSE, true);

    // then
    assertThat(captor.getValue().state()).isEqualTo(ExportingState.SOFT_PAUSED);
  }

  @ParameterizedTest
  @ValueSource(strings = {ExportingEndpoint.PAUSE, ExportingEndpoint.RESUME})
  void pauseAndResumeFailIfSubmissionFails(final String operation) {
    // given
    when(requestSender.changeExporterState(any()))
        .thenReturn(
            CompletableFuture.completedFuture(
                Either.left(new ErrorResponse(ErrorResponse.ErrorCode.INVALID_REQUEST, "nope"))));

    // when
    final var response = endpoint.post(operation, false);

    // then
    assertThat(response)
        .returns(
            WebEndpointResponse.STATUS_INTERNAL_SERVER_ERROR, from(WebEndpointResponse::getStatus));
  }

  @ParameterizedTest
  @MethodSource("exceptionSource")
  void shouldReturnResponseCorrectlyWhenExceptionIsThrown(
      final String operation, final String message) {
    // given
    final var exception = new RuntimeException(message);
    when(requestSender.changeExporterState(any()))
        .thenReturn(CompletableFuture.failedFuture(new CompletionException(exception)));

    // when
    final var response = endpoint.post(operation, false);

    // then
    assertThat(response)
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

  private CompletableFuture<Either<ErrorResponse, ClusterConfigurationChangeResponse>> emptyPlan() {
    return CompletableFuture.completedFuture(
        Either.right(new ClusterConfigurationChangeResponse(0, Map.of(), Map.of(), List.of())));
  }
}
