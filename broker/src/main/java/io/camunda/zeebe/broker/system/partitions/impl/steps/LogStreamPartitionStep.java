/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import io.camunda.zeebe.broker.system.partitions.PartitionStartupAndTransitionContextImpl;
import io.camunda.zeebe.broker.system.partitions.PartitionStep;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.logstreams.storage.atomix.AtomixLogStorage;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.future.CompletableActorFuture;

public class LogStreamPartitionStep implements PartitionStep {
  @Override
  public ActorFuture<Void> open(final PartitionStartupAndTransitionContextImpl context) {
    final CompletableActorFuture<Void> openFuture = new CompletableActorFuture<>();

    final var logStorage = context.getLogStorage();
    buildLogstream(context, logStorage)
        .onComplete(
            ((logStream, err) -> {
              if (err == null) {
                context.setLogStream(logStream);

                context
                    .getComponentHealthMonitor()
                    .registerComponent(logStream.getLogName(), logStream);
                openFuture.complete(null);
              } else {
                openFuture.completeExceptionally(err);
              }
            }));

    return openFuture;
  }

  @Override
  public ActorFuture<Void> close(final PartitionStartupAndTransitionContextImpl context) {
    context.getComponentHealthMonitor().removeComponent(context.getLogStream().getLogName());
    final ActorFuture<Void> future = context.getLogStream().closeAsync();
    context.setLogStream(null);
    return future;
  }

  @Override
  public String getName() {
    return "logstream";
  }

  private ActorFuture<LogStream> buildLogstream(
      final PartitionStartupAndTransitionContextImpl context,
      final AtomixLogStorage atomixLogStorage) {
    return LogStream.builder()
        .withLogStorage(atomixLogStorage)
        .withLogName("logstream-" + context.getRaftPartition().name())
        .withNodeId(context.getNodeId())
        .withPartitionId(context.getRaftPartition().id().id())
        .withMaxFragmentSize(context.getMaxFragmentSize())
        .withActorSchedulingService(context.getActorSchedulingService())
        .buildAsync();
  }
}
