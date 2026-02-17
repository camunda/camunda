/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.transport.stream.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.transport.stream.api.RemoteStreamErrorHandler;
import io.camunda.zeebe.transport.stream.api.StreamResponseException;
import io.camunda.zeebe.transport.stream.impl.AggregatedRemoteStream.StreamId;
import io.camunda.zeebe.transport.stream.impl.RemoteStreamPusher.Transport;
import io.camunda.zeebe.transport.stream.impl.messages.ErrorCode;
import io.camunda.zeebe.transport.stream.impl.messages.ErrorResponse;
import io.camunda.zeebe.transport.stream.impl.messages.PushStreamRequest;
import io.camunda.zeebe.transport.stream.impl.messages.PushStreamResponse;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.agrona.MutableDirectBuffer;
import org.assertj.core.condition.VerboseCondition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;

final class RemoteStreamPusherTest {
  private final StreamId streamId = new StreamId(UUID.randomUUID(), MemberId.anonymous());
  private final TestTransport transport = new TestTransport();
  private final Executor executor = Runnable::run;
  private final TestRemoteStreamMetrics metrics = new TestRemoteStreamMetrics();
  private final RemoteStreamPusher<Payload> pusher =
      new RemoteStreamPusher<>(transport, executor, metrics);

  @Test
  void shouldPushPayload() {
    // given
    final var payload = new Payload(1);
    final var errorHandler = new TestErrorHandler();

    // when
    pusher.pushAsync(payload, errorHandler, streamId);

    // then
    final var sentRequest = transport.message;
    assertThat(errorHandler.errors).isEmpty();
    assertThat(sentRequest).isNotNull();
    assertThat(sentRequest.request.streamId()).isEqualTo(streamId.streamId());
    assertThat(sentRequest.request.payloadWriter()).isEqualTo(payload);
    assertThat(sentRequest.receiver).isEqualTo(streamId.receiver());
    assertThat(metrics.getPushSucceeded()).isOne();
  }

  @Test
  void shouldReportTransportError() {
    // given
    final var payload = new Payload(1);
    final var errorHandler = new TestErrorHandler();
    final var failure = new RuntimeException("Sync failure");
    transport.synchronousException = failure;

    // when
    pusher.pushAsync(payload, errorHandler, streamId);

    // then
    assertThat(metrics.getPushFailed()).isOne();
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
    pusher.pushAsync(payload, errorHandler, streamId);

    // then
    assertThat(metrics.getPushFailed()).isOne();
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

    // when
    pusher.pushAsync(null, errorHandler, streamId);

    // then
    assertThat(errorHandler.errors())
        .haveExactly(
            1,
            VerboseCondition.verboseCondition(
                e -> (e.error instanceof NullPointerException),
                "a null pointer exception",
                e -> " but it has an error of type '%s'".formatted(e.error.getClass())));
  }

  @Test
  void shouldFailOnNullErrorHandler() {
    // given
    final var payload = new Payload(1);

    // when - then
    assertThatCode(() -> pusher.pushAsync(payload, null, streamId))
        .isInstanceOf(NullPointerException.class);
  }

  @ParameterizedTest
  @EnumSource(
      value = ErrorCode.class,
      mode = Mode.EXCLUDE,
      names = {"SBE_UNKNOWN", "NULL_VAL"})
  void shouldTrackFailedPushTries(final ErrorCode detailCode) {
    // given
    final var payload = new Payload(1);
    final var errorHandler = new TestErrorHandler();
    final var errorResponse =
        new ErrorResponse().code(ErrorCode.INTERNAL).message("foo").addDetail(detailCode, "bar");
    final var failure = new StreamResponseException(errorResponse);
    transport.response = CompletableFuture.failedFuture(failure);

    // when
    pusher.pushAsync(payload, errorHandler, streamId);

    // then
    assertThat(metrics.getFailedPushTry(detailCode)).isOne();
  }

  private record Payload(int version) implements BufferWriter {

    @Override
    public int getLength() {
      return Integer.BYTES;
    }

    @Override
    public int write(final MutableDirectBuffer buffer, final int offset) {
      buffer.putInt(offset, version);
      return getLength();
    }
  }

  private record TestErrorHandler(List<Error> errors) implements RemoteStreamErrorHandler<Payload> {

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
    private CompletableFuture<byte[]> response =
        CompletableFuture.completedFuture(BufferUtil.bufferAsArray(new PushStreamResponse()));
    private Message message;
    private Exception synchronousException;

    @Override
    public CompletableFuture<byte[]> send(final PushStreamRequest request, final MemberId receiver)
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
