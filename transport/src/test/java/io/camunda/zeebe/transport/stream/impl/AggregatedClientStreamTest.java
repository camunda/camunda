/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.transport.stream.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.scheduler.testing.TestActorFuture;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.camunda.zeebe.transport.stream.api.ClientStreamConsumer;
import io.camunda.zeebe.transport.stream.api.ClientStreamId;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

class AggregatedClientStreamTest {
  private static final ClientStreamConsumer CLIENT_STREAM_CONSUMER =
      p -> CompletableFuture.completedFuture(null);

  private final UnsafeBuffer streamType = new UnsafeBuffer(BufferUtil.wrapString("foo"));
  private final TestSerializableData metadata = new TestSerializableData(1234);
  private final TestClientStreamMetrics metrics = new TestClientStreamMetrics();
  final AggregatedClientStream<TestSerializableData> stream =
      new AggregatedClientStream<>(
          UUID.randomUUID(), new LogicalId<>(streamType, metadata), metrics);

  private final TestConcurrencyControl executor = new TestConcurrencyControl();

  @Test
  void shouldRetryWithAllAvailableClientsIfPushFailed() {
    // given
    final List<ClientStreamId> executedClients = new ArrayList<>();
    final List<ClientStreamId> expected =
        List.of(
            addFailingClient(executedClients::add),
            addFailingClient(executedClients::add),
            addFailingClient(executedClients::add));

    // when
    final TestActorFuture<Void> future = new TestActorFuture<>();
    stream.push(null, future, executor);

    // then
    assertThat(future).failsWithin(Duration.ofMillis(100));
    assertThat(executedClients).containsExactlyInAnyOrderElementsOf(expected);
  }

  @Test
  void shouldSucceedWhenOneClientSucceeds() {
    // given
    addFailingClient(s -> {});
    addFailingClient(s -> {});

    final AtomicBoolean pushSucceeded = new AtomicBoolean(false);
    final ClientStreamIdImpl streamId = getNextStreamId();
    addClient(
        streamId,
        p -> {
          pushSucceeded.set(true);
          return CompletableFuture.completedFuture(null);
        });

    // when
    final TestActorFuture<Void> future = new TestActorFuture<>();
    stream.push(null, future, executor);

    // then
    assertThat(future).succeedsWithin(Duration.ofMillis(100));
    assertThat(pushSucceeded.get()).isTrue();
  }

  @Test
  void shouldReportStreamCountOnAdd() {
    // given

    // when
    addClient(getNextStreamId(), CLIENT_STREAM_CONSUMER);
    addClient(getNextStreamId(), CLIENT_STREAM_CONSUMER);

    // then
    assertThat(metrics.getAggregatedClientCountObservations()).containsExactly(1, 2);
  }

  @Test
  void shouldReportStreamCountOnRemove() {
    // given
    final var streamId = getNextStreamId();
    stream.addClient(
        new ClientStream<>(streamId, stream, streamType, metadata, CLIENT_STREAM_CONSUMER));

    // when
    stream.removeClient(streamId);

    // then
    assertThat(metrics.getAggregatedClientCountObservations()).containsExactly(1, 0);
  }

  private ClientStreamIdImpl getNextStreamId() {
    return new ClientStreamIdImpl(stream.getStreamId(), stream.nextLocalId());
  }

  private ClientStreamId addFailingClient(final Consumer<ClientStreamId> consumer) {
    final ClientStreamIdImpl streamId = getNextStreamId();
    addClient(
        streamId,
        p -> {
          consumer.accept(streamId);
          throw new RuntimeException("Failed");
        });
    return streamId;
  }

  private void addClient(final ClientStreamIdImpl streamId, final ClientStreamConsumer consumer) {
    stream.addClient(new ClientStream<>(streamId, stream, streamType, metadata, consumer));
  }
}
