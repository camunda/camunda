/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.transform;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;

import io.camunda.zeebe.dmn.DecisionEngine;
import io.camunda.zeebe.dmn.DecisionEngineFactory;
import io.camunda.zeebe.dmn.ParsedDecision;
import io.camunda.zeebe.dmn.ParsedDecisionRequirementsGraph;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.state.deployment.DeployedDrg;
import io.camunda.zeebe.engine.state.deployment.PersistedDecision;
import io.camunda.zeebe.engine.state.immutable.DecisionState;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRequirementsRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentResource;
import io.camunda.zeebe.protocol.record.intent.DecisionIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionRequirementsIntent;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRequirementsMetadataValue;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.io.ByteArrayInputStream;
import java.util.Collection;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;

public final class DmnResourceTransformer implements DeploymentResourceTransformer {

  private static final int INITIAL_VERSION = 1;

  private static final long UNKNOWN_DECISION_REQUIREMENTS_KEY = -1L;

  private static final Either<Failure, Object> NO_DUPLICATES = Either.right(null);

  private final DecisionEngine decisionEngine = DecisionEngineFactory.createDecisionEngine();

  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final Function<byte[], DirectBuffer> checksumGenerator;
  private final DecisionState decisionState;

  public DmnResourceTransformer(
      final KeyGenerator keyGenerator,
      final StateWriter stateWriter,
      final Function<byte[], DirectBuffer> checksumGenerator,
      final DecisionState decisionState) {
    this.keyGenerator = keyGenerator;
    this.stateWriter = stateWriter;
    this.checksumGenerator = checksumGenerator;
    this.decisionState = decisionState;
  }

  @Override
  public Either<Failure, Void> createMetadata(
      final DeploymentResource resource, final DeploymentRecord deployment) {

    final var dmnResource = new ByteArrayInputStream(resource.getResource());
    final var parsedDrg = decisionEngine.parse(dmnResource);

    if (parsedDrg.isValid()) {
      return checkForDuplicateIds(resource, parsedDrg, deployment)
          .map(
              noDuplicates -> {
                appendMetadataToDeploymentEvent(resource, parsedDrg, deployment);
                return null;
              });

    } else {
      final var failure =
          String.format("'%s': %s", resource.getResourceName(), parsedDrg.getFailureMessage());
      return Either.left(new Failure(failure));
    }
  }

  @Override
  public Either<Failure, Void> writeRecords(
      final DeploymentResource resource, final DeploymentRecord deployment) {
    if (deployment.hasDuplicatesOnly()) {
      return Either.right(null);
    }
    final var checksum = checksumGenerator.apply(resource.getResource());
    deployment.decisionRequirementsMetadata().stream()
        .filter(drg -> checksum.equals(drg.getChecksumBuffer()))
        .findFirst()
        .ifPresent(
            drg -> {
              var drgKey = drg.getDecisionRequirementsKey();
              if (drg.isDuplicate()) {
                // create new version as the deployment contains at least one other non-duplicate
                // resource and all resources in a deployment should be versioned together
                drgKey = keyGenerator.nextKey();
                drg.setDecisionRequirementsKey(drgKey)
                    .setDecisionRequirementsVersion(drg.getDecisionRequirementsVersion() + 1)
                    .setDuplicate(false);
              }
              stateWriter.appendFollowUpEvent(
                  drgKey,
                  DecisionRequirementsIntent.CREATED,
                  new DecisionRequirementsRecord()
                      .setDecisionRequirementsKey(drgKey)
                      .setDecisionRequirementsId(drg.getDecisionRequirementsId())
                      .setDecisionRequirementsName(drg.getDecisionRequirementsName())
                      .setDecisionRequirementsVersion(drg.getDecisionRequirementsVersion())
                      .setNamespace(drg.getNamespace())
                      .setResourceName(drg.getResourceName())
                      .setChecksum(drg.getChecksumBuffer())
                      .setResource(resource.getResourceBuffer())
                      .setTenantId(drg.getTenantId()));

              deployment.decisionsMetadata().stream()
                  .filter(
                      decision ->
                          decision
                              .getDecisionRequirementsId()
                              .equals(drg.getDecisionRequirementsId()))
                  .forEach(
                      decision -> {
                        var decisionKey = decision.getDecisionKey();
                        if (decision.isDuplicate()) {
                          decisionKey = keyGenerator.nextKey();
                          decision
                              .setDecisionKey(decisionKey)
                              .setDecisionRequirementsKey(drg.getDecisionRequirementsKey())
                              .setVersion(decision.getVersion() + 1)
                              .setDuplicate(false);
                        }
                        stateWriter.appendFollowUpEvent(
                            decisionKey,
                            DecisionIntent.CREATED,
                            new DecisionRecord()
                                .setDecisionKey(decisionKey)
                                .setDecisionId(decision.getDecisionId())
                                .setDecisionName(decision.getDecisionName())
                                .setVersion(decision.getVersion())
                                .setDecisionRequirementsId(decision.getDecisionRequirementsId())
                                .setDecisionRequirementsKey(decision.getDecisionRequirementsKey())
                                .setTenantId(decision.getTenantId()));
                      });
            });
    return Either.right(null);
  }

  private Either<Failure, ?> checkForDuplicateIds(
      final DeploymentResource resource,
      final ParsedDecisionRequirementsGraph parsedDrg,
      final DeploymentRecord deploymentEvent) {

    return checkDuplicatedDrgIds(resource, parsedDrg, deploymentEvent)
        .flatMap(noDuplicates -> checkDuplicatedDecisionIds(resource, parsedDrg, deploymentEvent));
  }

  private Either<Failure, ?> checkDuplicatedDrgIds(
      final DeploymentResource resource,
      final ParsedDecisionRequirementsGraph parsedDrg,
      final DeploymentRecord deploymentEvent) {

    final var decisionRequirementsId = parsedDrg.getId();

    return deploymentEvent.getDecisionRequirementsMetadata().stream()
        .filter(drg -> drg.getDecisionRequirementsId().equals(decisionRequirementsId))
        .findFirst()
        .map(
            duplicatedDrg -> {
              final var failureMessage =
                  String.format(
                      "Expected the decision requirements ids to be unique within a deployment"
                          + " but found a duplicated id '%s' in the resources '%s' and '%s'.",
                      decisionRequirementsId,
                      duplicatedDrg.getResourceName(),
                      resource.getResourceName());
              return Either.left(new Failure(failureMessage));
            })
        .orElse(NO_DUPLICATES);
  }

  private Either<Failure, ?> checkDuplicatedDecisionIds(
      final DeploymentResource resource,
      final ParsedDecisionRequirementsGraph parsedDrg,
      final DeploymentRecord deploymentEvent) {

    final var decisionIds =
        parsedDrg.getDecisions().stream().map(ParsedDecision::getId).collect(Collectors.toList());

    return deploymentEvent.getDecisionsMetadata().stream()
        .filter(decision -> decisionIds.contains(decision.getDecisionId()))
        .findFirst()
        .map(
            duplicatedDecision -> {
              final var failureMessage =
                  String.format(
                      "Expected the decision ids to be unique within a deployment"
                          + " but found a duplicated id '%s' in the resources '%s' and '%s'.",
                      duplicatedDecision.getDecisionId(),
                      findResourceName(
                          deploymentEvent, duplicatedDecision.getDecisionRequirementsKey()),
                      resource.getResourceName());
              return Either.left(new Failure(failureMessage));
            })
        .orElse(NO_DUPLICATES);
  }

  private String findResourceName(
      final DeploymentRecord deploymentEvent, final long decisionRequirementsKey) {

    return deploymentEvent.getDecisionRequirementsMetadata().stream()
        .filter(drg -> drg.getDecisionRequirementsKey() == decisionRequirementsKey)
        .map(DecisionRequirementsMetadataValue::getResourceName)
        .findFirst()
        .orElse("<?>");
  }

  private void appendMetadataToDeploymentEvent(
      final DeploymentResource resource,
      final ParsedDecisionRequirementsGraph parsedDrg,
      final DeploymentRecord deploymentEvent) {

    final LongSupplier newDecisionRequirementsKey = keyGenerator::nextKey;
    final DirectBuffer checksum = checksumGenerator.apply(resource.getResource());
    final var drgRecord = deploymentEvent.decisionRequirementsMetadata().add();

    drgRecord
        .setDecisionRequirementsId(parsedDrg.getId())
        .setDecisionRequirementsName(parsedDrg.getName())
        .setNamespace(parsedDrg.getNamespace())
        .setResourceName(resource.getResourceName())
        .setChecksum(checksum)
        .setTenantId(deploymentEvent.getTenantId());

    decisionState
        .findLatestDecisionRequirementsByTenantAndId(
            deploymentEvent.getTenantId(), wrapString(parsedDrg.getId()))
        .ifPresentOrElse(
            latestDrg -> {
              final int latestVersion = latestDrg.getDecisionRequirementsVersion();
              final boolean isDuplicate =
                  hasSameResourceNameAs(resource, latestDrg)
                      && hasSameChecksumAs(checksum, latestDrg)
                      && hasSameDecisionRequirementsKeyAs(parsedDrg.getDecisions(), latestDrg);

              if (isDuplicate) {
                drgRecord
                    .setDecisionRequirementsKey(latestDrg.getDecisionRequirementsKey())
                    .setDecisionRequirementsVersion(latestVersion)
                    .setDuplicate(true);
              } else {
                drgRecord
                    .setDecisionRequirementsKey(newDecisionRequirementsKey.getAsLong())
                    .setDecisionRequirementsVersion(latestVersion + 1);
              }
            },
            () ->
                drgRecord
                    .setDecisionRequirementsKey(newDecisionRequirementsKey.getAsLong())
                    .setDecisionRequirementsVersion(INITIAL_VERSION));

    parsedDrg
        .getDecisions()
        .forEach(
            decision -> {
              final LongSupplier newDecisionKey = keyGenerator::nextKey;

              final var decisionRecord = deploymentEvent.decisionsMetadata().add();
              decisionRecord
                  .setDecisionId(decision.getId())
                  .setDecisionName(decision.getName())
                  .setDecisionRequirementsId(parsedDrg.getId())
                  .setDecisionRequirementsKey(drgRecord.getDecisionRequirementsKey())
                  .setTenantId(drgRecord.getTenantId());

              decisionState
                  .findLatestDecisionByIdAndTenant(
                      wrapString(decision.getId()), drgRecord.getTenantId())
                  .ifPresentOrElse(
                      latestDecision -> {
                        final var latestVersion = latestDecision.getVersion();
                        final var isDuplicate =
                            latestDecision.getDecisionRequirementsKey()
                                == drgRecord.getDecisionRequirementsKey();
                        if (isDuplicate) {
                          decisionRecord
                              .setDecisionKey(latestDecision.getDecisionKey())
                              .setVersion(latestVersion)
                              .setDuplicate(true);
                        } else {
                          decisionRecord
                              .setDecisionKey(newDecisionKey.getAsLong())
                              .setVersion(latestVersion + 1);
                        }
                      },
                      () ->
                          decisionRecord
                              .setDecisionKey(newDecisionKey.getAsLong())
                              .setVersion(INITIAL_VERSION));
            });
  }

  private boolean hasSameResourceNameAs(final DeploymentResource resource, final DeployedDrg drg) {
    return drg.getResourceName().equals(resource.getResourceNameBuffer());
  }

  private boolean hasSameChecksumAs(final DirectBuffer checksum, final DeployedDrg drg) {
    return drg.getChecksum().equals(checksum);
  }

  private boolean hasSameDecisionRequirementsKeyAs(
      final Collection<ParsedDecision> decisions, final DeployedDrg drg) {
    return decisions.stream()
        .map(ParsedDecision::getId)
        .map(BufferUtil::wrapString)
        .map(
            decisionId ->
                decisionState
                    .findLatestDecisionByIdAndTenant(decisionId, drg.getTenantId())
                    .map(PersistedDecision::getDecisionRequirementsKey)
                    .orElse(UNKNOWN_DECISION_REQUIREMENTS_KEY))
        .allMatch(drgKey -> drgKey == drg.getDecisionRequirementsKey());
  }
}
