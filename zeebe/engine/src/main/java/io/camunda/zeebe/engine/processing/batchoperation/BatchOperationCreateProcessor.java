/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation;

import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.streamprocessor.DistributedTypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationCreationRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.Either;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BatchOperationCreateProcessor
    implements DistributedTypedRecordProcessor<BatchOperationCreationRecord> {

  private static final Logger LOGGER = LoggerFactory.getLogger(BatchOperationCreateProcessor.class);

  private static final String EMPTY_JSON_OBJECT = "{}";
  private static final String MESSAGE_GIVEN_FILTER_IS_EMPTY = "Given filter is empty";

  private final KeyGenerator keyGenerator;
  private final CommandDistributionBehavior commandDistributionBehavior;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final AuthorizationCheckBehavior authCheckBehavior;

  public BatchOperationCreateProcessor(
      final Writers writers,
      final KeyGenerator keyGenerator,
      final CommandDistributionBehavior commandDistributionBehavior,
      final AuthorizationCheckBehavior authCheckBehavior) {
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    this.keyGenerator = keyGenerator;
    this.commandDistributionBehavior = commandDistributionBehavior;
    this.authCheckBehavior = authCheckBehavior;
  }

  @Override
  public void processNewCommand(final TypedRecord<BatchOperationCreationRecord> command) {
    if (isEmptyOrNullFilter(command)) {
      rejectionWriter.appendRejection(
          command, RejectionType.INVALID_ARGUMENT, MESSAGE_GIVEN_FILTER_IS_EMPTY);
      responseWriter.writeRejectionOnCommand(
          command, RejectionType.INVALID_ARGUMENT, MESSAGE_GIVEN_FILTER_IS_EMPTY);
      return;
    }

    final var authorizationResult = isAuthorized(command);
    if (authorizationResult.isLeft()) {
      final Rejection rejection = authorizationResult.getLeft();
      rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
      responseWriter.writeRejectionOnCommand(command, rejection.type(), rejection.reason());
      return;
    }

    final long key = keyGenerator.nextKey();
    final var recordValue = command.getValue();
    LOGGER.debug("Processing new command with key '{}': {}", key, recordValue);

    final var recordWithKey = new BatchOperationCreationRecord();
    recordWithKey.wrap(recordValue);
    recordWithKey.setBatchOperationKey(key);

    stateWriter.appendFollowUpEvent(key, BatchOperationIntent.CREATED, recordWithKey);
    responseWriter.writeEventOnCommand(key, BatchOperationIntent.CREATED, recordWithKey, command);
    commandDistributionBehavior
        .withKey(key)
        .inQueue(DistributionQueue.BATCH_OPERATION)
        .distribute(command.getValueType(), command.getIntent(), recordWithKey);
  }

  @Override
  public void processDistributedCommand(final TypedRecord<BatchOperationCreationRecord> command) {
    final var recordValue = command.getValue();

    LOGGER.debug("Processing distributed command with key '{}': {}", command.getKey(), recordValue);
    stateWriter.appendFollowUpEvent(
        command.getKey(), BatchOperationIntent.CREATED, command.getValue());
    commandDistributionBehavior.acknowledgeCommand(command);
  }

  private Either<Rejection, Void> isAuthorized(
      final TypedRecord<BatchOperationCreationRecord> command) {

    // first check for general CREATE_BATCH_OPERATION permission
    final var isAuthorized =
        authCheckBehavior.isAuthorized(
            new AuthorizationRequest(
                command, AuthorizationResourceType.BATCH_OPERATION, PermissionType.CREATE));
    if (isAuthorized.isLeft()) {
      // if that's not present, check for the BO type dependent permission
      final var permission =
          switch (command.getValue().getBatchOperationType()) {
            case CANCEL_PROCESS_INSTANCE ->
                PermissionType.CREATE_BATCH_OPERATION_CANCEL_PROCESS_INSTANCE;
            case MIGRATE_PROCESS_INSTANCE ->
                PermissionType.CREATE_BATCH_OPERATION_MIGRATE_PROCESS_INSTANCE;
            case MODIFY_PROCESS_INSTANCE ->
                PermissionType.CREATE_BATCH_OPERATION_MODIFY_PROCESS_INSTANCE;
            case RESOLVE_INCIDENT -> PermissionType.CREATE_BATCH_OPERATION_RESOLVE_INCIDENT;
          };
      return authCheckBehavior.isAuthorized(
          new AuthorizationRequest(command, AuthorizationResourceType.BATCH_OPERATION, permission));
    }

    return isAuthorized;
  }

  private static boolean isEmptyOrNullFilter(
      final TypedRecord<BatchOperationCreationRecord> command) {
    return command.getValue().getEntityFilter() == null
        || command.getValue().getEntityFilter().equalsIgnoreCase(EMPTY_JSON_OBJECT);
  }
}
