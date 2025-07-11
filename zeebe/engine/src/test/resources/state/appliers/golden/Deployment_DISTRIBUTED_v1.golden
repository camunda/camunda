/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapArray;
import static java.util.function.Predicate.not;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableDecisionState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessState;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRequirementsMetadataRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRequirementsRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRequirementsMetadataValue;
import io.camunda.zeebe.protocol.record.value.deployment.DeploymentResource;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

public class DeploymentDistributedApplier
    implements TypedEventApplier<DeploymentIntent, DeploymentRecord> {

  private final MutableProcessState mutableProcessState;
  private final MutableDecisionState decisionState;

  public DeploymentDistributedApplier(
      final MutableProcessState mutableProcessState, final MutableDecisionState decisionState) {
    this.mutableProcessState = mutableProcessState;
    this.decisionState = decisionState;
  }

  @Override
  public void applyState(final long key, final DeploymentRecord value) {
    mutableProcessState.putDeployment(value);

    putDmnResourcesInState(value);
  }

  private void putDmnResourcesInState(final DeploymentRecord value) {
    value.decisionRequirementsMetadata().stream()
        .filter(not(DecisionRequirementsMetadataRecord::isDuplicate))
        .forEach(
            drg -> {
              final var resource = getResourceByName(value, drg.getResourceName());
              final var decisionRequirementsRecord =
                  createDecisionRequirementsRecord(drg, resource);
              decisionState.storeDecisionRequirements(decisionRequirementsRecord);
            });

    value.decisionsMetadata().stream()
        .filter(not(DecisionRecord::isDuplicate))
        .forEach(decisionState::storeDecisionRecord);
  }

  private DirectBuffer getResourceByName(
      final DeploymentRecord deployment, final String resourceName) {
    return deployment.getResources().stream()
        .filter(resource -> resource.getResourceName().equals(resourceName))
        .map(DeploymentResource::getResource)
        .map(BufferUtil::wrapArray)
        .findFirst()
        .orElseThrow(() -> new NoSuchResourceException(resourceName));
  }

  private DecisionRequirementsRecord createDecisionRequirementsRecord(
      final DecisionRequirementsMetadataValue drg, final DirectBuffer resource) {
    return new DecisionRequirementsRecord()
        .setDecisionRequirementsKey(drg.getDecisionRequirementsKey())
        .setDecisionRequirementsId(drg.getDecisionRequirementsId())
        .setDecisionRequirementsVersion(drg.getDecisionRequirementsVersion())
        .setDecisionRequirementsName(drg.getDecisionRequirementsName())
        .setNamespace(drg.getNamespace())
        .setResourceName(drg.getResourceName())
        .setChecksum(wrapArray(drg.getChecksum()))
        .setResource(resource)
        .setTenantId(drg.getTenantId());
  }

  private static final class NoSuchResourceException extends IllegalStateException {
    private NoSuchResourceException(final String resourceName) {
      super(
          String.format(
              "Expected to find resource '%s' in deployment but not found", resourceName));
    }
  }
}
