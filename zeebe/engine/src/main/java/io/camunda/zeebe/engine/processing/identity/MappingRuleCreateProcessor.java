/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity;

import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.streamprocessor.DistributedTypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.engine.state.immutable.MappingRuleState;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRuleRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.MappingRuleIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

public class MappingRuleCreateProcessor
    implements DistributedTypedRecordProcessor<MappingRuleRecord> {

  private static final String MAPPING_RULE_NULL_VALUE_ERROR_MESSAGE =
      "Expected to create mapping rule with claimName '%s' and claimValue '%s' and name '%s' and mappingRuleId '%s', but at least one of them is null.";
  private static final String MAPPING_RULE_SAME_CLAIM_ALREADY_EXISTS_ERROR_MESSAGE =
      "Expected to create mapping rule with claimName '%s' and claimValue '%s', but a mapping rule with this claim already exists.";
  private static final String MAPPING_RULE_SAME_ID_ALREADY_EXISTS_ERROR_MESSAGE =
      "Expected to create mapping rule with id '%s', but a mapping rule with this id already exists.";

  private final MappingRuleState mappingRuleState;
  private final AuthorizationCheckBehavior authCheckBehavior;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final CommandDistributionBehavior commandDistributionBehavior;

  public MappingRuleCreateProcessor(
      final MappingRuleState mappingRuleState,
      final AuthorizationCheckBehavior authCheckBehavior,
      final KeyGenerator keyGenerator,
      final Writers writers,
      final CommandDistributionBehavior commandDistributionBehavior) {
    this.mappingRuleState = mappingRuleState;
    this.authCheckBehavior = authCheckBehavior;
    this.keyGenerator = keyGenerator;
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    this.commandDistributionBehavior = commandDistributionBehavior;
  }

  @Override
  public void processNewCommand(final TypedRecord<MappingRuleRecord> command) {
    final var authorizationRequest =
        new AuthorizationRequest(
            command, AuthorizationResourceType.MAPPING_RULE, PermissionType.CREATE);
    final var isAuthorized = authCheckBehavior.isAuthorized(authorizationRequest);
    if (isAuthorized.isLeft()) {
      final var rejection = isAuthorized.getLeft();
      rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
      responseWriter.writeRejectionOnCommand(command, rejection.type(), rejection.reason());
      return;
    }

    final var record = command.getValue();
    if (record.getMappingRuleId() == null
        || record.getMappingRuleId().isBlank()
        || record.getName() == null
        || record.getName().isBlank()
        || record.getClaimName() == null
        || record.getClaimName().isBlank()
        || record.getClaimValue() == null
        || record.getClaimValue().isBlank()) {
      final var errorMessage =
          MAPPING_RULE_NULL_VALUE_ERROR_MESSAGE.formatted(
              record.getClaimName(),
              record.getClaimValue(),
              record.getName(),
              record.getMappingRuleId());
      rejectionWriter.appendRejection(command, RejectionType.NULL_VAL, errorMessage);
      responseWriter.writeRejectionOnCommand(command, RejectionType.NULL_VAL, errorMessage);
      return;
    }

    final var persistedMappingRuleWithSameClaim =
        mappingRuleState.get(record.getClaimName(), record.getClaimValue());
    if (persistedMappingRuleWithSameClaim.isPresent()) {
      final var errorMessage =
          MAPPING_RULE_SAME_CLAIM_ALREADY_EXISTS_ERROR_MESSAGE.formatted(
              record.getClaimName(), record.getClaimValue());
      rejectionWriter.appendRejection(command, RejectionType.ALREADY_EXISTS, errorMessage);
      responseWriter.writeRejectionOnCommand(command, RejectionType.ALREADY_EXISTS, errorMessage);
      return;
    }

    final var persistedMappingRuleWithSameId = mappingRuleState.get(record.getMappingRuleId());
    if (persistedMappingRuleWithSameId.isPresent()) {
      final var errorMessage =
          MAPPING_RULE_SAME_ID_ALREADY_EXISTS_ERROR_MESSAGE.formatted(record.getMappingRuleId());
      rejectionWriter.appendRejection(command, RejectionType.ALREADY_EXISTS, errorMessage);
      responseWriter.writeRejectionOnCommand(command, RejectionType.ALREADY_EXISTS, errorMessage);
      return;
    }

    final long key = keyGenerator.nextKey();
    record.setMappingRuleKey(key);

    stateWriter.appendFollowUpEvent(key, MappingRuleIntent.CREATED, record);
    responseWriter.writeEventOnCommand(key, MappingRuleIntent.CREATED, record, command);

    commandDistributionBehavior
        .withKey(key)
        .inQueue(DistributionQueue.IDENTITY.getQueueId())
        .distribute(command);
  }

  @Override
  public void processDistributedCommand(final TypedRecord<MappingRuleRecord> command) {
    final var record = command.getValue();
    mappingRuleState
        .get(record.getMappingRuleId())
        .ifPresentOrElse(
            existingMappingRule -> {
              final var errorMessage =
                  MAPPING_RULE_SAME_ID_ALREADY_EXISTS_ERROR_MESSAGE.formatted(
                      existingMappingRule.getMappingRuleId());
              rejectionWriter.appendRejection(command, RejectionType.ALREADY_EXISTS, errorMessage);
            },
            () ->
                stateWriter.appendFollowUpEvent(
                    command.getKey(), MappingRuleIntent.CREATED, record));

    commandDistributionBehavior.acknowledgeCommand(command);
  }
}
