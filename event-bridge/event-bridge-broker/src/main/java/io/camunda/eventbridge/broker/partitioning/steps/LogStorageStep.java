/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.partitioning.steps;

import io.atomix.raft.RaftCommitListener;
import io.camunda.eventbridge.broker.compaction.CompactedLogStorage;
import io.camunda.eventbridge.broker.partitioning.PartitionContext;
import io.camunda.eventbridge.broker.partitioning.PartitionStartupStep;
import io.camunda.eventbridge.protocol.request.coordination.CleanupPolicy;
import io.camunda.zeebe.broker.logstreams.AtomixLogStorage;
import io.camunda.zeebe.logstreams.storage.LogStorage;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LogStorageStep implements PartitionStartupStep {

  private static final Logger LOG = LoggerFactory.getLogger(LogStorageStep.class);

  @Override
  public String getName() {
    return "LogStorage";
  }

  @Override
  public void prepare(final PartitionContext context) {
    final var server = context.getRaftPartition().getServer();
    if (server == null) {
      throw new IllegalStateException(
          "Partition " + context.getPartitionId() + " — raft server is null");
    }
    final var appenderOpt = server.getAppender();
    if (appenderOpt.isEmpty()) {
      throw new IllegalStateException(
          "Partition " + context.getPartitionId() + " — raft appender unavailable");
    }

    LogStorage logStorage = AtomixLogStorage.ofPartition(server::openReader, appenderOpt.get());
    if (context.getCleanupPolicy() == CleanupPolicy.COMPACT) {
      // Composite fetch (event-bridge ADR 0001, decision 9): positions at or below the cleaner
      // point are served from the clean set, everything else flows through this same live-log
      // storage unchanged. A DELETE partition never wraps its storage, so it is byte-for-byte
      // unaffected.
      logStorage =
          new CompactedLogStorage(
              logStorage,
              context.getCompactionManifestStore(),
              context.getCompactionLeaseRegistry(),
              context.getCompactionDirectory());
    }
    context.setLogStorage(logStorage);
    LOG.info("Partition {} — LogStorage prepared", context.getPartitionId());
  }

  @Override
  public ActorFuture<Void> activate(final PartitionContext context) {
    final var server = context.getRaftPartition().getServer();
    // RaftCommitListener, not the concrete AtomixLogStorage: a COMPACT partition's LogStorage is
    // wrapped in CompactedLogStorage, which forwards onCommit — checking the concrete class here
    // would silently drop the registration for every COMPACT partition.
    if (server != null && context.getLogStorage() instanceof final RaftCommitListener listener) {
      server.addCommitListener(listener);
    }
    return CompletableActorFuture.completed(null);
  }

  @Override
  public ActorFuture<Void> deactivate(final PartitionContext context) {
    final var server = context.getRaftPartition().getServer();
    final var logStorage = context.getLogStorage();
    context.setLogStorage(null);

    if (server != null && logStorage instanceof final RaftCommitListener listener) {
      server.removeCommitListener(listener);
    }

    return CompletableActorFuture.completed(null);
  }
}
