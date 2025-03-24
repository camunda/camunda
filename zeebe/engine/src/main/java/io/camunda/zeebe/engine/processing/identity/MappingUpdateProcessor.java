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
import io.camunda.zeebe.engine.state.immutable.MappingState;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.MappingIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

public class MappingUpdateProcessor implements DistributedTypedRecordProcessor<MappingRecord> {

  private static final String MAPPING_SAME_CLAIM_ALREADY_EXISTS_ERROR_MESSAGE =
      "Expected to create mapping with claimName '%s' and claimValue '%s', but a mapping with this claim already exists.";
  private static final String MAPPING_ID_DOES_NOT_EXIST_ERROR_MESSAGE =
      "Expected to update mapping with id '%s', but a mapping with this id does not exist.";

  private final MappingState mappingState;
  private final AuthorizationCheckBehavior authCheckBehavior;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final CommandDistributionBehavior commandDistributionBehavior;

  public MappingUpdateProcessor(
      final MappingState mappingState,
      final AuthorizationCheckBehavior authCheckBehavior,
      final KeyGenerator keyGenerator,
      final Writers writers,
      final CommandDistributionBehavior commandDistributionBehavior) {
    this.mappingState = mappingState;
    this.authCheckBehavior = authCheckBehavior;
    this.keyGenerator = keyGenerator;
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    this.commandDistributionBehavior = commandDistributionBehavior;
  }

  @Override
  public void processNewCommand(final TypedRecord<MappingRecord> command) {

    final var record = command.getValue();
    final var mappingId = record.getMappingId();
    final var persistedRecord = mappingState.get(mappingId);
    if (persistedRecord.isEmpty()) {
      final var errorMessage = MAPPING_ID_DOES_NOT_EXIST_ERROR_MESSAGE.formatted(mappingId);
      rejectionWriter.appendRejection(command, RejectionType.NOT_FOUND, errorMessage);
      responseWriter.writeRejectionOnCommand(command, RejectionType.NOT_FOUND, errorMessage);
      return;
    }

    final var authorizationRequest =
        new AuthorizationRequest(
                command, AuthorizationResourceType.MAPPING_RULE, PermissionType.UPDATE)
            .addResourceId(mappingId);
    final var isAuthorized = authCheckBehavior.isAuthorized(authorizationRequest);
    if (isAuthorized.isLeft()) {
      final var rejection = isAuthorized.getLeft();
      rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
      responseWriter.writeRejectionOnCommand(command, rejection.type(), rejection.reason());
      return;
    }

    final var persistedMappingWithSameClaim =
        mappingState.get(record.getClaimName(), record.getClaimValue());
    if (persistedMappingWithSameClaim.isPresent()
        && !persistedMappingWithSameClaim.get().getId().equals(mappingId)) {
      final var errorMessage =
          MAPPING_SAME_CLAIM_ALREADY_EXISTS_ERROR_MESSAGE.formatted(
              record.getClaimName(), record.getClaimValue());
      rejectionWriter.appendRejection(command, RejectionType.ALREADY_EXISTS, errorMessage);
      responseWriter.writeRejectionOnCommand(command, RejectionType.ALREADY_EXISTS, errorMessage);
      return;
    }

    stateWriter.appendFollowUpEvent(record.getMappingKey(), MappingIntent.UPDATED, record);
    responseWriter.writeEventOnCommand(
        record.getMappingKey(), MappingIntent.UPDATED, record, command);

    commandDistributionBehavior
        .withKey(keyGenerator.nextKey())
        .inQueue(DistributionQueue.IDENTITY.getQueueId())
        .distribute(command);
  }

  @Override
  public void processDistributedCommand(final TypedRecord<MappingRecord> command) {
    stateWriter.appendFollowUpEvent(command.getKey(), MappingIntent.UPDATED, command.getValue());
    commandDistributionBehavior.acknowledgeCommand(command);
  }
}
