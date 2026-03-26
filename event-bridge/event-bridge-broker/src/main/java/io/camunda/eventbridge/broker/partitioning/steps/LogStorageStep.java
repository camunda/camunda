/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.partitioning.steps;

import io.camunda.eventbridge.broker.partitioning.PartitionContext;
import io.camunda.eventbridge.broker.partitioning.PartitionStartupStep;
import io.camunda.zeebe.broker.logstreams.AtomixLogStorage;
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

    context.setLogStorage(AtomixLogStorage.ofPartition(server::openReader, appenderOpt.get()));
    LOG.info("Partition {} — LogStorage prepared", context.getPartitionId());
  }

  @Override
  public ActorFuture<Void> activate(final PartitionContext context) {
    final var server = context.getRaftPartition().getServer();
    if (server != null && context.getLogStorage() instanceof final AtomixLogStorage a) {
      server.addCommitListener(a);
    }
    return CompletableActorFuture.completed(null);
  }

  @Override
  public ActorFuture<Void> deactivate(final PartitionContext context) {
    final var server = context.getRaftPartition().getServer();
    final var logStorage = context.getLogStorage();
    context.setLogStorage(null);

    if (server != null && logStorage instanceof final AtomixLogStorage a) {
      server.removeCommitListener(a);
    }

    return CompletableActorFuture.completed(null);
  }
}
