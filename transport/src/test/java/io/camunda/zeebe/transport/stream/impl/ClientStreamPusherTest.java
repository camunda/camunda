/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.transport.stream.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.testing.TestActorFuture;
import io.camunda.zeebe.transport.stream.api.ClientStreamConsumer;
import io.camunda.zeebe.transport.stream.api.ClientStreamId;
import io.camunda.zeebe.transport.stream.api.NoSuchStreamException;
import io.camunda.zeebe.transport.stream.api.StreamExhaustedException;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.agrona.concurrent.UnsafeBuffer;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

final class ClientStreamPusherTest {
  private final UnsafeBuffer streamType = new UnsafeBuffer(BufferUtil.wrapString("foo"));
  private final TestSerializableData metadata = new TestSerializableData(1234);
  private final TestClientStreamMetrics metrics = new TestClientStreamMetrics();

  private final AggregatedClientStream<TestSerializableData> stream =
      new AggregatedClientStream<>(
          UUID.randomUUID(), new LogicalId<>(streamType, metadata), metrics);
  private final ClientStreamPusher streamPusher = new ClientStreamPusher();

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
    streamPusher.push(stream, null, future);

    // then
    assertThat(future)
        .failsWithin(Duration.ofMillis(100))
        .withThrowableThat()
        .havingCause()
        .isInstanceOf(StreamExhaustedException.class);
    assertThat(executedClients).containsExactlyInAnyOrderElementsOf(expected);
  }

  @Test
  void shouldFailIfOnNoClients() {
    // given
    final TestActorFuture<Void> future = new TestActorFuture<>();

    // when
    streamPusher.push(stream, null, future);

    // then
    assertThat(future)
        .failsWithin(Duration.ofMillis(100))
        .withThrowableThat()
        .havingCause()
        .isInstanceOf(NoSuchStreamException.class);
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
          return CompletableActorFuture.completed(null);
        });

    // when
    final TestActorFuture<Void> future = new TestActorFuture<>();
    streamPusher.push(stream, null, future);

    // then
    assertThat(future).succeedsWithin(Duration.ofMillis(100));
    assertThat(pushSucceeded.get()).isTrue();
  }

  @Test
  void shouldAddIndividualErrorsAsSuppressed() {
    // given
    final var expected =
        List.of(
            new RuntimeException("Foo"),
            new IllegalStateException("Foo"),
            new IllegalArgumentException("Foo"));
    expected.forEach(f -> addFailingClient(ignored -> {}, f));

    // when
    final TestActorFuture<Void> future = new TestActorFuture<>();
    streamPusher.push(stream, null, future);

    // then
    assertThat(future)
        .failsWithin(Duration.ofMillis(100))
        .withThrowableThat()
        .havingCause()
        .asInstanceOf(InstanceOfAssertFactories.throwable(StreamExhaustedException.class))
        .extracting(
            StreamExhaustedException::getSuppressed,
            InstanceOfAssertFactories.array(Throwable[].class))
        .containsExactlyInAnyOrderElementsOf(expected);
  }

  private ClientStreamIdImpl getNextStreamId() {
    return new ClientStreamIdImpl(stream.streamId(), stream.nextLocalId());
  }

  private ClientStreamId addFailingClient(final Consumer<ClientStreamId> consumer) {
    return addFailingClient(consumer, new RuntimeException("Failed"));
  }

  private ClientStreamId addFailingClient(
      final Consumer<ClientStreamId> consumer, final RuntimeException failure) {
    final ClientStreamIdImpl streamId = getNextStreamId();
    addClient(
        streamId,
        p -> {
          consumer.accept(streamId);
          throw failure;
        });
    return streamId;
  }

  private void addClient(final ClientStreamIdImpl streamId, final ClientStreamConsumer consumer) {
    stream.addClient(new ClientStreamImpl<>(streamId, stream, streamType, metadata, consumer));
  }
}
