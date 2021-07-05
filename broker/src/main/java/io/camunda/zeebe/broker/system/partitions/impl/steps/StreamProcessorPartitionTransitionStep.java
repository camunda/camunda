/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import io.camunda.zeebe.broker.system.partitions.PartitionTransitionContext;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionStep;
import io.camunda.zeebe.protocol.record.PartitionRole;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.future.CompletableActorFuture;

public class StreamProcessorPartitionTransitionStep implements PartitionTransitionStep {

  @Override
  public ActorFuture<PartitionTransitionContext> transitionTo(
      final PartitionTransitionContext context,
      final long currentTerm,
      final PartitionRole currentRole,
      final long nextTerm,
      final PartitionRole targetRole) {

    final var streamProcessor = context.getStreamProcessor();

    switch (targetRole) {
      case LEADER:
        {
          if (!context.shouldProcess()) {
            streamProcessor.pauseProcessing();
          } else {
            streamProcessor.resumeProcessing();
          }
          break;
        }
      default:
        {
          streamProcessor.pauseProcessing();
        }
    }

    return CompletableActorFuture.completed(context);
  }

  @Override
  public String getName() {
    return "StreamProcessor";
  }
}
