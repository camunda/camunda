/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.transform;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;

import com.google.common.base.Strings;
import io.camunda.zeebe.dmn.DecisionEngine;
import io.camunda.zeebe.dmn.DecisionEngineFactory;
import io.camunda.zeebe.dmn.ParsedDecision;
import io.camunda.zeebe.dmn.ParsedDecisionInput;
import io.camunda.zeebe.dmn.ParsedDecisionOutput;
import io.camunda.zeebe.dmn.ParsedDecisionRequirementsGraph;
import io.camunda.zeebe.dmn.ParsedDecisionRule;
import io.camunda.zeebe.dmn.impl.ParsedDmnScalaDrg;
import io.camunda.zeebe.engine.processing.common.DecisionBehavior;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.ChecksumGenerator;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.state.deployment.DeployedDrg;
import io.camunda.zeebe.engine.state.deployment.PersistedDecision;
import io.camunda.zeebe.engine.state.immutable.DecisionState;
import io.camunda.zeebe.model.bpmn.impl.BpmnModelConstants;
import io.camunda.zeebe.model.bpmn.impl.ZeebeConstants;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRequirementsRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentResource;
import io.camunda.zeebe.protocol.record.intent.DecisionIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionRequirementsIntent;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.io.ByteArrayInputStream;
import java.util.Collection;
import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.stream.Stream;
import org.agrona.DirectBuffer;
import org.camunda.bpm.model.dmn.instance.ExtensionElements;

public final class DmnResourceTransformer implements DeploymentResourceTransformer {

  private static final int INITIAL_VERSION = 1;

  private static final long UNKNOWN_DECISION_REQUIREMENTS_KEY = -1L;

  private static final Either<Failure, Object> NO_VALIDATION_ERROR = Either.right(null);

  private final DecisionEngine decisionEngine = DecisionEngineFactory.createDecisionEngine();

  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final ChecksumGenerator checksumGenerator;
  private final DecisionState decisionState;

  private final ValidationConfig config;

  public DmnResourceTransformer(
      final KeyGenerator keyGenerator,
      final StateWriter stateWriter,
      final ChecksumGenerator checksumGenerator,
      final DecisionState decisionState,
      final ValidationConfig config) {
    this.keyGenerator = keyGenerator;
    this.stateWriter = stateWriter;
    this.checksumGenerator = checksumGenerator;
    this.decisionState = decisionState;
    this.config = config;
  }

  @Override
  public boolean canTransform(final DeploymentResource resource) {
    return resource.getResourceName().endsWith(".dmn");
  }

  @Override
  public Either<Failure, DeploymentResourceContext> createMetadata(
      final DeploymentResource resource, final DeploymentRecord deployment) {

    final var dmnResource = new ByteArrayInputStream(resource.getResource());
    final var parsedDrg = decisionEngine.parse(dmnResource);

    if (parsedDrg.isValid()) {
      return checkDrdIdNameLength(resource, parsedDrg)
          .flatMap(valid -> checkDecisions(resource, parsedDrg))
          .map(
              valid -> {
                appendMetadataToDeploymentEvent(resource, parsedDrg, deployment);
                return DeploymentResourceContext.NONE;
              });

    } else {
      final var failure =
          String.format("'%s': %s", resource.getResourceName(), parsedDrg.getFailureMessage());
      return Either.left(new Failure(failure));
    }
  }

  @Override
  public void writeRecords(final DeploymentResource resource, final DeploymentRecord deployment) {
    final var checksum = checksumGenerator.checksum(resource.getResourceBuffer());
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
                      .setTenantId(drg.getTenantId())
                      .setDeploymentKey(deployment.getDeploymentKey()));

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
                              .setDuplicate(false)
                              .setDeploymentKey(deployment.getDeploymentKey());
                        }
                        stateWriter.appendFollowUpEvent(
                            decisionKey,
                            DecisionIntent.CREATED,
                            new DecisionRecord()
                                .setDecisionKey(decisionKey)
                                .setDecisionId(decision.getDecisionId())
                                .setDecisionName(decision.getDecisionName())
                                .setVersion(decision.getVersion())
                                .setVersionTag(decision.getVersionTag())
                                .setDecisionRequirementsId(decision.getDecisionRequirementsId())
                                .setDecisionRequirementsKey(decision.getDecisionRequirementsKey())
                                .setTenantId(decision.getTenantId())
                                .setDeploymentKey(decision.getDeploymentKey()));
                      });
            });
  }

  private Either<Failure, ?> checkDrdIdNameLength(
      final DeploymentResource resource, final ParsedDecisionRequirementsGraph parsedDrg) {
    final var decisionRequirementsId = parsedDrg.getId();
    final var decisionRequirementsName = parsedDrg.getName();

    return checkFieldLength(
            Stream.of(decisionRequirementsId),
            config.maxIdFieldLength(),
            "The ID of a DRG must not be longer than the configured max-id-length of %s characters, but was '%s' in resource '%s'",
            resource)
        .flatMap(
            valid ->
                checkFieldLength(
                    Stream.of(decisionRequirementsName),
                    config.maxNameFieldLength(),
                    "The name of a DRG must not be longer than the configured max-name-length of %s characters, but was '%s' in DRG '%s'",
                    resource));
  }

  private Either<Failure, ?> checkDecision(
      final DeploymentResource resource, final ParsedDecision decision) {
    return checkFieldLength(
            decision.getId(),
            config.maxIdFieldLength(),
            "The ID of a decision must not be longer than the configured max-id-length of %s characters, but was '%s' in resource '%s'",
            resource)
        .flatMap(
            valid ->
                checkFieldLength(
                    decision.getName(),
                    config.maxNameFieldLength(),
                    "The name of a decision must not be longer than the configured max-name-length of %s characters, but was '%s' in resource '%s'",
                    resource))
        .flatMap(valid -> checkDecisionTable(resource, decision));
  }

  private Either<Failure, ?> checkDecisionTable(
      final DeploymentResource resource, final ParsedDecision decision) {
    if (decision.getDecisionTable() == null) {
      return NO_VALIDATION_ERROR;
    }

    return checkFieldLength(
            decision.getDecisionTable().getInputs().stream().map(ParsedDecisionInput::getId),
            config.maxIdFieldLength(),
            "The ID of a decision input must not be longer than the configured max-id-length of %s characters, but was '%s' in resource '%s'",
            resource)
        .flatMap(
            valid ->
                checkFieldLength(
                    decision.getDecisionTable().getInputs().stream()
                        .map(ParsedDecisionInput::getName),
                    config.maxNameFieldLength(),
                    "The name of a decision input must not be longer than the configured max-name-length of %s characters, but was '%s' in resource '%s'",
                    resource))
        .flatMap(
            valid ->
                checkFieldLength(
                    decision.getDecisionTable().getOutputs().stream()
                        .map(ParsedDecisionOutput::getId),
                    config.maxIdFieldLength(),
                    "The ID of a decision output must not be longer than the configured max-id-length of %s characters, but was '%s' in resource '%s'",
                    resource))
        .flatMap(
            valid ->
                checkFieldLength(
                    decision.getDecisionTable().getOutputs().stream()
                        .map(ParsedDecisionOutput::getName),
                    config.maxNameFieldLength(),
                    "The name of a decision output must not be longer than the configured max-name-length of %s characters, but was '%s' in resource '%s'",
                    resource))
        .flatMap(
            valid ->
                checkFieldLength(
                    decision.getDecisionTable().getRules().stream()
                        .map(ParsedDecisionRule::getId)
                        .filter(id -> !Strings.isNullOrEmpty(id)),
                    config.maxIdFieldLength(),
                    "The ID of a decision rule must not be longer than the configured max-id-length of %s characters, but was '%s' in resource '%s'",
                    resource))
        .flatMap(valid -> checkBlankRuleIds(resource, decision));
  }

  private Either<Failure, ?> checkBlankRuleIds(
      final DeploymentResource resource, final ParsedDecision decision) {
    final var hasBlankRuleIds =
        decision.getDecisionTable().getRules().stream()
            .anyMatch(rule -> Strings.isNullOrEmpty(rule.getId()));
    if (hasBlankRuleIds) {
      final var synthesizedRuleId = DecisionBehavior.synthesizeRuleId(decision.getId(), 9999, 9999);
      final var maxLengthDecisionId =
          config.maxIdFieldLength() - synthesizedRuleId.length() + decision.getId().length();
      return checkFieldLength(
          decision.getId(),
          maxLengthDecisionId,
          "A blank ruleId requires a decisionId having a max-id-length of %s characters, but was '%s' in resource '%s'. Either shorten the decisionId or set a ruleId explicitly",
          resource);
    } else {
      return NO_VALIDATION_ERROR;
    }
  }

  private Either<Failure, ?> checkDecisions(
      final DeploymentResource resource, final ParsedDecisionRequirementsGraph parsedDrg) {
    for (final var decision : parsedDrg.getDecisions()) {
      final var validation = checkDecision(resource, decision);
      if (validation.isLeft()) {
        return validation;
      }
    }

    return NO_VALIDATION_ERROR;
  }

  private static Either<Failure, ?> checkFieldLength(
      final String value,
      final int maxLength,
      final String message,
      final DeploymentResource resource) {
    return checkFieldLength(Stream.of(value), maxLength, message, resource);
  }

  private static Either<Failure, ?> checkFieldLength(
      final Stream<String> values,
      final int maxLength,
      final String message,
      final DeploymentResource resource) {
    return values
        .filter(id -> id != null && id.length() > maxLength)
        .findFirst()
        .map(
            id ->
                Either.left(
                    new Failure(String.format(message, maxLength, id, resource.getResourceName()))))
        .orElse(NO_VALIDATION_ERROR);
  }

  private void appendMetadataToDeploymentEvent(
      final DeploymentResource resource,
      final ParsedDecisionRequirementsGraph parsedDrg,
      final DeploymentRecord deploymentEvent) {

    final LongSupplier newDecisionRequirementsKey = keyGenerator::nextKey;
    final DirectBuffer checksum = checksumGenerator.checksum(resource.getResourceBuffer());
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
              getOptionalVersionTag(parsedDrg, decision.getId())
                  .ifPresent(decisionRecord::setVersionTag);

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
                              .setDeploymentKey(latestDecision.getDeploymentKey())
                              .setDuplicate(true);
                        } else {
                          decisionRecord
                              .setDecisionKey(newDecisionKey.getAsLong())
                              .setVersion(latestVersion + 1)
                              .setDeploymentKey(deploymentEvent.getDeploymentKey());
                        }
                      },
                      () ->
                          decisionRecord
                              .setDecisionKey(newDecisionKey.getAsLong())
                              .setVersion(INITIAL_VERSION)
                              .setDeploymentKey(deploymentEvent.getDeploymentKey()));
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

  private Optional<String> getOptionalVersionTag(
      final ParsedDecisionRequirementsGraph parsedDrg, final String decisionId) {
    if (parsedDrg instanceof final ParsedDmnScalaDrg dmn) {
      final var decisionElement = dmn.getParsedDmn().model().getModelElementById(decisionId);
      return Optional.ofNullable(
              decisionElement.getUniqueChildElementByType(ExtensionElements.class))
          .map(
              extensionElements ->
                  extensionElements.getUniqueChildElementByNameNs(
                      BpmnModelConstants.ZEEBE_NS, ZeebeConstants.ELEMENT_VERSION_TAG))
          .map(versionTag -> versionTag.getAttributeValue(ZeebeConstants.ATTRIBUTE_VALUE));
    }
    return Optional.empty();
  }
}
