/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment;

import io.camunda.zeebe.engine.state.immutable.DeploymentState;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.stream.api.scheduling.Task;
import io.camunda.zeebe.stream.api.scheduling.TaskResult;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DeploymentReconstructionStarter implements StreamProcessorLifecycleAware, Task {

  private static final Logger LOG = LoggerFactory.getLogger(DeploymentReconstructionStarter.class);
  private final DeploymentState deploymentState;

  public DeploymentReconstructionStarter(final DeploymentState deploymentState) {
    this.deploymentState = deploymentState;
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    if (deploymentState.hasStoredAllDeployments()) {
      LOG.trace("All deployments are already stored, skipping reconstruction");
      return;
    }

    context.getScheduleService().runDelayed(Duration.ZERO, this);
  }

  @Override
  public TaskResult execute(final TaskResultBuilder taskResultBuilder) {
    LOG.debug("Not all deployments are stored, starting reconstruction");
    return taskResultBuilder.build();
  }
}
