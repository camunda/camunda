/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.partitioning;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Two-phase startup: prepare all steps synchronously, then activate sequentially. Each step's
 * activation future must complete before the next step begins. Deactivation waits for each step to
 * complete before proceeding to the next.
 */
final class PartitionStartupSequence {

  private static final Logger LOG = LoggerFactory.getLogger(PartitionStartupSequence.class);

  private final List<PartitionStartupStep> steps;
  private final PartitionContext context;
  private final Consumer<Runnable> actorSubmitter;
  private final BooleanSupplier shouldContinue;
  private final Runnable onCompleted;

  private int preparedSteps;
  private int activatedSteps;
  private boolean closed;
  private boolean closing;

  PartitionStartupSequence(
      final List<PartitionStartupStep> steps,
      final PartitionContext context,
      final Consumer<Runnable> actorSubmitter,
      final BooleanSupplier shouldContinue,
      final Runnable onCompleted) {
    this.steps = steps;
    this.context = context;
    this.actorSubmitter = actorSubmitter;
    this.shouldContinue = shouldContinue;
    this.onCompleted = onCompleted;
  }

  void start() {
    closed = false;
    closing = false;
    preparedSteps = 0;
    activatedSteps = 0;

    for (int i = 0; i < steps.size(); i++) {
      final var step = steps.get(i);
      try {
        LOG.info("Partition {} — preparing: {}", context.getPartitionId(), step.getName());
        step.prepare(context);
        preparedSteps = i + 1;
      } catch (final Exception e) {
        LOG.error("Partition {} — prepare failed: {}", context.getPartitionId(), step.getName(), e);
        closeAll();
        return;
      }
    }

    activateStep(0);
  }

  /**
   * Closes all activated steps in reverse order. Each step's deactivation future is awaited before
   * proceeding to the next. Calls {@code onCompleted} when all steps are closed.
   */
  void closeAll() {
    if (closed || closing) {
      return;
    }
    closing = true;

    LOG.info(
        "Partition {} — closing {} activated / {} prepared steps",
        context.getPartitionId(),
        activatedSteps,
        preparedSteps);

    deactivateStep(activatedSteps - 1);
  }

  boolean isTransitioning() {
    return !closed && (activatedSteps < preparedSteps || closing);
  }

  private void activateStep(final int index) {
    if (closed || closing) {
      return;
    }

    if (index >= steps.size()) {
      LOG.info("Partition {} — all steps activated", context.getPartitionId());
      onCompleted.run();
      return;
    }

    if (!shouldContinue.getAsBoolean()) {
      LOG.info(
          "Partition {} — activation cancelled before: {}",
          context.getPartitionId(),
          steps.get(index).getName());
      closeAll();
      return;
    }

    final var step = steps.get(index);
    LOG.info("Partition {} — activating: {}", context.getPartitionId(), step.getName());

    try {
      final var future = step.activate(context);
      future.onComplete((ok, error) -> actorSubmitter.accept(() -> onStepActivated(index, error)));
    } catch (final Exception e) {
      LOG.error("Partition {} — activation threw: {}", context.getPartitionId(), step.getName(), e);
      closeAll();
    }
  }

  private void onStepActivated(final int index, final Throwable error) {
    if (closed) {
      try {
        steps.get(index).deactivate(context);
      } catch (final Exception e) {
        LOG.warn(
            "Partition {} — error deactivating late step: {}",
            context.getPartitionId(),
            steps.get(index).getName(),
            e);
      }
      return;
    }

    if (closing) {
      // Step completed during close — count it so deactivation covers it
      activatedSteps = index + 1;
      deactivateStep(activatedSteps - 1);
      return;
    }

    if (error != null) {
      LOG.error(
          "Partition {} — activation failed: {}",
          context.getPartitionId(),
          steps.get(index).getName(),
          error);
      closeAll();
      return;
    }

    activatedSteps = index + 1;

    if (!shouldContinue.getAsBoolean()) {
      LOG.info(
          "Partition {} — activation cancelled after: {}",
          context.getPartitionId(),
          steps.get(index).getName());
      closeAll();
      return;
    }

    activateStep(index + 1);
  }

  private void deactivateStep(final int index) {
    if (index < 0) {
      closed = true;
      closing = false;
      activatedSteps = 0;
      preparedSteps = 0;
      LOG.info("Partition {} — all steps deactivated", context.getPartitionId());
      onCompleted.run();
      return;
    }

    final var step = steps.get(index);
    LOG.info("Partition {} — deactivating: {}", context.getPartitionId(), step.getName());

    try {
      final var future = step.deactivate(context);
      future.onComplete(
          (ok, error) ->
              actorSubmitter.accept(
                  () -> {
                    if (error != null) {
                      LOG.warn(
                          "Partition {} — error deactivating: {}",
                          context.getPartitionId(),
                          step.getName(),
                          error);
                    }
                    deactivateStep(index - 1);
                  }));
    } catch (final Exception e) {
      LOG.warn(
          "Partition {} — error deactivating: {}", context.getPartitionId(), step.getName(), e);
      deactivateStep(index - 1);
    }
  }
}
