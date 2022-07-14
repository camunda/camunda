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
import io.camunda.zeebe.broker.transport.partitionapi.InterPartitionCommandReceiverActor;
import io.camunda.zeebe.scheduler.SchedulingHints;
import io.camunda.zeebe.scheduler.future.ActorFuture;

public class InterPartitionCommandReceiverStep implements PartitionTransitionStep {

  @Override
  public ActorFuture<Void> prepareTransition(
      final PartitionTransitionContext context, final long term, final Role targetRole) {
    final var receiver = context.getPartitionCommandReceiver();
    if (receiver != null) {
      final ActorFuture<Void> closeFuture = receiver.closeAsync();
      closeFuture.onComplete((res, error) -> context.setPartitionCommandReceiver(null));
      return closeFuture;
    } else {
      return context.getConcurrencyControl().createCompletedFuture();
    }
  }

  @Override
  public ActorFuture<Void> transitionTo(
      final PartitionTransitionContext context, final long term, final Role targetRole) {
    if (targetRole == Role.LEADER && context.getPartitionCommandReceiver() == null) {
      final ActorFuture<Void> future = context.getConcurrencyControl().createFuture();

      context
          .getLogStream()
          .newLogStreamRecordWriter()
          .onComplete(
              (writer, error) -> {
                if (error != null) {
                  future.completeExceptionally(error);
                  return;
                }
                final var receiver =
                    new InterPartitionCommandReceiverActor(
                        context.getNodeId(),
                        context.getPartitionId(),
                        context.getClusterCommunicationService(),
                        writer);
                context.getActorSchedulingService().submitActor(receiver, SchedulingHints.IO_BOUND);
                context.setPartitionCommandReceiver(receiver);
                future.complete(null);
              });
      return future;
    }
    return context.getConcurrencyControl().createCompletedFuture();
  }

  @Override
  public String getName() {
    return "PartitionCommandReceiver";
  }
}
