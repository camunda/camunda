/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import io.atomix.raft.RaftServer.Role;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionContext;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionStep;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.logstreams.log.LogStreamBuilder;
import io.camunda.zeebe.logstreams.storage.atomix.AtomixLogStorage;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.future.CompletableActorFuture;
import java.util.function.Supplier;

public final class LogStreamPartitionTransitionStep implements PartitionTransitionStep {

  private final Supplier<LogStreamBuilder> logStreamBuilderSupplier;

  public LogStreamPartitionTransitionStep() {
    this(LogStream::builder);
  }

  // Used for testing
  LogStreamPartitionTransitionStep(final Supplier<LogStreamBuilder> logStreamBuilderSupplier) {
    this.logStreamBuilderSupplier = logStreamBuilderSupplier;
  }

  @Override
  public ActorFuture<Void> prepareTransition(
      final PartitionTransitionContext context, final long term, final Role targetRole) {
    final var logStream = context.getLogStream();
    if (logStream != null
        && (shouldInstallOnTransition(targetRole, context.getCurrentRole())
            || targetRole == Role.INACTIVE)) {
      context.getComponentHealthMonitor().removeComponent(logStream.getLogName());
      final ActorFuture<Void> future = logStream.closeAsync();
      future.onComplete(
          (ok, error) -> {
            if (error == null) {
              context.setLogStream(null);
            }
          });

      return future;
    } else {
      return CompletableActorFuture.completed(null);
    }
  }

  @Override
  public ActorFuture<Void> transitionTo(
      final PartitionTransitionContext context, final long term, final Role targetRole) {
    if ((context.getLogStream() == null && targetRole != Role.INACTIVE)
        || shouldInstallOnTransition(targetRole, context.getCurrentRole())) {
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
    } else {
      return CompletableActorFuture.completed(null);
    }
  }

  @Override
  public String getName() {
    return "LogStream";
  }

  private ActorFuture<LogStream> buildLogstream(
      final PartitionTransitionContext context, final AtomixLogStorage atomixLogStorage) {
    return logStreamBuilderSupplier
        .get()
        .withLogStorage(atomixLogStorage)
        .withLogName("logstream-" + context.getRaftPartition().name())
        .withNodeId(context.getNodeId())
        .withPartitionId(context.getPartitionId())
        .withMaxFragmentSize(context.getMaxFragmentSize())
        .withActorSchedulingService(context.getActorSchedulingService())
        .buildAsync();
  }

  private boolean shouldInstallOnTransition(final Role newRole, final Role currentRole) {
    return newRole == Role.LEADER
        || (newRole == Role.FOLLOWER && currentRole != Role.CANDIDATE)
        || (newRole == Role.CANDIDATE && currentRole != Role.FOLLOWER);
  }
}
