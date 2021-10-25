/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions.impl;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

import io.atomix.raft.RaftServer.Role;
import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionContext;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionStep;
import io.camunda.zeebe.util.sched.ConcurrencyControl;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.slf4j.Logger;

final class PartitionTransitionProcess {

  private static final Logger LOG = Loggers.SYSTEM_LOGGER;

  private final List<PartitionTransitionStep> pendingSteps;
  private final Deque<PartitionTransitionStep> stepsToCleanUp = new ArrayDeque<>();
  private final ConcurrencyControl concurrencyControl;
  private final PartitionTransitionContext context;
  private final long term;
  private final Role role;
  private boolean cancelRequested = false;

  PartitionTransitionProcess(
      final List<PartitionTransitionStep> pendingSteps,
      final ConcurrencyControl concurrencyControl,
      final PartitionTransitionContext context,
      final long term,
      final Role role) {
    this.pendingSteps = new ArrayList<>(requireNonNull(pendingSteps));
    pendingSteps.forEach(stepsToCleanUp::push);
    this.concurrencyControl = requireNonNull(concurrencyControl);
    this.context = requireNonNull(context);
    this.term = term;
    this.role = requireNonNull(role);
  }

  void start(final ActorFuture<Void> future) {
    LOG.info(format("Transition to %s on term %d starting", role, term));

    if (pendingSteps.isEmpty()) {
      LOG.info("No steps defined for transition");
      future.complete(null);
      return;
    }

    proceedWithTransition(future);
  }

  private void proceedWithTransition(final ActorFuture<Void> future) {
    if (cancelRequested) {
      LOG.info(format("Cancelling transition to %s on term %d", role, term));
      future.complete(null);
      return;
    }

    concurrencyControl.run(
        () -> {
          final var nextStep = pendingSteps.remove(0);

          LOG.info(
              format("Transition to %s on term %d - executing %s", role, term, nextStep.getName()));

          nextStep
              .transitionTo(context, term, role)
              .onComplete((ok, error) -> onStepCompletion(future, error));
        });
  }

  private void onStepCompletion(final ActorFuture<Void> future, final Throwable error) {
    if (error != null) {
      LOG.error(error.getMessage(), error);
      future.completeExceptionally(error);

      return;
    }

    if (pendingSteps.isEmpty()) {
      LOG.info(format("Transition to %s on term %d completed", role, term));
      future.complete(null);

      return;
    }

    proceedWithTransition(future);
  }

  // TODO transitively rename to prepare in develop branch
  ActorFuture<Void> cleanup(final long newTerm, final Role newRole) {
    LOG.info(
        format(
            "Preparation before transition to %s on term %d starting (in preparation for new transition to %s)",
            role, term, newRole));
    final ActorFuture<Void> cleanupFuture = concurrencyControl.createFuture();

    if (stepsToCleanUp.isEmpty()) {
      LOG.info("No steps to clean up");
      cleanupFuture.complete(null);
    } else {
      proceedWithCleanup(cleanupFuture, newTerm, newRole);
    }
    return cleanupFuture;
  }

  private void proceedWithCleanup(
      final ActorFuture<Void> future, final long newTerm, final Role newRole) {
    concurrencyControl.run(
        () -> {
          final var nextCleanupStep = stepsToCleanUp.pop();

          LOG.info(
              format(
                  "Preparation before transition to %s on term %d - executing %s",
                  role, term, nextCleanupStep.getName()));

          nextCleanupStep
              .prepareTransition(context, newTerm, newRole)
              .onComplete((ok, error) -> onCleanupStepCompletion(future, error, newTerm, newRole));
        });
  }

  private void onCleanupStepCompletion(
      final ActorFuture<Void> future,
      final Throwable error,
      final long newTerm,
      final Role newRole) {
    if (error != null) {
      LOG.error(error.getMessage(), error);
      future.completeExceptionally(error);

      return;
    }

    if (stepsToCleanUp.isEmpty()) {
      LOG.info(format("Preparation before transition to %s on term %d completed", role, term));
      future.complete(null);

      return;
    }

    proceedWithCleanup(future, newTerm, newRole);
  }

  void cancel() {
    LOG.info(format("Received cancel signal for transition to %s on term %d", role, term));
    cancelRequested = true;
  }
}
