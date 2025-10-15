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

public class MappingRuleUpdateProcessor
    implements DistributedTypedRecordProcessor<MappingRuleRecord> {
  private static final String MAPPING_RULE_NULL_VALUE_ERROR_MESSAGE =
      "Expected to update mapping rule with claimName '%s' and claimValue '%s' and name '%s' and mappingRuleId '%s', but at least one of them is null.";
  private static final String MAPPING_RULE_SAME_CLAIM_ALREADY_EXISTS_ERROR_MESSAGE =
      "Expected to update mapping rule with claimName '%s' and claimValue '%s', but a mapping rule with this claim already exists.";
  private static final String MAPPING_RULE_ID_DOES_NOT_EXIST_ERROR_MESSAGE =
      "Expected to update mapping rule with id '%s', but a mapping rule with this id does not exist.";

  private final MappingRuleState mappingRuleState;
  private final AuthorizationCheckBehavior authCheckBehavior;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final CommandDistributionBehavior commandDistributionBehavior;

  public MappingRuleUpdateProcessor(
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

    final var record = command.getValue();
    final var mappingRuleId = record.getMappingRuleId();
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

    final var persistedRecord = mappingRuleState.get(mappingRuleId);
    if (persistedRecord.isEmpty()) {
      final var errorMessage =
          MAPPING_RULE_ID_DOES_NOT_EXIST_ERROR_MESSAGE.formatted(mappingRuleId);
      rejectionWriter.appendRejection(command, RejectionType.NOT_FOUND, errorMessage);
      responseWriter.writeRejectionOnCommand(command, RejectionType.NOT_FOUND, errorMessage);
      return;
    }

    final var authorizationRequest =
        new AuthorizationRequest(
                command, AuthorizationResourceType.MAPPING_RULE, PermissionType.UPDATE)
            .addResourceId(mappingRuleId);
    final var isAuthorized = authCheckBehavior.isAuthorizedOrInternalCommand(authorizationRequest);
    if (isAuthorized.isLeft()) {
      final var rejection = isAuthorized.getLeft();
      rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
      responseWriter.writeRejectionOnCommand(command, rejection.type(), rejection.reason());
      return;
    }

    final var persistedMappingRuleWithSameClaim =
        mappingRuleState.get(record.getClaimName(), record.getClaimValue());
    if (persistedMappingRuleWithSameClaim.isPresent()
        && !persistedMappingRuleWithSameClaim.get().getMappingRuleId().equals(mappingRuleId)) {
      final var errorMessage =
          MAPPING_RULE_SAME_CLAIM_ALREADY_EXISTS_ERROR_MESSAGE.formatted(
              record.getClaimName(), record.getClaimValue());
      rejectionWriter.appendRejection(command, RejectionType.ALREADY_EXISTS, errorMessage);
      responseWriter.writeRejectionOnCommand(command, RejectionType.ALREADY_EXISTS, errorMessage);
      return;
    }

    stateWriter.appendFollowUpEvent(record.getMappingRuleKey(), MappingRuleIntent.UPDATED, record);
    responseWriter.writeEventOnCommand(
        record.getMappingRuleKey(), MappingRuleIntent.UPDATED, record, command);

    commandDistributionBehavior
        .withKey(keyGenerator.nextKey())
        .inQueue(DistributionQueue.IDENTITY.getQueueId())
        .distribute(command);
  }

  @Override
  public void processDistributedCommand(final TypedRecord<MappingRuleRecord> command) {
    stateWriter.appendFollowUpEvent(
        command.getKey(), MappingRuleIntent.UPDATED, command.getValue());
    commandDistributionBehavior.acknowledgeCommand(command);
  }
}
