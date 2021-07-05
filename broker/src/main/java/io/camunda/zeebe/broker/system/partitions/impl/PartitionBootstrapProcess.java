/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions.impl;

import io.camunda.zeebe.broker.system.partitions.PartitionBootstrapContext;
import io.camunda.zeebe.broker.system.partitions.PartitionBootstrapStep;
import io.camunda.zeebe.broker.system.partitions.PartitionTransition;
import io.camunda.zeebe.util.sched.future.CompletableActorFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.function.Function;

public class PartitionBootstrapProcess {

  private final PartitionBootstrapContext bootstrapContext;
  private final List<PartitionBootstrapStep> bootstrapSteps;

  private final Function<PartitionBootstrapContext, PartitionTransition> transitionFactory;

  private final Stack<PartitionBootstrapStep> startedSteps = new Stack<>();

  public PartitionBootstrapProcess(
      final PartitionBootstrapContext bootstrapContext,
      final List<PartitionBootstrapStep> bootstrapSteps,
      final Function<PartitionBootstrapContext, PartitionTransition> transitionFactory) {
    this.bootstrapContext = bootstrapContext;
    this.bootstrapSteps = new ArrayList<>(bootstrapSteps);
    this.transitionFactory = transitionFactory;
  }

  public CompletableActorFuture<PartitionTransition> bootstrap() {
    final var result = new CompletableActorFuture<PartitionTransition>();

    final var stepsToDo = new ArrayList<>(bootstrapSteps);

    proceedBootstrap(bootstrapContext, stepsToDo, result);

    return result;
  }

  private void proceedBootstrap(
      final PartitionBootstrapContext bootstrapContext,
      final ArrayList<PartitionBootstrapStep> stepsToDo,
      final CompletableActorFuture<PartitionTransition> partitionTransitionFuture) {
    if (stepsToDo.isEmpty()) {
      partitionTransitionFuture.complete(transitionFactory.apply(bootstrapContext));
    } else {
      // TODO check for abort signal
      final var nextStep = stepsToDo.remove(0);
      startedSteps.push(nextStep);
      // TODO log start
      bootstrapContext
          .getActorControl()
          .submit(
              () -> {
                final var stepFuture = nextStep.open(bootstrapContext);
                stepFuture.onComplete(
                    (updatedBootstrapContext, error) -> {
                      // TODO log end
                      if (error != null) {
                        partitionTransitionFuture.completeExceptionally(error);
                      } else {
                        proceedBootstrap(
                            updatedBootstrapContext, stepsToDo, partitionTransitionFuture);
                      }
                    });
              });
    }
  }
}
