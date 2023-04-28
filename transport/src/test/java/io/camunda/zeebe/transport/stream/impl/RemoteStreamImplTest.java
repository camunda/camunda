/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.transport.stream.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.transport.stream.api.RemoteStreamMetrics;
import io.camunda.zeebe.transport.stream.impl.AggregatedRemoteStream.StreamConsumer;
import io.camunda.zeebe.transport.stream.impl.AggregatedRemoteStream.StreamId;
import io.camunda.zeebe.transport.stream.impl.RemoteStreamPusher.Transport;
import io.camunda.zeebe.transport.stream.impl.messages.PushStreamRequest;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.agrona.concurrent.UnsafeBuffer;
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

  private final RemoteStreamImpl<TestSerializableData, TestSerializableData> remoteStream =
      new RemoteStreamImpl<>(aggregatedStream, pusher, executor);

  @Test
  void shouldRetryWithAllAvailableRemoteStreams() {
    // given
    final UUID stream1 = UUID.randomUUID();
    aggregatedStream.addConsumer(
        new StreamConsumer<>(new StreamId(stream1, MemberId.anonymous()), properties, streamType));
    final UUID stream2 = UUID.randomUUID();
    aggregatedStream.addConsumer(
        new StreamConsumer<>(new StreamId(stream2, MemberId.anonymous()), properties, streamType));
    final UUID stream3 = UUID.randomUUID();
    aggregatedStream.addConsumer(
        new StreamConsumer<>(new StreamId(stream3, MemberId.anonymous()), properties, streamType));

    // when
    remoteStream.push(payload, (e, p) -> {});

    // then
    assertThat(transport.attemptedStreams).containsExactlyInAnyOrder(stream1, stream2, stream3);
  }

  @Test
  void shouldStopRetryWhenPushSucceeds() {
    // given
    final UUID stream1 = UUID.randomUUID();
    aggregatedStream.addConsumer(
        new StreamConsumer<>(new StreamId(stream1, MemberId.anonymous()), properties, streamType));
    final UUID stream2 = UUID.randomUUID();
    aggregatedStream.addConsumer(
        new StreamConsumer<>(new StreamId(stream2, MemberId.anonymous()), properties, streamType));
    final UUID stream3 = UUID.randomUUID();
    aggregatedStream.addConsumer(
        new StreamConsumer<>(new StreamId(stream3, MemberId.anonymous()), properties, streamType));

    // when
    transport.succeedAfterAttempts(1);
    remoteStream.push(payload, (e, p) -> {});

    // then
    assertThat(transport.attemptedStreams).hasSize(2);
  }

  private static class FailingTransport implements Transport {

    private final List<UUID> attemptedStreams = new ArrayList<>();

    private int succeedAfterAttempt = Integer.MAX_VALUE;
    private int attempt = 0;

    void succeedAfterAttempts(final int attempt) {
      succeedAfterAttempt = attempt;
    }

    @Override
    public CompletableFuture<Void> send(final PushStreamRequest request, final MemberId receiver)
        throws Exception {
      attemptedStreams.add(request.streamId());
      attempt++;
      if (attempt <= succeedAfterAttempt) {
        return CompletableFuture.failedFuture(new RuntimeException("force fail"));
      }
      return CompletableFuture.completedFuture(null);
    }
  }
}
