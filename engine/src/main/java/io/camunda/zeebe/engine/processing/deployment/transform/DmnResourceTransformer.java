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
import io.camunda.zeebe.engine.processing.deployment.transform.DeploymentTransformer.DeploymentResourceTransformer;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.state.KeyGenerator;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRecord;
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
      final DeploymentResource resource, final DeploymentRecord record) {

    final var dmnResource = new ByteArrayInputStream(resource.getResource());
    final var parsedDrg = decisionEngine.parse(dmnResource);

    if (parsedDrg.isValid()) {
      writeRecords(resource, parsedDrg);
      return Either.right(null);

    } else {
      final var failure = new Failure(parsedDrg.getFailureMessage());
      return Either.left(failure);
    }
  }

  private void writeRecords(
      final DeploymentResource resource, final ParsedDecisionRequirementsGraph parsedDrg) {

    final var decisionRequirementsKey = keyGenerator.nextKey();

    stateWriter.appendFollowUpEvent(
        decisionRequirementsKey,
        DecisionRequirementsIntent.CREATED,
        new DecisionRequirementsRecord()
            .setDecisionRequirementsKey(decisionRequirementsKey)
            .setDecisionRequirementsId(parsedDrg.getId())
            .setDecisionRequirementsName(parsedDrg.getName())
            .setDecisionRequirementsVersion(1)
            .setNamespace(parsedDrg.getNamespace())
            .setResourceName(resource.getResourceName())
            .setResource(resource.getResourceBuffer())
            .setChecksum(checksumGenerator.apply(resource)));

    parsedDrg
        .getDecisions()
        .forEach(
            decision -> {
              final var decisionKey = keyGenerator.nextKey();

              stateWriter.appendFollowUpEvent(
                  decisionKey,
                  DecisionIntent.CREATED,
                  new DecisionRecord()
                      .setDecisionKey(decisionKey)
                      .setDecisionId(decision.getId())
                      .setDecisionName(decision.getName())
                      .setVersion(1)
                      .setDecisionRequirementsId(parsedDrg.getId())
                      .setDecisionRequirementsKey(decisionRequirementsKey));
            });
  }
}
