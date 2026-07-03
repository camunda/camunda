/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.client.internal.consumer;

import io.camunda.eventbridge.client.Consumer;
import io.camunda.eventbridge.client.ConsumerClosedException;
import io.camunda.eventbridge.client.Event;
import io.camunda.eventbridge.client.RebalanceListener;
import io.camunda.eventbridge.client.TopicPartition;
import io.camunda.eventbridge.client.internal.ClientConfig;
import io.camunda.eventbridge.client.internal.transport.HttpTransport;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Default {@link Consumer} implementation: a thin facade over four collaborators — a {@link
 * SubscriptionState} (owned partitions, fetch positions, seeks, reset resolution), a {@link
 * PrefetchBuffer} (per-partition event buffers and the poll concurrency primitives), a {@link
 * Prefetcher} (the background long-poll driver), and a {@link GroupCoordinator}
 * (join/leave/heartbeat/rejoin/commit and member id/epoch).
 *
 * <p>All public methods are thread-safe.
 */
public final class ConsumerImpl implements Consumer {

  private final String groupId;
  private final AtomicBoolean closed = new AtomicBoolean(false);

  private final SubscriptionState subscription;
  private final PrefetchBuffer buffer;
  private final Prefetcher prefetcher;
  private final GroupCoordinator coordinator;

  /**
   * Primary constructor. Consumer starts with no owned partitions and {@code memberEpoch = 0}.
   *
   * @param fetcher the fetch capability (typically the client facade)
   * @param transport the shared HTTP transport used for coordinator requests
   * @param executor the client's shared scheduled executor
   * @param config the client configuration (offset reset, fetch/prefetch tuning)
   */
  public ConsumerImpl(
      final Fetcher fetcher,
      final HttpTransport transport,
      final ScheduledExecutorService executor,
      final ClientConfig config,
      final String groupId,
      final List<String> topics,
      final String instanceId) {
    this(
        fetcher,
        transport,
        executor,
        config,
        groupId,
        topics == null ? List.of() : List.copyOf(topics),
        instanceId,
        0L,
        null);
  }

  /**
   * Convenience constructor for tests that need pre-seeded owned partitions and epoch without going
   * through a heartbeat round-trip.
   */
  public ConsumerImpl(
      final Fetcher fetcher,
      final HttpTransport transport,
      final ScheduledExecutorService executor,
      final ClientConfig config,
      final String groupId,
      final String consumerId,
      final List<TopicPartition> initialPartitions,
      final int initialEpoch) {
    this(
        fetcher,
        transport,
        executor,
        config,
        groupId,
        List.of(),
        consumerId,
        initialEpoch,
        initialPartitions);
  }

  private ConsumerImpl(
      final Fetcher fetcher,
      final HttpTransport transport,
      final ScheduledExecutorService executor,
      final ClientConfig config,
      final String groupId,
      final List<String> topics,
      final String instanceId,
      final long initialEpoch,
      final List<TopicPartition> initialPartitions) {
    this.groupId = groupId;
    subscription = new SubscriptionState(fetcher, config.offsetResetPolicy());
    buffer = new PrefetchBuffer(config.prefetchDepth(), config.maxBufferedBytes());
    prefetcher =
        new Prefetcher(
            fetcher,
            executor,
            subscription,
            buffer,
            closed::get,
            config.fetchMaxBytes(),
            config.fetchMinBytes(),
            config.longPollMs());
    coordinator =
        new GroupCoordinator(
            transport,
            executor,
            subscription,
            buffer,
            prefetcher,
            closed::get,
            groupId,
            topics,
            instanceId,
            this::checkNotClosed,
            config.heartbeatIntervalMs());
    coordinator.presetEpoch(initialEpoch);
    if (initialPartitions != null) {
      coordinator.applyOwnedPartitions(initialPartitions);
    }
  }

  @Override
  public CompletableFuture<Void> joinGroup() {
    return coordinator.joinGroup();
  }

  @Override
  public CompletableFuture<Void> leaveGroup() {
    return coordinator.leaveGroup();
  }

  @Override
  public CompletableFuture<Void> sendHeartbeat() {
    checkNotClosed();
    return coordinator.sendHeartbeat();
  }

  @Override
  public void seek(final Map<TopicPartition, Long> positions) {
    buffer.runLocked(
        () -> {
          // Invalidate any in-flight fetch started from the old cursor and drop stale buffered
          // events, so the next fetch resumes from the seeked position.
          buffer.bumpGeneration();
          positions.forEach(
              (tp, position) -> {
                subscription.recordSeek(tp, position);
                buffer.clear(tp);
              });
        });
  }

  @Override
  public void seekToBeginning(final Collection<TopicPartition> partitions) {
    buffer.runLocked(
        () -> {
          // Discard in-flight fetches and buffered events, then force each cursor to the start so
          // the next fetch replays from the beginning (rebuild).
          buffer.bumpGeneration();
          for (final TopicPartition tp : partitions) {
            subscription.rewindToBeginning(tp);
            buffer.clear(tp);
          }
        });
    prefetcher.kick();
  }

  @Override
  public void rebalanceListener(final RebalanceListener listener) {
    coordinator.setRebalanceListener(listener);
  }

  @Override
  public List<Event> poll(final int maxRecords, final Duration timeout) {
    checkNotClosed();
    prefetcher.kick(); // ensure a fetch is in flight for every empty, owned partition

    final List<Event> out = buffer.drain(maxRecords, timeout.toNanos(), closed::get);

    prefetcher.kick(); // pipeline: refill the drained partitions while the caller processes them
    return out;
  }

  @Override
  public CompletableFuture<Void> commitOffset(
      final String topic, final int partitionId, final long position) {
    checkNotClosed();
    return coordinator.commitOffset(topic, partitionId, position);
  }

  @Override
  public void close() {
    closed.set(true);
    coordinator.cancelHeartbeat();
    // Wake any poll() blocked on the buffer so it observes the closed flag and returns.
    buffer.signalLocked();
  }

  @Override
  public String getGroupId() {
    return groupId;
  }

  @Override
  public String getMemberId() {
    return coordinator.memberId();
  }

  @Override
  public long getMemberEpoch() {
    return coordinator.memberEpoch();
  }

  @Override
  public List<TopicPartition> getOwnedPartitions() {
    return Collections.unmodifiableList(subscription.ownedPartitions());
  }

  private void checkNotClosed() {
    if (closed.get()) {
      throw new ConsumerClosedException(
          "Consumer " + coordinator.memberId() + " in group " + groupId + " is closed");
    }
  }
}
