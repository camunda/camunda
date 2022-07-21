/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.distribute;

import io.camunda.zeebe.engine.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.engine.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.engine.state.immutable.DeploymentState;
import java.time.Duration;

public class DeploymentRedistributor implements StreamProcessorLifecycleAware {

  public static final Duration DEPLOYMENT_REDISTRIBUTION_INTERVAL = Duration.ofSeconds(10);
  private final int partitionsCount;
  private final DeploymentDistributionCommandSender deploymentDistributionCommandSender;
  private final DeploymentState deploymentState;

  public DeploymentRedistributor(
      final int partitionsCount,
      final DeploymentDistributionCommandSender deploymentDistributionCommandSender,
      final DeploymentState deploymentState) {
    this.partitionsCount = partitionsCount;
    this.deploymentDistributionCommandSender = deploymentDistributionCommandSender;
    this.deploymentState = deploymentState;
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    final var deploymentDistributionBehavior =
        new DeploymentDistributionBehavior(
            context.getWriters(), partitionsCount, deploymentDistributionCommandSender);

    final var schedulingService = context.getScheduleService();
    schedulingService.runAtFixedRate(
        DEPLOYMENT_REDISTRIBUTION_INTERVAL,
        () ->
            deploymentState.foreachPendingDeploymentDistribution(
                deploymentDistributionBehavior::distributeDeploymentToPartition));
  }
}
