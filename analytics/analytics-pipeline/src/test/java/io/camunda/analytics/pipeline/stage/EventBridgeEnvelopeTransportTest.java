/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.pipeline.stage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.eventbridge.client.EventBridgeClient;
import io.camunda.eventbridge.client.EventBridgeClient.BatchPublisher;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The pipelining and ordering contract of {@link EventBridgeEnvelopeTransport}: one batch per facts
 * partition per dispatch, all partitions in flight at once behind a single acknowledgment future,
 * and successive dispatches to the same partition applied strictly in order — the arrival order the
 * reduce-side {@code (segment, chunk)} dedup depends on.
 */
final class EventBridgeEnvelopeTransportTest {

  /** One initiated publish: its target partition, its frames, and its manually-completed ack. */
  private record Publish(int partition, List<byte[]> frames, CompletableFuture<List<Long>> ack) {}

  private final List<Publish> publishes = new ArrayList<>();
  private EventBridgeEnvelopeTransport transport;

  @BeforeEach
  void setUp() {
    final EventBridgeClient client = mock(EventBridgeClient.class);
    when(client.newBatch()).thenAnswer(invocation -> new PendingBatch());
    transport = new EventBridgeEnvelopeTransport(client, "facts");
  }

  private static byte[] frame(final String content) {
    return content.getBytes(StandardCharsets.UTF_8);
  }

  @Test
  void shouldPipelineAllDestinationsBehindOneAcknowledgmentFuture() {
    // given frames buffered for two facts partitions
    transport.send(1, frame("a"));
    transport.send(1, frame("b"));
    transport.send(2, frame("c"));

    // when they are dispatched
    final CompletableFuture<Void> acked = transport.dispatch();

    // then both partitions are in flight at once — one batch each, frames in hand-over order
    assertThat(publishes).hasSize(2);
    assertThat(publishes)
        .anySatisfy(
            publish -> {
              assertThat(publish.partition()).isEqualTo(1);
              assertThat(publish.frames()).containsExactly(frame("a"), frame("b"));
            })
        .anySatisfy(publish -> assertThat(publish.partition()).isEqualTo(2));

    // and the future acknowledges only once every destination has
    publishes.get(0).ack().complete(List.of(0L));
    assertThat(acked).isNotDone();
    publishes.get(1).ack().complete(List.of(0L));
    assertThat(acked).isCompleted();
  }

  @Test
  void shouldChainSuccessiveDispatchesToTheSamePartition() {
    // given a dispatch to partition 1 still awaiting its acknowledgment
    transport.send(1, frame("first"));
    final CompletableFuture<Void> firstAcked = transport.dispatch();

    // when a later frame for the same partition is dispatched
    transport.send(1, frame("second"));
    final CompletableFuture<Void> secondAcked = transport.dispatch();

    // then the second publish holds until the first acknowledges — pipelined sends to one
    // destination can never arrive reordered
    assertThat(publishes).hasSize(1);
    publishes.get(0).ack().complete(List.of(0L));
    assertThat(firstAcked).isCompleted();
    assertThat(publishes).hasSize(2);
    assertThat(publishes.get(1).frames()).containsExactly(frame("second"));
    publishes.get(1).ack().complete(List.of(1L));
    assertThat(secondAcked).isCompleted();
  }

  @Test
  void shouldStillPublishBehindAFailedPredecessor() {
    // given a dispatch to partition 1 that fails
    transport.send(1, frame("failed"));
    final CompletableFuture<Void> failedAcked = transport.dispatch();
    transport.send(1, frame("retry"));
    final CompletableFuture<Void> retryAcked = transport.dispatch();
    publishes.get(0).ack().completeExceptionally(new IllegalStateException("publish failed"));

    // then the failure surfaced through its own dispatch future ...
    assertThat(failedAcked).isCompletedExceptionally();

    // ... while the chained retry still goes out, strictly after the failed attempt settled —
    // whatever the failed request actually appended, the retry's duplicates are absorbed by the
    // reducer's dedup and its order relative to newer frames is preserved
    assertThat(publishes).hasSize(2);
    publishes.get(1).ack().complete(List.of(1L));
    assertThat(retryAcked).isCompleted();
  }

  @Test
  void shouldFailTheDispatchWhenAnyDestinationFails() {
    // given frames in flight to two partitions
    transport.send(1, frame("a"));
    transport.send(2, frame("b"));
    final CompletableFuture<Void> acked = transport.dispatch();

    // when one destination fails
    publishes.get(0).ack().complete(List.of(0L));
    publishes.get(1).ack().completeExceptionally(new IllegalStateException("publish failed"));

    // then the whole dispatch fails — the cut fails and the outbox retains the frames
    assertThat(acked).isCompletedExceptionally();
  }

  @Test
  void shouldAcknowledgeImmediatelyWhenNothingIsBuffered() {
    assertThat(transport.dispatch()).isCompleted();
    assertThat(publishes).isEmpty();
  }

  /** A {@link BatchPublisher} whose publish registers a manually-completed acknowledgment. */
  private final class PendingBatch implements BatchPublisher {

    private final List<byte[]> frames = new ArrayList<>();

    @Override
    public BatchPublisher add(final String key, final byte[] value) {
      frames.add(value);
      return this;
    }

    @Override
    public BatchPublisher add(final byte[] key, final byte[] value) {
      frames.add(value);
      return this;
    }

    @Override
    public BatchPublisher add(final byte[] value) {
      frames.add(value);
      return this;
    }

    @Override
    public BatchPublisher add(final String value) {
      frames.add(value.getBytes(StandardCharsets.UTF_8));
      return this;
    }

    @Override
    public BatchPublisher keyed() {
      return this;
    }

    @Override
    public CompletableFuture<List<Long>> publishToTopic(final String topic, final int partitionId) {
      final Publish publish =
          new Publish(partitionId, List.copyOf(frames), new CompletableFuture<>());
      publishes.add(publish);
      return publish.ack();
    }
  }
}
