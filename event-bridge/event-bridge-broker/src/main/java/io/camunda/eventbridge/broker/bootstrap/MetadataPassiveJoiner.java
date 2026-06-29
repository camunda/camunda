/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.bootstrap;

import io.atomix.primitive.partition.impl.DefaultPartitionManagementService;
import io.atomix.raft.partition.RaftPartition;
import io.camunda.zeebe.snapshots.ReceivableSnapshotStore;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Joins the metadata Raft group as a passive observer, <b>retrying until it succeeds</b>. A passive
 * join commits a configuration change, which needs the group to already have a leader; at startup
 * it races the voting members' election (every member answers {@code NO_LEADER} until one wins) and
 * Atomix's {@code joinWithRetry} gives up after a single pass over the members ("Sent join request
 * to all known members, but all failed"). We recover the same way the broker's other startup
 * interactions with the metadata leader do (see {@code BrokerRegistrar}'s register-until-leader
 * loop, and Zeebe's {@code ClusterConfigurationManager} operation retry): keep retrying until the
 * leader is reachable.
 *
 * <p>Each retry first {@link RaftPartition#close() closes} the half-started server so its Raft
 * message subjects are unregistered, letting the next attempt's fresh server re-register them; only
 * the Raft server is rebuilt, while the metadata partition actor and its state DB (created once by
 * the caller) are reused. The {@code onJoined} callback — which wires the role-change listener — is
 * invoked only on the attempt that succeeds.
 *
 * <p>Retries are scheduled on the supplied executor; the {@code closing} supplier lets in-flight
 * retries bail when the broker is shutting down rather than scheduling more work.
 */
final class MetadataPassiveJoiner {

  private static final Logger LOG = LoggerFactory.getLogger(MetadataPassiveJoiner.class);

  private final int partitionId;
  private final RaftPartition raftPartition;
  private final ReceivableSnapshotStore snapshotStore;
  private final DefaultPartitionManagementService managementService;
  private final ExecutorService executorService;
  private final BooleanSupplier closing;
  private final Runnable onJoined;

  MetadataPassiveJoiner(
      final int partitionId,
      final RaftPartition raftPartition,
      final ReceivableSnapshotStore snapshotStore,
      final DefaultPartitionManagementService managementService,
      final ExecutorService executorService,
      final BooleanSupplier closing,
      final Runnable onJoined) {
    this.partitionId = partitionId;
    this.raftPartition = raftPartition;
    this.snapshotStore = snapshotStore;
    this.managementService = managementService;
    this.executorService = executorService;
    this.closing = closing;
    this.onJoined = onJoined;
  }

  /** Starts the passive join, retrying until it succeeds or the broker begins shutting down. */
  void start() {
    attempt(0);
  }

  private void attempt(final int attempt) {
    if (closing.getAsBoolean()) {
      return;
    }
    raftPartition
        .joinAsPassive(managementService, snapshotStore)
        .whenComplete(
            (rp, error) -> {
              if (error == null) {
                LOG.info("Metadata raft partition {} observed (passive)", partitionId);
                onJoined.run();
                return;
              }
              if (closing.getAsBoolean()) {
                return;
              }
              final var delay = retryDelay(attempt);
              LOG.warn(
                  "Failed to observe (passive join) metadata raft partition {} (attempt {}); the "
                      + "group may have no leader yet — retrying in {}",
                  partitionId,
                  attempt + 1,
                  delay,
                  error);
              // Unregister the half-started server's Raft subjects before the next attempt
              // re-creates them; otherwise the rebuilt server collides with the previous
              // registration.
              raftPartition
                  .close()
                  .whenComplete(
                      (ignored, closeError) -> {
                        if (closeError != null) {
                          LOG.warn(
                              "Error closing metadata raft partition {} before passive-join retry",
                              partitionId,
                              closeError);
                        }
                        scheduleRetry(attempt + 1, delay);
                      });
            });
  }

  private void scheduleRetry(final int attempt, final Duration delay) {
    if (closing.getAsBoolean() || executorService.isShutdown()) {
      return;
    }
    try {
      CompletableFuture.runAsync(
          () -> attempt(attempt),
          CompletableFuture.delayedExecutor(
              delay.toMillis(), TimeUnit.MILLISECONDS, executorService));
    } catch (final RejectedExecutionException e) {
      LOG.debug("Not scheduling metadata passive-join retry — executor is shutting down");
    }
  }

  /** Capped exponential backoff for passive-join retries: 1s, 2s, 4s, then 8s thereafter. */
  private static Duration retryDelay(final int attempt) {
    return Duration.ofSeconds(1L << Math.min(attempt, 3));
  }
}
