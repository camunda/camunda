/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.transport.commandapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.broker.transport.ErrorResponseWriter;
import io.camunda.zeebe.broker.transport.backpressure.NoopRequestLimiter;
import io.camunda.zeebe.broker.transport.backpressure.RequestLimiter;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerPublishMessageRequest;
import io.camunda.zeebe.logstreams.log.LogStreamRecordWriter;
import io.camunda.zeebe.protocol.impl.encoding.ExecuteCommandRequest;
import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferWriter;
import io.camunda.zeebe.util.sched.testing.ControlledActorSchedulerRule;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class CommandApiRequestHandlerTest {
  @Rule public final ControlledActorSchedulerRule scheduler = new ControlledActorSchedulerRule();
  final CommandApiRequestHandler handler = new CommandApiRequestHandler();

  @Before
  public void setup() {
    scheduler.submitActor(handler);
    handler.addPartition(0, mock(LogStreamRecordWriter.class), new NoopRequestLimiter<>());
    scheduler.workUntilDone();
  }

  @Test
  public void shouldRejectCommandIfNotLeader() {
    // given
    final var request = new ExecuteCommandRequest();
    handler.removePartition(0);
    scheduler.workUntilDone();

    // when
    final var response = handleRequest(request);

    // then
    assertThat(response).matches(Either::isLeft);
    assertThat(response.getLeft().getErrorCode()).isEqualTo(ErrorCode.PARTITION_LEADER_MISMATCH);
  }

  @Test
  public void shouldRejectCommandWithoutEvent() {
    // given
    final var request = new ExecuteCommandRequest();

    // when
    final var response = handleRequest(request);

    // then
    assertThat(response).matches(Either::isLeft);
    assertThat(response.getLeft().getErrorCode()).isEqualTo(ErrorCode.UNSUPPORTED_MESSAGE);
  }

  @Test
  public void shouldRejectCommandWithUnknownEvent() {
    // given
    final var request = new ExecuteCommandRequest();
    request.setValueType(ValueType.ERROR);

    // when
    final var response = handleRequest(request);

    // then
    assertThat(response).matches(Either::isLeft);
    assertThat(response.getLeft().getErrorCode()).isEqualTo(ErrorCode.UNSUPPORTED_MESSAGE);
  }

  @Test
  public void shouldRejectCommandIfResourcesExhausted() {
    // given
    final RequestLimiter<Intent> limiter = mock(RequestLimiter.class);
    when(limiter.tryAcquire(anyInt(), anyLong(), any())).thenReturn(false);
    handler.addPartition(0, mock(LogStreamRecordWriter.class), limiter);
    scheduler.workUntilDone();

    final var request =
        new BrokerPublishMessageRequest("test", "1").setMessageId("1").setTimeToLive(0);
    request.serializeValue();

    // when
    final var response = handleRequest(request);

    // then
    assertThat(response).matches(Either::isLeft);
    assertThat(response.getLeft().getErrorCode()).isEqualTo(ErrorCode.RESOURCE_EXHAUSTED);
  }

  @Test
  public void shouldWriteToLog() {
    // given
    final var logWriter = mock(LogStreamRecordWriter.class);
    when(logWriter.metadataWriter(any())).thenReturn(logWriter);
    when(logWriter.valueWriter(any())).thenReturn(logWriter);
    handler.addPartition(0, logWriter, new NoopRequestLimiter<>());
    scheduler.workUntilDone();

    final var request =
        new BrokerPublishMessageRequest("test", "1").setMessageId("1").setTimeToLive(0);
    request.serializeValue();

    // when
    final var response = handleRequest(request);

    // then
    assertThat(response).matches(Either::isRight);
    verify(logWriter).tryWrite();
    verify(logWriter).reset();
  }

  private Either<ErrorResponseWriter, CommandApiResponseWriter> handleRequest(
      final BufferWriter request) {
    final var reader = new CommandApiRequestReader();
    final var writer = new CommandApiResponseWriter();
    final var errorWriter = new ErrorResponseWriter();
    final var requestBuffer = new UnsafeBuffer(new byte[request.getLength()]);
    request.write(requestBuffer, 0);
    reader.wrap(requestBuffer, 0, requestBuffer.capacity());
    return handler.handleExecuteCommandRequest(0, 0, reader, writer, errorWriter);
  }
}
