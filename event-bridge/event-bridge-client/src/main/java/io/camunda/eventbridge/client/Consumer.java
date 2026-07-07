/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.client;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Consumer handle returned by {@link EventBridgeClient#subscribe(String, String, List)}.
 *
 * <p>Maintains a consumer's group membership with the coordinator (join/leave/heartbeat) and keeps
 * a background long-poll fetch in flight per owned partition so {@link #poll(int, Duration)}
 * returns from an in-memory buffer without itself hitting the network. Call {@link
 * #sendHeartbeat()} periodically to maintain group membership and receive partition assignment
 * deltas or full reconciliation signals from the coordinator.
 *
 * <p>All methods are thread-safe.
 */
public interface Consumer extends AutoCloseable {

  /** Registers this consumer with the coordinator and starts the heartbeat loop. */
  CompletableFuture<Void> joinGroup();

  /** Leaves the group, dropping owned partitions and stopping the heartbeat loop. */
  CompletableFuture<Void> leaveGroup();

  /** Sends a single heartbeat, applying any assignment change carried in the response. */
  CompletableFuture<Void> sendHeartbeat();

  /**
   * Requests that the next fetch for each given (topic, partition) start at the supplied position,
   * resuming from a caller-checkpointed offset rather than the coordinator's committed offset or
   * the reset policy. Pass the position <em>after</em> the last record the caller durably processed
   * (i.e. {@code lastProcessed + 1}).
   *
   * <p>Safe to call before or after the partition is assigned: it takes effect immediately for
   * already-owned partitions and is remembered for partitions assigned later. A later
   * coordinator-committed offset never rewinds it (positions only advance via {@code max}), so a
   * caller whose durable checkpoint is ahead of the committed offset resumes from the checkpoint.
   */
  void seek(Map<TopicPartition, Long> positions);

  /**
   * Rewinds the given (topic, partition)s to the start of the log, so the next fetch replays from
   * the beginning regardless of any committed or checkpointed offset. Unlike {@link #seek}, this
   * forces the position backwards — used to rebuild per-partition state from the source when a
   * partition is (re)assigned to a member that has no local state for it.
   */
  void seekToBeginning(Collection<TopicPartition> partitions);

  /**
   * Pauses fetching and delivery for the given (topic, partition)s. Idempotent. A paused partition
   * is excluded from new background fetches and from {@link #poll(int, Duration)}'s drain;
   * already-buffered events (including a fetch in flight when the pause was marked) are
   * <em>retained</em> and delivered after {@link #resume(Collection)}. The fetch position is
   * untouched. A pause mark is dropped when the partition is revoked on a rebalance, but survives
   * {@link #seek(Map)} and {@link #seekToBeginning(Collection)}.
   */
  void pause(Collection<TopicPartition> partitions);

  /**
   * Resumes fetching and delivery for the given (topic, partition)s, waking a poll parked while
   * only paused data was buffered. Idempotent; partitions that are not paused (or not owned) are
   * ignored.
   */
  void resume(Collection<TopicPartition> partitions);

  /** Returns a snapshot of the (topic, partition)s currently paused via {@link #pause}. */
  Set<TopicPartition> paused();

  /**
   * Registers a listener notified when this consumer's partition assignment changes on a rebalance.
   * Replaces any previously registered listener; pass {@code null} to clear. Set it before {@link
   * #joinGroup}/first heartbeat so the initial assignment is observed.
   */
  void rebalanceListener(RebalanceListener listener);

  /**
   * Returns the next batch of prefetched events across all owned (topic, partition)s, in sorted
   * partition order, blocking up to {@code timeout} for the background prefetcher to deliver at
   * least one record.
   *
   * <p>This does not itself hit the network: a background driver keeps a long-poll fetch in flight
   * per owned partition and fills a per-partition buffer, so an idle poll parks on the broker (no
   * spin), a multi-partition consumer returns as soon as <em>any</em> partition delivers, and the
   * next fetch overlaps the caller's processing of this batch (pipelining).
   *
   * @param maxRecords maximum total number of records to return across all partitions
   * @param timeout maximum time to wait for records to become available
   * @return list of events fetched (may be empty if none arrived within {@code timeout})
   * @throws ConsumerClosedException if {@link #close()} has been called
   */
  List<Event> poll(int maxRecords, Duration timeout);

  /**
   * Commits the consumed offset for the given (topic, partition).
   *
   * <p>If the coordinator reports that this consumer is no longer registered (HTTP 404), the client
   * automatically rejoins and retries the commit exactly once. If the single retry also fails, the
   * exception is propagated to the caller.
   *
   * @param topic topic the partition belongs to
   * @param partitionId partition to commit
   * @param position the log position that has been fully processed
   * @return a future that completes when the commit is acknowledged
   */
  CompletableFuture<Void> commitOffset(String topic, int partitionId, long position);

  /**
   * Closes this consumer handle. Idempotent. After close, all method calls throw {@link
   * ConsumerClosedException}. Declared without a checked exception to satisfy {@link AutoCloseable}
   * for try-with-resources use.
   */
  @Override
  void close();

  /** Returns the consumer group id. */
  String getGroupId();

  /** Returns the member id assigned by the coordinator, or {@code null} before a join. */
  String getMemberId();

  /**
   * Returns the epoch from the last successful heartbeat response ({@code 0} if never received).
   */
  long getMemberEpoch();

  /** Returns an unmodifiable snapshot of the (topic, partition)s currently owned. */
  List<TopicPartition> getOwnedPartitions();
}
