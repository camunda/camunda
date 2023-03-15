/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.transport.stream.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.transport.stream.api.RemoteStream.ErrorHandler;
import io.camunda.zeebe.transport.stream.impl.ImmutableStreamRegistry.StreamId;
import io.camunda.zeebe.transport.stream.impl.RemoteStreamPusher.Transport;
import io.camunda.zeebe.transport.stream.impl.messages.PushStreamRequest;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.agrona.MutableDirectBuffer;
import org.junit.jupiter.api.Test;

final class RemoteStreamPusherTest {
  private final StreamId streamId = new StreamId(UUID.randomUUID(), MemberId.anonymous());
  private final TestTransport transport = new TestTransport();
  private final Executor executor = Runnable::run;
  private final RemoteStreamPusher<Payload> pusher =
      new RemoteStreamPusher<>(streamId, transport, executor);

  @Test
  void shouldPushPayload() {
    // given
    final var payload = new Payload(1);
    final var errorHandler = new TestErrorHandler();

    // when
    pusher.pushAsync(payload, errorHandler);

    // then
    final var sentRequest = transport.message;
    assertThat(errorHandler.errors).isEmpty();
    assertThat(sentRequest).isNotNull();
    assertThat(sentRequest.request.streamId()).isEqualTo(streamId.streamId());
    assertThat(sentRequest.request.payloadWriter()).isEqualTo(payload);
    assertThat(sentRequest.receiver).isEqualTo(streamId.receiver());
  }

  @Test
  void shouldReportTransportError() {
    // given
    final var payload = new Payload(1);
    final var errorHandler = new TestErrorHandler();
    final var failure = new RuntimeException("Sync failure");
    transport.synchronousException = failure;

    // when
    pusher.pushAsync(payload, errorHandler);

    // then
    assertThat(errorHandler.errors)
        .hasSize(1)
        .first()
        .extracting(TestErrorHandler.Error::payload, TestErrorHandler.Error::error)
        .containsExactly(payload, failure);
  }

  @Test
  void shouldReportAsyncTransportError() {
    // given
    final var payload = new Payload(1);
    final var errorHandler = new TestErrorHandler();
    final var failure = new RuntimeException("Async failure");
    transport.response = CompletableFuture.failedFuture(failure);

    // when
    pusher.pushAsync(payload, errorHandler);

    // then
    assertThat(errorHandler.errors)
        .hasSize(1)
        .first()
        .extracting(TestErrorHandler.Error::payload, TestErrorHandler.Error::error)
        .containsExactly(payload, failure);
  }

  @Test
  void shouldFailOnNullPayload() {
    // given
    final var errorHandler = new TestErrorHandler();

    // when - then
    assertThatCode(() -> pusher.pushAsync(null, errorHandler))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void shouldFailOnNullErrorHandler() {
    // given
    final var payload = new Payload(1);

    // when - then
    assertThatCode(() -> pusher.pushAsync(payload, null)).isInstanceOf(NullPointerException.class);
  }

  private record Payload(int version) implements BufferWriter {

    @Override
    public int getLength() {
      return Integer.BYTES;
    }

    @Override
    public void write(final MutableDirectBuffer buffer, final int offset) {
      buffer.putInt(offset, version);
    }
  }

  private record TestErrorHandler(List<Error> errors) implements ErrorHandler<Payload> {

    private TestErrorHandler() {
      this(new ArrayList<>());
    }

    @Override
    public void handleError(final Throwable error, final Payload data) {
      errors.add(new Error(data, error));
    }

    private record Error(Payload payload, Throwable error) {}
  }

  private static final class TestTransport implements Transport {
    private CompletableFuture<Void> response = CompletableFuture.completedFuture(null);
    private Message message;
    private Exception synchronousException;

    @Override
    public CompletableFuture<Void> send(final PushStreamRequest request, final MemberId receiver)
        throws Exception {
      if (synchronousException != null) {
        throw synchronousException;
      }

      message = new Message(request, receiver);
      return response;
    }

    private record Message(PushStreamRequest request, MemberId receiver) {}
  }
}
