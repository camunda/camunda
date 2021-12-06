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

import io.camunda.zeebe.broker.transport.ApiRequestHandler.RequestReader;
import io.camunda.zeebe.broker.transport.ApiRequestHandler.ResponseWriter;
import io.camunda.zeebe.transport.ServerOutput;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.sched.ActorControl;
import io.camunda.zeebe.util.sched.testing.ControlledActorSchedulerRule;
import org.agrona.DirectBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class ApiRequestHandlerTest {

  @Rule
  public final ControlledActorSchedulerRule schedulerRule = new ControlledActorSchedulerRule();

  private RequestReader<?> reader;
  private ResponseWriter writer;
  private TestApiRequestHandler handler;

  @Before
  public void setUp() {
    reader = mock(RequestReader.class);
    writer = mock(ResponseWriter.class);
    handler = new TestApiRequestHandler(reader, writer);
    schedulerRule.submitActor(handler);
  }

  @Test
  public void shouldReadRequestBuffer() {
    // given
    final var buffer = mock(DirectBuffer.class);
    final var output = mock(ServerOutput.class);

    // when
    handler.onRequest(output, 0, 0, buffer, 0, 1);
    schedulerRule.workUntilDone();

    // then
    verify(reader).wrap(buffer, 0, 1);
  }

  @Test
  public void shouldResetReaderAndWriter() {
    // given
    final var buffer = mock(DirectBuffer.class);
    final var output = mock(ServerOutput.class);

    // when
    handler.onRequest(output, 0, 0, buffer, 0, 1);
    schedulerRule.workUntilDone();

    // then

    verify(reader).reset();
    verify(writer).reset();
  }

  @Test
  public void shouldWriteResponse() {
    // given
    final var buffer = mock(DirectBuffer.class);
    final var output = mock(ServerOutput.class);
    final var partitionId = 12;
    final var requestId = 34;
    doThrow(new RuntimeException()).when(buffer).wrap(buffer, 0, 1);

    // when
    handler.onRequest(output, partitionId, requestId, buffer, 0, 1);
    schedulerRule.workUntilDone();

    // then
    verify(writer).tryWriteResponse(output, partitionId, requestId);
  }

  private static class TestApiRequestHandler
      extends ApiRequestHandler<RequestReader<?>, ResponseWriter> {
    TestApiRequestHandler(
        final RequestReader<?> requestReader, final ResponseWriter responseWriter) {
      super(requestReader, responseWriter);
    }

    public ActorControl actor() {
      return actor;
    }

    @Override
    protected Either<ErrorResponseWriter, ResponseWriter> handle(
        final int partitionId,
        final long requestId,
        final RequestReader<?> requestReader,
        final ResponseWriter responseWriter,
        final ErrorResponseWriter errorWriter) {
      return Either.right(responseWriter);
    }
  }
}
