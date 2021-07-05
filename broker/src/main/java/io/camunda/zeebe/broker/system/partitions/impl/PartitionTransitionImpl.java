/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions.impl;

import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.system.partitions.PartitionContext;
import io.camunda.zeebe.broker.system.partitions.PartitionTransition;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionContext;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionStep;
import io.camunda.zeebe.protocol.record.PartitionRole;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.future.CompletableActorFuture;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;

public class PartitionTransitionImpl implements PartitionTransition {

  private static final Logger LOG = Loggers.SYSTEM_LOGGER;

  private final PartitionTransitionContext context;
  private final List<PartitionTransitionStep> transitionSteps;
  private final List<PartitionTransitionStep> openedSteps = new ArrayList<>();
  private final CompletableActorFuture<Void> currentTransition =
      CompletableActorFuture.completed(null);

  public PartitionTransitionImpl(
      final PartitionTransitionContext context,
      final List<PartitionTransitionStep> transitionSteps) {
    this.context = context;
    this.transitionSteps = transitionSteps;
  }

  @Override
  public ActorFuture<PartitionContext> toRole(
      final long currentTerm,
      final PartitionRole currentRole,
      final long nextTerm,
      final PartitionRole targetRole) {
    final var result = new CompletableActorFuture<PartitionContext>();
    // TODO stop all components

    transitionPartition(
        result,
        context,
        new ArrayList<>(transitionSteps),
        currentTerm,
        currentRole,
        nextTerm,
        targetRole);

    // TODO resume all components

    return result;
  }

  private void transitionPartition(
      final CompletableActorFuture<PartitionContext> future,
      final PartitionTransitionContext context,
      final List<PartitionTransitionStep> steps,
      final long currentTerm,
      final PartitionRole currentRole,
      final long nextTerm,
      final PartitionRole targetRole) {
    if (steps.isEmpty()) {
      LOG.debug(
          "Partition {} transition complete, installed {} resources!",
          context.getPartitionId(),
          openedSteps.size());

      context
          .getPartitionListeners()
          .forEach(
              (pl) -> {
                switch (targetRole) {
                  case FOLLOWER:
                    {
                      pl.onBecomingFollower(context.getPartitionId(), nextTerm);
                      break;
                    }
                  case LEADER:
                    {
                      pl.onBecomingLeader(
                          context.getPartitionId(), nextTerm, context.getLogStream());
                      break;
                    }
                  default:
                    {
                      pl.onBecomingInactive(context.getPartitionId(), nextTerm);
                    }
                }
              });

      future.complete(context.toPartitionContext());
      return;
    }

    final PartitionTransitionStep step = steps.remove(0);
    try {
      step.transitionTo(context, currentTerm, currentRole, nextTerm, targetRole)
          .onComplete(
              (newContext, error) -> {
                if (error != null) {
                  LOG.error("Expected to open step '{}' but failed with", step.getName(), error);
                  future.completeExceptionally(error);
                } else {
                  openedSteps.add(step);
                  transitionPartition(
                      future, newContext, steps, currentTerm, currentRole, nextTerm, targetRole);
                }
              });
    } catch (final Exception e) {
      LOG.error("Expected to open step '{}' but failed with", step.getName(), e);
      future.completeExceptionally(e);
    }
  }
}
