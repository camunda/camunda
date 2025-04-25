/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment;

import io.camunda.zeebe.engine.state.immutable.DeploymentState;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.stream.api.scheduling.Task;
import io.camunda.zeebe.stream.api.scheduling.TaskResult;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
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

    // TODO: Re-enable this when we have time to finish deployment reconstruction
    // context.getScheduleService().runDelayed(Duration.ZERO, this);
  }

  @Override
  public TaskResult execute(final TaskResultBuilder taskResultBuilder) {
    LOG.debug("Not all deployments are stored, starting reconstruction");
    // There's a chance that this command is written more than once, for example when the broker is
    // restarted before the first command is processed.
    // This is fine as long as the command is idempotent and does not contain any state. Do not
    // start to add more state to the command or make the command non-idempotent unless you also
    // find a way to prevent that this initial command is written more than once.

    taskResultBuilder.appendCommandRecord(
        DeploymentIntent.RECONSTRUCT, DeploymentRecord.emptyCommandForReconstruction());
    return taskResultBuilder.build();
  }
}
