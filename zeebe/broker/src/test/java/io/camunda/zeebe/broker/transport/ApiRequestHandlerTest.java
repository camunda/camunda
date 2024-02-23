/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.transport;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.broker.transport.AsyncApiRequestHandler.RequestReader;
import io.camunda.zeebe.broker.transport.AsyncApiRequestHandler.ResponseWriter;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import io.camunda.zeebe.transport.ServerOutput;
import io.camunda.zeebe.util.Either;
import java.util.function.Supplier;
import org.agrona.DirectBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

final class ApiRequestHandlerTest {
  @RegisterExtension
  public final ControlledActorSchedulerExtension actorScheduler =
      new ControlledActorSchedulerExtension();

  @Test
  void shouldReadRequestBuffer() {
    // given
    final var reader = mock(RequestReader.class);
    final var writer = mock(ResponseWriter.class);
    final var handler = new TestApiRequestHandler(() -> reader, () -> writer);
    actorScheduler.submitActor(handler);

    final var buffer = mock(DirectBuffer.class);
    final var output = mock(ServerOutput.class);

    // when
    handler.onRequest(output, 0, 0, buffer, 0, 1);
    actorScheduler.workUntilDone();

    // then
    verify(reader).wrap(buffer, 0, 1);
  }

  @Test
  void shouldResetReaderAndWriter() {
    // given
    final var reader = mock(RequestReader.class);
    final var writer = mock(ResponseWriter.class);
    final var handler = new TestApiRequestHandler(() -> reader, () -> writer);
    actorScheduler.submitActor(handler);

    final var buffer = mock(DirectBuffer.class);
    final var output = mock(ServerOutput.class);

    // when
    handler.onRequest(output, 0, 0, buffer, 0, 1);
    actorScheduler.workUntilDone();

    // then

    verify(reader).reset();
    verify(writer).reset();
  }

  @Test
  void shouldWriteResponse() {
    // given
    final var reader = mock(RequestReader.class);
    final var writer = mock(ResponseWriter.class);
    final var handler = new TestApiRequestHandler(() -> reader, () -> writer);
    actorScheduler.submitActor(handler);

    final var buffer = mock(DirectBuffer.class);
    final var output = mock(ServerOutput.class);
    final var partitionId = 12;
    final var requestId = 34;
    doThrow(new RuntimeException()).when(buffer).wrap(buffer, 0, 1);

    // when
    handler.onRequest(output, partitionId, requestId, buffer, 0, 1);
    actorScheduler.workUntilDone();

    // then
    verify(writer).tryWriteResponse(output, partitionId, requestId);
  }

  private static class TestApiRequestHandler
      extends AsyncApiRequestHandler<RequestReader<?>, ResponseWriter> {
    TestApiRequestHandler(
        final Supplier<RequestReader<?>> requestReaderSupplier,
        final Supplier<ResponseWriter> responseWriterSupplier) {
      super(requestReaderSupplier, responseWriterSupplier);
    }

    @Override
    protected ActorFuture<Either<ErrorResponseWriter, ResponseWriter>> handleAsync(
        final int partitionId,
        final long requestId,
        final RequestReader<?> requestReader,
        final ResponseWriter responseWriter,
        final ErrorResponseWriter errorWriter) {
      return CompletableActorFuture.completed(Either.right(responseWriter));
    }
  }
}
