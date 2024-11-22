/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.scaling.redistribution;

import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.scaling.redistribution.RedistributableResource.Deployment;
import io.camunda.zeebe.engine.scaling.redistribution.RedistributionStage.Done;
import io.camunda.zeebe.engine.scaling.redistribution.ResourceRedistributor.Redistribution;
import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.RoutingState;
import io.camunda.zeebe.protocol.impl.record.value.scaling.RedistributionProgress;
import io.camunda.zeebe.protocol.impl.record.value.scaling.RedistributionRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.scaling.RedistributionIntent;
import java.util.SequencedCollection;
import org.agrona.collections.IntHashSet;

public final class RedistributionBehavior {
  private final TypedCommandWriter commandWriter;
  private final StateWriter stateWriter;
  private final CommandDistributionBehavior distributionBehavior;
  private final RoutingState routingState;
  private final ProcessingState processingState;
  private final RedistributionState redistributionState;

  public RedistributionBehavior(
      final Writers writers,
      final CommandDistributionBehavior distributionBehavior,
      final ProcessingState processingState) {
    commandWriter = writers.command();
    stateWriter = writers.state();
    this.distributionBehavior = distributionBehavior;
    routingState = processingState.getRoutingState();
    this.processingState = processingState;
    redistributionState = processingState.getRedistributionState();
  }

  public void startRedistribution(final long redistributionKey) {
    stateWriter.appendFollowUpEvent(
        redistributionKey, RedistributionIntent.STARTED, new RedistributionRecord());
    commandWriter.appendFollowUpCommand(
        redistributionKey, RedistributionIntent.CONTINUE, new RedistributionRecord());
  }

  public void continueRedistribution(final long redistributionKey) {
    // get current state and matching redistributor
    final var stage = redistributionState.getStage();
    final var progress = redistributionState.getProgress();
    final var redistributor = redistributorForCurrentStage(stage);

    // get all new redistributions
    final var redistributions = redistributor.nextRedistributions(progress);

    if (redistributions.isEmpty()) {
      advanceToNextStage(redistributionKey, progress, stage);
    } else {
      advanceInSameStage(redistributionKey, progress, stage, redistributions);
    }
  }

  private void advanceInSameStage(
      final long redistributionKey,
      final RedistributionProgress progress,
      final RedistributionStage stage,
      final SequencedCollection<? extends Redistribution<?>> redistributions) {
    // find partitions to redistribute to
    final var currentPartitions = routingState.currentPartitions();
    final var desiredPartitions = routingState.desiredPartitions();
    final var newPartitions = new IntHashSet(desiredPartitions.size());
    newPartitions.addAll(desiredPartitions);
    newPartitions.removeAll(currentPartitions);

    // enqueue all redistributions
    for (final var redistribution : redistributions) {
      distributionBehavior
          .withKey(redistribution.distributionKey())
          .inQueue(DistributionQueue.REDISTRIBUTION)
          .forPartitions(newPartitions)
          .distribute(
              redistribution.distributionValueType(),
              redistribution.distributionIntent(),
              redistribution.distributionValue());
    }

    // Update progress
    updateProgress(progress, redistributions);
    final var continuedRedistribution = new RedistributionRecord();
    continuedRedistribution.setProgress(progress);
    continuedRedistribution.setStage(RedistributionStage.stageToIndex(stage));
    stateWriter.appendFollowUpEvent(
        redistributionKey, RedistributionIntent.CONTINUED, continuedRedistribution);

    // Request continuation once this batch of redistributions is done
    distributionBehavior
        .withKey(redistributionKey)
        .afterQueue(DistributionQueue.REDISTRIBUTION)
        .continueWith(
            ValueType.REDISTRIBUTION, RedistributionIntent.CONTINUE, new RedistributionRecord());
  }

  private void advanceToNextStage(
      final long redistributionKey,
      final RedistributionProgress progress,
      final RedistributionStage currentStage) {
    final var nextStage = RedistributionStage.nextStage(currentStage);

    final var continuedRedistribution =
        new RedistributionRecord()
            .setProgress(progress)
            .setStage(RedistributionStage.stageToIndex(nextStage));

    stateWriter.appendFollowUpEvent(
        redistributionKey, RedistributionIntent.CONTINUED, continuedRedistribution);

    if (nextStage instanceof Done) {
      commandWriter.appendFollowUpCommand(
          redistributionKey, RedistributionIntent.COMPLETE, new RedistributionRecord());
    } else {
      commandWriter.appendFollowUpCommand(
          redistributionKey, RedistributionIntent.CONTINUE, new RedistributionRecord());
    }
  }

  private void updateProgress(
      final RedistributionProgress progress,
      final SequencedCollection<? extends Redistribution<?>> redistributions) {
    for (final var redistribution : redistributions) {
      for (final var resource : redistribution.containedResources()) {
        switch (resource) {
          case Deployment(final var deploymentKey) -> progress.claimDeploymentKey(deploymentKey);
        }
      }
    }
  }

  /**
   * Extend this method for each new {@link RedistributionStage}. The must be one {@link
   * ResourceRedistributor} for each stage.
   */
  private ResourceRedistributor<?, ?> redistributorForCurrentStage(
      final RedistributionStage stage) {
    return switch (stage) {
      case final RedistributionStage.Deployments ignored ->
          new DeploymentRedistributor(processingState);
      case final RedistributionStage.Done ignored -> null;
    };
  }
}
