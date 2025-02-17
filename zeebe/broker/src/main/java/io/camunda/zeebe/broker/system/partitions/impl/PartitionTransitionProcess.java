/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.impl;

import static java.util.Objects.requireNonNull;

import io.atomix.raft.RaftServer.Role;
import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.system.partitions.PartitionTransition.CancelledPartitionTransition;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionContext;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionStep;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.clock.ActorClock;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.util.health.HealthIssue;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;

final class PartitionTransitionProcess {

  public static final String MSG_PREPARE_TRANSITION =
      "Prepare transition from {}[term: {}] -> {}[term: {}]";
  public static final String MSG_PREPARE_TRANSITION_STEP =
      MSG_PREPARE_TRANSITION + " - preparing {}";
  public static final String MSG_PREPARE_TRANSITION_COMPLETED =
      MSG_PREPARE_TRANSITION + " completed";
  private static final Logger LOG = Loggers.SYSTEM_LOGGER;
  private static final long STEP_TIMEOUT_MS = Duration.ofSeconds(60).toMillis();
  private PartitionTransitionStep currentStep;
  private final List<PartitionTransitionStep> pendingSteps;
  private final Deque<PartitionTransitionStep> stepsToPrepare = new ArrayDeque<>();
  private final ConcurrencyControl concurrencyControl;
  private final PartitionTransitionContext context;
  private final long term;
  private final Role role;
  private boolean cancelRequested = false;
  private boolean completed = false;

  private long stepStartedAtMs = -1;

  PartitionTransitionProcess(
      final List<PartitionTransitionStep> pendingSteps,
      final ConcurrencyControl concurrencyControl,
      final PartitionTransitionContext context,
      final long term,
      final Role role) {
    this.pendingSteps = new ArrayList<>(requireNonNull(pendingSteps));
    pendingSteps.forEach(stepsToPrepare::push);
    this.concurrencyControl = requireNonNull(concurrencyControl);
    this.context = requireNonNull(context);
    context.setConcurrencyControl(concurrencyControl);
    this.term = term;
    this.role = requireNonNull(role);
  }

  void start(final ActorFuture<Void> future) {
    LOG.info("Transition to {} on term {} starting", role, term);

    if (pendingSteps.isEmpty()) {
      LOG.info("No steps defined for transition");
      future.complete(null);
      return;
    }

    proceedWithTransition(future);
  }

  private void proceedWithTransition(final ActorFuture<Void> future) {
    if (cancelRequested) {
      LOG.info("Cancelling transition to {} on term {}", role, term);
      future.completeExceptionally(new CancelledPartitionTransition());
      completed = true;
      return;
    }

    concurrencyControl.run(
        () -> {
          final var nextStep = pendingSteps.remove(0);
          currentStep = nextStep;
          stepStartedAtMs = ActorClock.currentTimeMillis();
          LOG.debug(
              "Transition to {} on term {} - transitioning {}", role, term, nextStep.getName());
          nextStep
              .transitionTo(context, term, role)
              .onComplete((ok, error) -> onStepCompletion(future, error));
        });
  }

  private void onStepCompletion(final ActorFuture<Void> future, final Throwable error) {
    if (error != null) {
      future.completeExceptionally(error);
      return;
    }

    if (pendingSteps.isEmpty()) {
      LOG.info("Transition to {} on term {} completed", role, term);
      future.complete(null);
      completed = true;
      currentStep = null;
      stepStartedAtMs = -1;
      return;
    }

    proceedWithTransition(future);
  }

  ActorFuture<Void> prepare(final long newTerm, final Role newRole) {
    LOG.info(
        MSG_PREPARE_TRANSITION,
        context.getCurrentRole(),
        context.getCurrentTerm(),
        newRole,
        newTerm);
    final ActorFuture<Void> prepareFuture = concurrencyControl.createFuture();

    if (stepsToPrepare.isEmpty()) {
      LOG.info("No steps to prepare transition");
      prepareFuture.complete(null);
    } else {
      proceedWithPrepare(prepareFuture, newTerm, newRole);
    }
    return prepareFuture;
  }

  private void proceedWithPrepare(
      final ActorFuture<Void> future, final long newTerm, final Role newRole) {
    concurrencyControl.run(
        () -> {
          final var nextPrepareStep = stepsToPrepare.pop();

          LOG.debug(
              MSG_PREPARE_TRANSITION_STEP,
              context.getCurrentRole(),
              context.getCurrentTerm(),
              newRole,
              newTerm,
              nextPrepareStep.getName());

          nextPrepareStep
              .prepareTransition(context, newTerm, newRole)
              .onComplete((ok, error) -> onPrepareStepCompletion(future, error, newTerm, newRole));
        });
  }

  private void onPrepareStepCompletion(
      final ActorFuture<Void> future,
      final Throwable error,
      final long newTerm,
      final Role newRole) {
    if (error != null) {
      LOG.error(error.getMessage(), error);
      future.completeExceptionally(error);

      return;
    }

    if (stepsToPrepare.isEmpty()) {
      LOG.info(
          MSG_PREPARE_TRANSITION_COMPLETED,
          context.getCurrentRole(),
          context.getCurrentTerm(),
          newRole,
          newTerm);
      future.complete(null);

      return;
    }

    proceedWithPrepare(future, newTerm, newRole);
  }

  void cancel() {
    if (!completed) {
      LOG.info("Received cancel signal for transition to {} on term {}", role, term);
    }
    cancelRequested = true;
  }

  boolean isCompleted() {
    return completed;
  }

  @Override
  public String toString() {
    return "PartitionTransitionProcess{"
        + "term="
        + term
        + ", role="
        + role
        + ", cancelRequested="
        + cancelRequested
        + ", completed="
        + completed
        + ", stepsToPrepare=["
        + stepsToPrepare.stream()
            .map(PartitionTransitionStep::getName)
            .collect(Collectors.joining(", "))
        + "], pendingSteps=["
        + pendingSteps.stream()
            .map(PartitionTransitionStep::getName)
            .collect(Collectors.joining(", "))
        + "]}";
  }

  public HealthIssue getHealthIssue() {
    if (currentStep != null && ActorClock.currentTimeMillis() > stepStartedAtMs + STEP_TIMEOUT_MS) {
      final var nowMillis = ActorClock.currentTimeMillis();
      final var message =
          "Transition from %s on term %s appears blocked, step %s has been running for %s"
              .formatted(
                  context.getCurrentRole(),
                  context.getCurrentTerm(),
                  currentStep.getName(),
                  Duration.ofMillis(nowMillis - stepStartedAtMs));
      return HealthIssue.of(message, Instant.ofEpochMilli(nowMillis));
    }
    return null;
  }
}
