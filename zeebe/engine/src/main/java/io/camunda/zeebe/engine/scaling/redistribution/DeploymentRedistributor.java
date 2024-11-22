/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.scaling.redistribution;

import io.camunda.zeebe.engine.scaling.redistribution.RedistributionStage.Deployments;
import io.camunda.zeebe.engine.state.immutable.DeploymentState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.scaling.RedistributionProgress;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import java.util.List;
import java.util.SequencedCollection;
import java.util.Set;

public final class DeploymentRedistributor
    implements ResourceRedistributor<Deployments, DeploymentRecord> {
  DeploymentState deploymentState;

  public DeploymentRedistributor(final ProcessingState processingState) {
    deploymentState = processingState.getDeploymentState();
  }

  @Override
  public SequencedCollection<Redistribution<DeploymentRecord>> nextRedistributions(
      final RedistributionProgress progress) {

    final var previousDeploymentKey = progress.getDeploymentKey();

    // TODO: Rehydrate the deployment record with all referenced resources
    final var deployment = deploymentState.nextDeployment(previousDeploymentKey);
    if (deployment == null) {
      return List.of();
    }
    final var deploymentKey = deployment.getDeploymentKey();

    // TODO: Should also contain all the resources within the deployment, i.e. processes etc.
    final var resources =
        Set.<RedistributableResource>of(new RedistributableResource.Deployment(deploymentKey));

    return List.of(
        new Redistribution<>(
            deploymentKey, ValueType.DEPLOYMENT, DeploymentIntent.CREATE, deployment, resources));
  }
}
