/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.transform;

import io.camunda.zeebe.dmn.DecisionEngine;
import io.camunda.zeebe.dmn.DecisionEngineFactory;
import io.camunda.zeebe.dmn.ParsedDecisionRequirementsGraph;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.state.KeyGenerator;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRequirementsMetadataRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRequirementsRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentResource;
import io.camunda.zeebe.protocol.record.intent.DecisionIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionRequirementsIntent;
import io.camunda.zeebe.util.Either;
import java.io.ByteArrayInputStream;
import java.util.function.Function;
import org.agrona.DirectBuffer;

public final class DmnResourceTransformer implements DeploymentResourceTransformer {

  private final DecisionEngine decisionEngine = DecisionEngineFactory.createDecisionEngine();

  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final Function<DeploymentResource, DirectBuffer> checksumGenerator;

  public DmnResourceTransformer(
      final KeyGenerator keyGenerator,
      final StateWriter stateWriter,
      final Function<DeploymentResource, DirectBuffer> checksumGenerator) {
    this.keyGenerator = keyGenerator;
    this.stateWriter = stateWriter;
    this.checksumGenerator = checksumGenerator;
  }

  @Override
  public Either<Failure, Void> transformResource(
      final DeploymentResource resource, final DeploymentRecord deployment) {

    final var dmnResource = new ByteArrayInputStream(resource.getResource());
    final var parsedDrg = decisionEngine.parse(dmnResource);

    if (parsedDrg.isValid()) {
      appendMetadataToDeploymentEvent(resource, parsedDrg, deployment);
      writeRecords(deployment, resource);
      return Either.right(null);

    } else {
      final var failure = new Failure(parsedDrg.getFailureMessage());
      return Either.left(failure);
    }
  }

  private void appendMetadataToDeploymentEvent(
      final DeploymentResource resource,
      final ParsedDecisionRequirementsGraph parsedDrg,
      final DeploymentRecord deploymentEvent) {

    final var decisionRequirementsKey = keyGenerator.nextKey();
    final var checksum = checksumGenerator.apply(resource);

    deploymentEvent
        .decisionRequirementsMetadata()
        .add()
        .setDecisionRequirementsKey(decisionRequirementsKey)
        .setDecisionRequirementsId(parsedDrg.getId())
        .setDecisionRequirementsName(parsedDrg.getName())
        .setDecisionRequirementsVersion(1)
        .setNamespace(parsedDrg.getNamespace())
        .setResourceName(resource.getResourceName())
        .setChecksum(checksum);

    parsedDrg
        .getDecisions()
        .forEach(
            decision -> {
              final var decisionKey = keyGenerator.nextKey();

              deploymentEvent
                  .decisionsMetadata()
                  .add()
                  .setDecisionKey(decisionKey)
                  .setDecisionId(decision.getId())
                  .setDecisionName(decision.getName())
                  .setVersion(1)
                  .setDecisionRequirementsId(parsedDrg.getId())
                  .setDecisionRequirementsKey(decisionRequirementsKey);
            });
  }

  private void writeRecords(final DeploymentRecord deployment, final DeploymentResource resource) {

    for (DecisionRequirementsMetadataRecord drg : deployment.decisionRequirementsMetadata()) {
      stateWriter.appendFollowUpEvent(
          drg.getDecisionRequirementsKey(),
          DecisionRequirementsIntent.CREATED,
          new DecisionRequirementsRecord()
              .setDecisionRequirementsKey(drg.getDecisionRequirementsKey())
              .setDecisionRequirementsId(drg.getDecisionRequirementsId())
              .setDecisionRequirementsName(drg.getDecisionRequirementsName())
              .setDecisionRequirementsVersion(drg.getDecisionRequirementsVersion())
              .setNamespace(drg.getNamespace())
              .setResourceName(drg.getResourceName())
              .setChecksum(drg.getChecksumBuffer())
              .setResource(resource.getResourceBuffer()));
    }

    for (DecisionRecord decision : deployment.decisionsMetadata()) {
      stateWriter.appendFollowUpEvent(
          decision.getDecisionKey(),
          DecisionIntent.CREATED,
          new DecisionRecord()
              .setDecisionKey(decision.getDecisionKey())
              .setDecisionId(decision.getDecisionId())
              .setDecisionName(decision.getDecisionName())
              .setVersion(decision.getVersion())
              .setDecisionRequirementsId(decision.getDecisionRequirementsId())
              .setDecisionRequirementsKey(decision.getDecisionRequirementsKey()));
    }
  }
}
