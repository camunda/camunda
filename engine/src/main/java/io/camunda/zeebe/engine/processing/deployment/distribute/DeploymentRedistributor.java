/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.deployment.distribute;

import io.zeebe.engine.processing.streamprocessor.ReadonlyProcessingContext;
import io.zeebe.engine.processing.streamprocessor.StreamProcessorLifecycleAware;
import io.zeebe.engine.state.immutable.DeploymentState;

public class DeploymentRedistributor implements StreamProcessorLifecycleAware {

  private final int partitionsCount;
  private final DeploymentDistributor deploymentDistributor;
  private final DeploymentState deploymentState;

  public DeploymentRedistributor(
      final int partitionsCount,
      final DeploymentDistributor deploymentDistributor,
      final DeploymentState deploymentState) {
    this.partitionsCount = partitionsCount;
    this.deploymentDistributor = deploymentDistributor;
    this.deploymentState = deploymentState;
  }

  @Override
  public void onRecovered(final ReadonlyProcessingContext context) {
    final var actor = context.getActor();
    final var writers = context.getWriters();

    final var deploymentDistributionBehavior =
        new DeploymentDistributionBehavior(writers, partitionsCount, deploymentDistributor, actor);

    deploymentState.foreachPendingDeploymentDistribution(
        (key, partitionId, deployment) ->
            deploymentDistributionBehavior.distributeDeploymentToPartition(
                partitionId, key, deployment));
  }
}
