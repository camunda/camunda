/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.transport.stream.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.transport.stream.api.ClientStreamBlockedException;
import io.camunda.zeebe.transport.stream.api.NoSuchStreamException;
import io.camunda.zeebe.transport.stream.api.StreamExhaustedException;
import io.camunda.zeebe.transport.stream.api.StreamResponseException.ErrorDetail;
import io.camunda.zeebe.transport.stream.impl.messages.ErrorCode;
import io.camunda.zeebe.transport.stream.impl.messages.ErrorResponse;
import io.camunda.zeebe.transport.stream.impl.messages.PushStreamRequest;
import java.time.Duration;
import java.util.stream.Stream;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

final class ClientStreamApiHandlerTest {
  private final ClientStreamManager<?> clientStreamManager = mock(ClientStreamManager.class);

  @ParameterizedTest
  @MethodSource("provideExceptionToErrorMap")
  void shouldMapExceptionToErrorResponse(final ExceptionErrorCase testCase) {
    // given
    final var apiHandler = new ClientStreamApiHandler(clientStreamManager, Runnable::run);
    final var request = new PushStreamRequest();
    final var payloadPushed = ArgumentCaptor.forClass(CompletableActorFuture.class);
    //noinspection unchecked
    doNothing().when(clientStreamManager).onPayloadReceived(eq(request), payloadPushed.capture());

    // when
    final var response = apiHandler.handlePushRequest(request);
    payloadPushed.getValue().completeExceptionally(testCase.exception());

    // then
    assertThat(response)
        .succeedsWithin(Duration.ZERO)
        .asInstanceOf(InstanceOfAssertFactories.type(ErrorResponse.class))
        .returns(testCase.code(), ErrorResponse::code);
  }

  @ParameterizedTest
  @MethodSource("provideExceptionToErrorMap")
  void shouldMapSuppressedAsErrorDetails(final ExceptionErrorCase testCase) {
    // given
    final var apiHandler = new ClientStreamApiHandler(clientStreamManager, Runnable::run);
    final var request = new PushStreamRequest();
    final var payloadPushed = ArgumentCaptor.forClass(CompletableActorFuture.class);
    final var error = new RuntimeException("failure");
    //noinspection unchecked
    doNothing().when(clientStreamManager).onPayloadReceived(eq(request), payloadPushed.capture());
    error.addSuppressed(testCase.exception());

    // when
    final var response = apiHandler.handlePushRequest(request);
    payloadPushed.getValue().completeExceptionally(error);

    // then
    assertThat(response)
        .succeedsWithin(Duration.ZERO)
        .asInstanceOf(InstanceOfAssertFactories.type(ErrorResponse.class))
        .extracting(ErrorResponse::details, InstanceOfAssertFactories.list(ErrorDetail.class))
        .map(ErrorDetail::code)
        .containsExactly(testCase.code());
  }

  private static Stream<ExceptionErrorCase> provideExceptionToErrorMap() {
    return Stream.of(
        new ExceptionErrorCase(new StreamExhaustedException("failed"), ErrorCode.EXHAUSTED),
        new ExceptionErrorCase(new ClientStreamBlockedException("failed"), ErrorCode.BLOCKED),
        new ExceptionErrorCase(new NoSuchStreamException("failed"), ErrorCode.NOT_FOUND),
        new ExceptionErrorCase(new RuntimeException("failed"), ErrorCode.INTERNAL));
  }

  private record ExceptionErrorCase(Throwable exception, ErrorCode code)
      implements Named<ExceptionErrorCase> {

    @Override
    public String getName() {
      return "%s -> %s".formatted(exception.getClass().getSimpleName(), code.name());
    }

    @Override
    public ExceptionErrorCase getPayload() {
      return this;
    }
  }
}
