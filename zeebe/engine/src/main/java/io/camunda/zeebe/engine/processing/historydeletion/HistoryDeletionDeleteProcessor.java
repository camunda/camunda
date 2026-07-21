/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.historydeletion;

import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.identity.PermissionsBehavior;
import io.camunda.zeebe.engine.processing.identity.authorization.CslAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.history.HistoryDeletionRecord;
import io.camunda.zeebe.protocol.record.RecordMetadataDecoder;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.HistoryDeletionIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;

public class HistoryDeletionDeleteProcessor implements TypedRecordProcessor<HistoryDeletionRecord> {

  private static final String ERROR_MESSAGE_PROCESS_INSTANCE_EXISTS =
      "Expected to delete history for process instance with key '%d', but it is still active.";
  private static final String NOT_FOUND_FOR_TENANT_MESSAGE =
      "Expected to perform operation '%s' on resource '%s', but no resource was found for tenant '%s'";

  private final StateWriter stateWriter;
  private final TypedResponseWriter responseWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final ElementInstanceState elementInstanceState;
  private final CslAuthorizationCheck cslCheck;
  private final PermissionsBehavior permissionsBehavior;

  public HistoryDeletionDeleteProcessor(
      final ProcessingState processingState,
      final Writers writers,
      final CslAuthorizationCheck cslCheck,
      final PermissionsBehavior permissionsBehavior) {
    elementInstanceState = processingState.getElementInstanceState();
    stateWriter = writers.state();
    responseWriter = writers.response();
    rejectionWriter = writers.rejection();
    this.cslCheck = cslCheck;
    this.permissionsBehavior = permissionsBehavior;
  }

  @Override
  public void processRecord(final TypedRecord<HistoryDeletionRecord> command) {
    switch (command.getValue().getResourceType()) {
      case PROCESS_INSTANCE -> deleteProcessInstance(command);
      case PROCESS_DEFINITION -> deleteProcessDefinition(command);
      case DECISION_INSTANCE -> deleteDecisionInstance(command);
      case DECISION_REQUIREMENTS -> deleteDecisionRequirements(command);
      default ->
          throw new UnsupportedOperationException(
              "Unsupported resource type: " + command.getValue().getResourceType());
    }
  }

  private void deleteProcessInstance(final TypedRecord<HistoryDeletionRecord> command) {
    final var recordValue = command.getValue();

    validateAuthorization(
            command,
            AuthorizationResourceType.PROCESS_DEFINITION,
            PermissionType.DELETE_PROCESS_INSTANCE,
            recordValue.getProcessId())
        .flatMap(this::validateProcessInstanceDoesNotExist)
        .ifRightOrLeft(
            validRecord -> writeHistoryDeletionEvent(recordValue, command),
            rejection -> {
              rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
              responseWriter.writeRejectedResponseOnCommand(
                  command, rejection.type(), rejection.reason());
            });
  }

  private void deleteProcessDefinition(final TypedRecord<HistoryDeletionRecord> command) {
    final var recordValue = command.getValue();
    writeHistoryDeletionEvent(recordValue, command);
  }

  private void deleteDecisionInstance(final TypedRecord<HistoryDeletionRecord> command) {
    final var recordValue = command.getValue();

    validateAuthorization(
            command,
            AuthorizationResourceType.DECISION_DEFINITION,
            PermissionType.DELETE_DECISION_INSTANCE,
            recordValue.getDecisionDefinitionId())
        .ifRightOrLeft(
            validRecord -> writeHistoryDeletionEvent(recordValue, command),
            rejection -> {
              rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
              responseWriter.writeRejectedResponseOnCommand(
                  command, rejection.type(), rejection.reason());
            });
  }

  private Either<Rejection, HistoryDeletionRecord> validateAuthorization(
      final TypedRecord<HistoryDeletionRecord> command,
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType,
      final String resourceId) {
    // If the command is part of a batch operation, the authorizations will be checked upon batch
    // operation creation. We cannot check the authorizations here.
    if (command.getBatchOperationReference()
        != RecordMetadataDecoder.batchOperationReferenceNullValue()) {
      return Either.right(command.getValue());
    }

    final var tenantId = command.getValue().getTenantId();
    return cslCheck
        .checkTenant(
            command,
            tenantId,
            command.getValue(),
            new Rejection(
                RejectionType.NOT_FOUND,
                NOT_FOUND_FOR_TENANT_MESSAGE.formatted(permissionType, resourceType, tenantId)))
        .flatMap(
            value ->
                permissionsBehavior.isAuthorizedWithResourceIdentifiers(
                    command, resourceType, permissionType, resourceId));
  }

  private void deleteDecisionRequirements(final TypedRecord<HistoryDeletionRecord> command) {
    final var recordValue = command.getValue();
    writeHistoryDeletionEvent(recordValue, command);
  }

  private Either<Rejection, HistoryDeletionRecord> validateProcessInstanceDoesNotExist(
      final HistoryDeletionRecord record) {
    final var processInstanceKey = record.getResourceKey();
    if (elementInstanceState.getInstance(processInstanceKey) != null) {
      final var rejection =
          new Rejection(
              RejectionType.INVALID_STATE,
              String.format(ERROR_MESSAGE_PROCESS_INSTANCE_EXISTS, processInstanceKey));
      return Either.left(rejection);
    }
    return Either.right(record);
  }

  private void writeHistoryDeletionEvent(
      final HistoryDeletionRecord recordValue, final TypedRecord<HistoryDeletionRecord> command) {
    stateWriter.appendFollowUpEvent(
        recordValue.getResourceKey(), HistoryDeletionIntent.DELETED, recordValue);
    responseWriter.writeAcceptedResponseOnCommand(
        recordValue.getResourceKey(), HistoryDeletionIntent.DELETED, recordValue, command);
  }
}
