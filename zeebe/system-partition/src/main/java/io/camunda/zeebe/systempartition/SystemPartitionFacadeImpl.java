/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.systempartition;

import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.partition.RaftPartition;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.logstreams.log.LogStreamWriter.WriteFailure;
import io.camunda.zeebe.logstreams.log.WriteContext;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.clusterconfiguration.ClusterConfigurationRecord;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ClusterConfigurationIntent;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.Either;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link SystemPartition} implementation backed by a real {@code StreamProcessor}.
 *
 * <p>Writes go through a {@link LogStreamWriter} on the leader; on followers, {@link
 * #submitCommand} fails with {@link NotLeaderException}. Reads come from a cached snapshot of the
 * persisted {@link ClusterConfiguration}, refreshed by {@link SystemPartitionMirror} on every
 * committed cluster-configuration event.
 *
 * <p>Pending command futures are correlated to their resulting events through {@link
 * PendingRequests}, keyed by a UUID stored in {@link ClusterConfigurationRecord#getRequestId()}.
 * The mirror calls {@link PendingRequests#resolve(String, ClusterConfigurationRecord)} for every
 * committed event.
 */
public final class SystemPartitionFacadeImpl implements SystemPartition {

  private static final Logger LOG = LoggerFactory.getLogger(SystemPartitionFacadeImpl.class);

  private final RaftPartition raftPartition;
  private final LogStreamWriter logStreamWriter;

  private final CopyOnWriteArrayList<Consumer<Boolean>> leaderListeners =
      new CopyOnWriteArrayList<>();
  private final CopyOnWriteArrayList<Consumer<ClusterConfiguration>> configListeners =
      new CopyOnWriteArrayList<>();

  private volatile ClusterConfiguration cached = ClusterConfiguration.uninitialized();

  public SystemPartitionFacadeImpl(
      final RaftPartition raftPartition, final LogStreamWriter logStreamWriter) {
    this.raftPartition = raftPartition;
    this.logStreamWriter = logStreamWriter;
  }

  @Override
  public ClusterConfiguration query() {
    return cached;
  }

  @Override
  public boolean isLeader() {
    return raftPartition != null && raftPartition.getRole() == Role.LEADER;
  }

  @Override
  public void addLeaderListener(final Consumer<Boolean> listener) {
    leaderListeners.add(listener);
  }

  @Override
  public void addClusterConfigListener(final Consumer<ClusterConfiguration> listener) {
    configListeners.add(listener);
  }

  @Override
  public ActorFuture<ClusterConfigurationRecord> submitCommand(
      final ClusterConfigurationIntent intent, final ClusterConfigurationRecord record) {
    final CompletableActorFuture<ClusterConfigurationRecord> future =
        new CompletableActorFuture<>();

    if (!isLeader()) {
      future.completeExceptionally(
          new NotLeaderException(
              "Local replica is not the leader of the system partition; cannot accept "
                  + intent
                  + " commands"));
      return future;
    }

    if (record.getRequestId() == null || record.getRequestId().isEmpty()) {
      record.setRequestId(UUID.randomUUID().toString());
    }

    PendingRequests.register(record.getRequestId(), future);

    final RecordMetadata metadata =
        new RecordMetadata()
            .recordType(RecordType.COMMAND)
            .valueType(ValueType.CLUSTER_CONFIGURATION)
            .intent(intent);

    final LogAppendEntry entry = LogAppendEntry.of(metadata, record);
    final Either<WriteFailure, Long> result =
        logStreamWriter.tryWrite(WriteContext.userCommand(intent), entry);

    if (result.isLeft()) {
      PendingRequests.cancel(record.getRequestId());
      future.completeExceptionally(
          new IllegalStateException(
              "Failed to write "
                  + intent
                  + " command to system-partition log: "
                  + result.getLeft()));
    }
    return future;
  }

  /**
   * Invoked by {@link SystemPartitionMirror} on every committed cluster-configuration event;
   * refreshes the cached snapshot and notifies listeners.
   */
  public void applyCommit(final ClusterConfiguration newConfig) {
    if (newConfig == null) {
      return;
    }
    cached = newConfig;
    for (final var listener : configListeners) {
      try {
        listener.accept(newConfig);
      } catch (final Exception e) {
        LOG.warn("System partition cluster-config listener {} threw", listener, e);
      }
    }
  }

  /**
   * Invoked by the broker's {@link io.atomix.raft.RaftRoleChangeListener} for the system partition;
   * notifies leader listeners and fails any pending requests on stepdown.
   */
  public void notifyLeaderChange(final boolean isLeader) {
    LOG.debug(
        "System partition leader change for {}: isLeader={}", raftPartition.id().id(), isLeader);
    if (!isLeader) {
      // Drop pending requests so callers can retry against the new leader. We don't know which
      // requests our own append produced, so we fail all of them — the worst case is a duplicate
      // submit on retry, which is fine because requestId is unique per attempt.
      PendingRequests.failAll(
          new NotLeaderException("Lost leadership of the system partition before commit"));
    }
    for (final var listener : leaderListeners) {
      try {
        listener.accept(isLeader);
      } catch (final Exception e) {
        LOG.warn("System partition leader listener {} threw", listener, e);
      }
    }
  }

  /**
   * Static registry of in-flight {@link #submitCommand} futures keyed by {@code requestId}.
   *
   * <p>Static rather than per-facade so the {@link SystemPartitionMirror} on followers can resolve
   * futures registered on the same broker without holding a reference to the facade — and so that
   * the lookup is cheap and safe across actor threads. {@code submitCommand} is gated by leadership
   * anyway, so cross-broker collisions on requestId (UUID-keyed) are not a concern.
   */
  public static final class PendingRequests {

    private static final Map<String, CompletableActorFuture<ClusterConfigurationRecord>> PENDING =
        new ConcurrentHashMap<>();

    private PendingRequests() {}

    static void register(
        final String requestId, final CompletableActorFuture<ClusterConfigurationRecord> future) {
      PENDING.put(requestId, future);
    }

    static void cancel(final String requestId) {
      PENDING.remove(requestId);
    }

    /**
     * Resolves the pending future for the given {@code requestId} (if any) by completing it with
     * the committed event record. Called from the mirror for every committed cluster-configuration
     * event.
     */
    public static void resolve(final String requestId, final ClusterConfigurationRecord record) {
      if (requestId == null || requestId.isEmpty()) {
        return;
      }
      final var future = PENDING.remove(requestId);
      if (future != null) {
        future.complete(record);
      }
    }

    static void failAll(final Throwable error) {
      // Drain by snapshotting keys to avoid concurrent-modification surprises.
      for (final var requestId : PENDING.keySet().toArray(new String[0])) {
        final var future = PENDING.remove(requestId);
        if (future != null) {
          future.completeExceptionally(error);
        }
      }
    }
  }
}
