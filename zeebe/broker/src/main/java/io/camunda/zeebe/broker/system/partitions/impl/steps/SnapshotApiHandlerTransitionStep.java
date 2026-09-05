/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import io.atomix.raft.RaftServer.Role;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionContext;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionStep;
import io.camunda.zeebe.broker.transport.snapshotapi.SnapshotApiRequestHandler;
import io.camunda.zeebe.protocol.ColumnFamilyScope;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.snapshots.transfer.SnapshotTransferServiceImpl;
import java.util.Set;

/**
 * Installs a per-partition {@link SnapshotApiRequestHandler} while this partition is leader, so
 * snapshot transfer requests are always served by the partition they were addressed to — including
 * across partition groups (physical tenants) that share the same partition numbers.
 */
public class SnapshotApiHandlerTransitionStep implements PartitionTransitionStep {

  @Override
  public ActorFuture<Void> prepareTransition(
      final PartitionTransitionContext context, final long term, final Role targetRole) {
    final var handler = context.getSnapshotApiRequestHandler();
    if (handler != null) {
      final var closeFuture = handler.closeAsync();
      context.setSnapshotApiRequestHandler(null);
      return closeFuture;
    }
    return CompletableActorFuture.completed();
  }

  @Override
  public ActorFuture<Void> transitionTo(
      final PartitionTransitionContext context, final long term, final Role targetRole) {
    if (targetRole.isLeader()) {
      return installHandler(context);
    }
    return CompletableActorFuture.completed();
  }

  @Override
  public String getName() {
    return "SnapshotApiHandlerTransitionStep";
  }

  private ActorFuture<Void> installHandler(final PartitionTransitionContext context) {
    final var handler =
        new SnapshotApiRequestHandler(
            context.partitionId(),
            context.getGatewayBrokerTransport(),
            context.getBrokerClient(),
            concurrency ->
                new SnapshotTransferServiceImpl(
                    context.getPersistedSnapshotStore(),
                    context.getSnapshotDirector(),
                    context.getPartitionId(),
                    (from, to) ->
                        context
                            .snapshotCopy()
                            .copySnapshot(from, to, Set.of(ColumnFamilyScope.GLOBAL)),
                    concurrency));
    final ActorFuture<Void> installed = context.getConcurrencyControl().createFuture();
    context.getActorSchedulingService().submitActor(handler).onComplete(installed);
    installed.onComplete(
        (ignored, error) -> {
          if (error == null) {
            context.setSnapshotApiRequestHandler(handler);
          }
        });
    return installed;
  }
}
