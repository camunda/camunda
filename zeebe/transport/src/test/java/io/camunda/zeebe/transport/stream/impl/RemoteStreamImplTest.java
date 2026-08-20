/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.transport.stream.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.transport.stream.api.RemoteStreamErrorHandler;
import io.camunda.zeebe.transport.stream.api.RemoteStreamMetrics;
import io.camunda.zeebe.transport.stream.api.StreamExhaustedException;
import io.camunda.zeebe.transport.stream.impl.AggregatedRemoteStream.StreamConsumer;
import io.camunda.zeebe.transport.stream.impl.AggregatedRemoteStream.StreamId;
import io.camunda.zeebe.transport.stream.impl.RemoteStreamPusher.Transport;
import io.camunda.zeebe.transport.stream.impl.messages.PushStreamRequest;
import io.camunda.zeebe.transport.stream.impl.messages.PushStreamResponse;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.agrona.collections.MutableReference;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RemoteStreamImplTest {

  private final UnsafeBuffer streamType = new UnsafeBuffer(BufferUtil.wrapString("foo"));
  private final TestSerializableData properties = new TestSerializableData(1);
  private final TestSerializableData payload = new TestSerializableData(1234);
  private final AggregatedRemoteStream<TestSerializableData> aggregatedStream =
      new AggregatedRemoteStream<>(new LogicalId<>(streamType, properties), new ArrayList<>());
  private final FailingTransport transport = new FailingTransport();
  private final Executor executor = Runnable::run;
  private final RemoteStreamPusher<TestSerializableData> pusher =
      new RemoteStreamPusher<>(transport, executor, RemoteStreamMetrics.noop());
  private RemoteStreamErrorHandler<TestSerializableData> errorHandler = (e, d) -> {};
  private final RemoteStreamImpl<TestSerializableData, TestSerializableData> remoteStream =
      new RemoteStreamImpl<>(aggregatedStream, pusher, (e, d) -> errorHandler.handleError(e, d));

  @BeforeEach
  void setup() {
    final UUID stream1 = UUID.randomUUID();
    final LogicalId<TestSerializableData> logicalId = new LogicalId<>(streamType, properties);
    aggregatedStream.addConsumer(
        new StreamConsumer<>(new StreamId(stream1, MemberId.anonymous()), logicalId));
    final UUID stream2 = UUID.randomUUID();
    aggregatedStream.addConsumer(
        new StreamConsumer<>(new StreamId(stream2, MemberId.anonymous()), logicalId));
    final UUID stream3 = UUID.randomUUID();
    aggregatedStream.addConsumer(
        new StreamConsumer<>(new StreamId(stream3, MemberId.anonymous()), logicalId));
  }

  @Test
  void shouldRetryWithAllAvailableRemoteStreams() {
    // given
    final var streams =
        aggregatedStream.streamConsumers().stream().map(s -> s.id().streamId()).toList();

    // when
    remoteStream.push(payload);

    // then
    assertThat(transport.attemptedStreams).containsExactlyInAnyOrderElementsOf(streams);
  }

  @Test
  void shouldStopRetryWhenPushSucceeds() {
    // given
    transport.succeedAfterAttempts(1);

    // when
    remoteStream.push(payload);

    // then
    assertThat(transport.attemptedStreams).hasSize(2);
  }

  @Test
  void shouldFailIfNoConsumersOnPush() {
    // given
    final MutableReference<Throwable> errorRef = new MutableReference<>();
    aggregatedStream.streamConsumers().clear();
    errorHandler = (e, d) -> errorRef.set(e);

    // when
    remoteStream.push(payload);

    // then
    assertThat(errorRef.get()).isInstanceOf(StreamExhaustedException.class);
    assertThat(transport.attemptedStreams).isEmpty();
  }

  private static final class FailingTransport implements Transport {

    private final List<UUID> attemptedStreams = new ArrayList<>();

    private int succeedAfterAttempt = Integer.MAX_VALUE;
    private int attempt = 0;

    void succeedAfterAttempts(final int attempt) {
      succeedAfterAttempt = attempt;
    }

    @Override
    public CompletableFuture<byte[]> send(
        final PushStreamRequest request, final MemberId receiver) {
      attemptedStreams.add(request.streamId());
      attempt++;
      if (attempt <= succeedAfterAttempt) {
        return CompletableFuture.failedFuture(new RuntimeException("force fail"));
      }
      return CompletableFuture.completedFuture(BufferUtil.bufferAsArray(new PushStreamResponse()));
    }
  }
}
