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

public class MappingCreateProcessor implements DistributedTypedRecordProcessor<MappingRecord> {

  private static final String MAPPING_SAME_CLAIM_ALREADY_EXISTS_ERROR_MESSAGE =
      "Expected to create mapping with claimName '%s' and claimValue '%s', but a mapping with this claim already exists.";
  private static final String MAPPING_SAME_ID_ALREADY_EXISTS_ERROR_MESSAGE =
      "Expected to create mapping with id '%s', but a mapping with this id already exists.";

  private final MappingState mappingState;
  private final AuthorizationCheckBehavior authCheckBehavior;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final CommandDistributionBehavior commandDistributionBehavior;

  public MappingCreateProcessor(
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
    final var persistedMappingWithSameClaim =
        mappingState.get(record.getClaimName(), record.getClaimValue());
    if (persistedMappingWithSameClaim.isPresent()) {
      final var errorMessage =
          MAPPING_SAME_CLAIM_ALREADY_EXISTS_ERROR_MESSAGE.formatted(
              record.getClaimName(), record.getClaimValue());
      rejectionWriter.appendRejection(command, RejectionType.ALREADY_EXISTS, errorMessage);
      responseWriter.writeRejectionOnCommand(command, RejectionType.ALREADY_EXISTS, errorMessage);
      return;
    }

    final var persistedMappingWithSameId = mappingState.get(record.getMappingId());
    if (persistedMappingWithSameId.isPresent()) {
      final var errorMessage =
          MAPPING_SAME_ID_ALREADY_EXISTS_ERROR_MESSAGE.formatted(record.getMappingId());
      rejectionWriter.appendRejection(command, RejectionType.ALREADY_EXISTS, errorMessage);
      responseWriter.writeRejectionOnCommand(command, RejectionType.ALREADY_EXISTS, errorMessage);
      return;
    }

    final long key = keyGenerator.nextKey();
    record.setMappingKey(key);

    stateWriter.appendFollowUpEvent(key, MappingIntent.CREATED, record);
    responseWriter.writeEventOnCommand(key, MappingIntent.CREATED, record, command);

    commandDistributionBehavior
        .withKey(key)
        .inQueue(DistributionQueue.IDENTITY.getQueueId())
        .distribute(command);
  }

  @Override
  public void processDistributedCommand(final TypedRecord<MappingRecord> command) {
    final var record = command.getValue();
    mappingState
        .get(record.getMappingId())
        .ifPresentOrElse(
            existingMapping -> {
              final var errorMessage =
                  MAPPING_SAME_ID_ALREADY_EXISTS_ERROR_MESSAGE.formatted(existingMapping.getId());
              rejectionWriter.appendRejection(command, RejectionType.ALREADY_EXISTS, errorMessage);
            },
            () -> stateWriter.appendFollowUpEvent(command.getKey(), MappingIntent.CREATED, record));

    commandDistributionBehavior.acknowledgeCommand(command);
  }
}
