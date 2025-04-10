/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableDecisionState;
import io.camunda.zeebe.engine.state.mutable.MutableDeploymentState;
import io.camunda.zeebe.engine.state.mutable.MutableFormState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;

public final class DeploymentReconstructedApplier
    implements TypedEventApplier<DeploymentIntent, DeploymentRecord> {
  private final MutableDeploymentState deploymentState;
  private final MutableProcessState processState;
  private final MutableFormState formState;
  private final MutableDecisionState decisionState;

  public DeploymentReconstructedApplier(final MutableProcessingState processingState) {
    deploymentState = processingState.getDeploymentState();
    processState = processingState.getProcessState();
    formState = processingState.getFormState();
    decisionState = processingState.getDecisionState();
  }

  @Override
  public void applyState(final long deploymentKey, final DeploymentRecord value) {
    deploymentState.storeDeploymentRecord(deploymentKey, value);
    for (final var processMetadata : value.processesMetadata()) {
      processState.setMissingDeploymentKey(
          processMetadata.getTenantId(), processMetadata.getKey(), deploymentKey);
    }

    for (final var formMetadata : value.formMetadata()) {
      formState.setMissingDeploymentKey(
          formMetadata.getTenantId(), formMetadata.getFormKey(), deploymentKey);
    }

    for (final var decisionMetadata : value.decisionsMetadata()) {
      decisionState.setMissingDeploymentKey(
          decisionMetadata.getTenantId(), decisionMetadata.getDecisionKey(), deploymentKey);
    }
  }
}
