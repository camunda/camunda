/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import io.camunda.zeebe.engine.processing.identity.authorization.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.authorization.request.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceBusinessIdRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceBusinessIdIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;

/**
 * Assigns a Business ID once to an already-running root process instance that has none. The
 * assignment is irreversible and only propagates to artifacts created after it (see ADR 0006). It
 * is only available while Business ID uniqueness is disabled.
 */
public class ProcessInstanceBusinessIdAssignProcessor
    implements TypedRecordProcessor<ProcessInstanceBusinessIdRecord> {

  private static final String ERROR_NOT_FOUND =
      "Expected to assign a business id to process instance with key '%d', but no such process instance was found";
  private static final String ERROR_NOT_A_PROCESS_INSTANCE =
      "Expected to assign a business id to process instance with key '%d', but the element with this key is not a process instance";
  private static final String ERROR_CHILD_INSTANCE =
      "Expected to assign a business id to process instance with key '%d', but it is a child process instance; a business id can only be assigned to root process instances";
  private static final String ERROR_NOT_ACTIVE =
      "Expected to assign a business id to process instance with key '%d', but it is not active; a business id can only be assigned to active process instances";
  private static final String ERROR_UNIQUENESS_ENABLED =
      "Expected to assign a business id to process instance with key '%d', but business id assignment is not allowed while business id uniqueness is enabled";
  private static final String ERROR_EMPTY =
      "Expected to assign a business id to process instance with key '%d', but the provided business id is empty";
  private static final String ERROR_INVALID =
      "Expected to assign a business id to process instance with key '%d', but the business id %s";
  private static final String ERROR_ALREADY_ASSIGNED =
      "Expected to assign a business id to process instance with key '%d', but it already has a business id assigned";

  private final StateWriter stateWriter;
  private final TypedResponseWriter responseWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final ElementInstanceState elementInstanceState;
  private final AuthorizationCheckBehavior authCheckBehavior;
  private final boolean businessIdUniquenessEnabled;

  public ProcessInstanceBusinessIdAssignProcessor(
      final Writers writers,
      final ProcessingState processingState,
      final AuthorizationCheckBehavior authCheckBehavior,
      final boolean businessIdUniquenessEnabled) {
    stateWriter = writers.state();
    responseWriter = writers.response();
    rejectionWriter = writers.rejection();
    elementInstanceState = processingState.getElementInstanceState();
    this.authCheckBehavior = authCheckBehavior;
    this.businessIdUniquenessEnabled = businessIdUniquenessEnabled;
  }

  @Override
  public void processRecord(final TypedRecord<ProcessInstanceBusinessIdRecord> command) {
    final ProcessInstanceBusinessIdRecord value = command.getValue();
    final long processInstanceKey = value.getProcessInstanceKey();
    final ElementInstance processInstance = elementInstanceState.getInstance(processInstanceKey);

    if (processInstance == null) {
      reject(command, RejectionType.NOT_FOUND, ERROR_NOT_FOUND.formatted(processInstanceKey));
      return;
    }

    final ProcessInstanceRecord processInstanceRecord = processInstance.getValue();

    if (!isAuthorized(command, processInstanceRecord)) {
      return;
    }

    if (processInstanceRecord.getBpmnElementType() != BpmnElementType.PROCESS) {
      reject(
          command,
          RejectionType.NOT_FOUND,
          ERROR_NOT_A_PROCESS_INSTANCE.formatted(processInstanceKey));
      return;
    }

    if (processInstanceRecord.hasParentProcessInstance()) {
      enrichRejectionCommand(command, processInstanceRecord);
      reject(
          command, RejectionType.INVALID_STATE, ERROR_CHILD_INSTANCE.formatted(processInstanceKey));
      return;
    }

    if (!processInstance.isActive()) {
      enrichRejectionCommand(command, processInstanceRecord);
      reject(command, RejectionType.INVALID_STATE, ERROR_NOT_ACTIVE.formatted(processInstanceKey));
      return;
    }

    if (businessIdUniquenessEnabled) {
      enrichRejectionCommand(command, processInstanceRecord);
      reject(
          command,
          RejectionType.INVALID_STATE,
          ERROR_UNIQUENESS_ENABLED.formatted(processInstanceKey));
      return;
    }

    final String businessId = value.getBusinessId();
    if (businessId.isEmpty()) {
      enrichRejectionCommand(command, processInstanceRecord);
      reject(command, RejectionType.INVALID_ARGUMENT, ERROR_EMPTY.formatted(processInstanceKey));
      return;
    }

    final var validation = BusinessIdValidator.validate(businessId);
    if (validation.isLeft()) {
      enrichRejectionCommand(command, processInstanceRecord);
      reject(
          command,
          RejectionType.INVALID_ARGUMENT,
          ERROR_INVALID.formatted(processInstanceKey, validation.getLeft()));
      return;
    }

    final String existingBusinessId = processInstanceRecord.getBusinessId();
    if (!existingBusinessId.isEmpty()) {
      if (existingBusinessId.equals(businessId)) {
        // Idempotent no-op: the identical value is already assigned. Respond success without
        // writing a second ASSIGNED event (see ADR 0006, D3).
        enrichAssignmentCommand(value, processInstanceRecord);
        responseWriter.writeEventOnCommand(
            processInstanceKey, ProcessInstanceBusinessIdIntent.ASSIGNED, value, command);
        return;
      }
      enrichRejectionCommand(command, processInstanceRecord);
      reject(
          command,
          RejectionType.INVALID_STATE,
          ERROR_ALREADY_ASSIGNED.formatted(processInstanceKey));
      return;
    }

    enrichAssignmentCommand(value, processInstanceRecord);
    stateWriter.appendFollowUpEvent(
        processInstanceKey, ProcessInstanceBusinessIdIntent.ASSIGNED, value);
    responseWriter.writeEventOnCommand(
        processInstanceKey, ProcessInstanceBusinessIdIntent.ASSIGNED, value, command);
  }

  private boolean isAuthorized(
      final TypedRecord<ProcessInstanceBusinessIdRecord> command,
      final ProcessInstanceRecord processInstanceRecord) {
    final var authorizationRequest =
        AuthorizationRequest.builder()
            .command(command)
            .resourceType(AuthorizationResourceType.PROCESS_DEFINITION)
            .permissionType(PermissionType.UPDATE_PROCESS_INSTANCE)
            .tenantId(processInstanceRecord.getTenantId())
            .addResourceId(processInstanceRecord.getBpmnProcessId())
            .build();
    final var isAuthorized = authCheckBehavior.isAuthorizedOrInternalCommand(authorizationRequest);
    if (isAuthorized.isRight()) {
      return true;
    }

    final var rejection = isAuthorized.getLeft();
    final String errorMessage =
        RejectionType.NOT_FOUND.equals(rejection.type())
            ? AuthorizationCheckBehavior.NOT_FOUND_ERROR_MESSAGE.formatted(
                "assign a business id to a process instance",
                processInstanceRecord.getProcessInstanceKey(),
                "such process instance")
            : rejection.reason();
    enrichRejectionCommand(command, processInstanceRecord);
    reject(command, rejection.type(), errorMessage);
    return false;
  }

  private void reject(
      final TypedRecord<ProcessInstanceBusinessIdRecord> command,
      final RejectionType rejectionType,
      final String reason) {
    rejectionWriter.appendRejection(command, rejectionType, reason);
    responseWriter.writeRejectionOnCommand(command, rejectionType, reason);
  }

  /**
   * Enriches the assignment value with the process instance context so the {@code ASSIGNED} event
   * and its exporters carry the full process information.
   */
  private void enrichAssignmentCommand(
      final ProcessInstanceBusinessIdRecord value,
      final ProcessInstanceRecord processInstanceRecord) {
    value
        .setTenantId(processInstanceRecord.getTenantId())
        .setProcessDefinitionKey(processInstanceRecord.getProcessDefinitionKey())
        .setBpmnProcessId(processInstanceRecord.getBpmnProcessId())
        .setRootProcessInstanceKey(processInstanceRecord.getRootProcessInstanceKey());
  }

  /**
   * Enriches the rejected command value with fields from the process instance record so rejection
   * records have the proper context for audit log export.
   */
  private void enrichRejectionCommand(
      final TypedRecord<ProcessInstanceBusinessIdRecord> command,
      final ProcessInstanceRecord processInstanceRecord) {
    command
        .getValue()
        .setTenantId(processInstanceRecord.getTenantId())
        .setProcessDefinitionKey(processInstanceRecord.getProcessDefinitionKey())
        .setBpmnProcessId(processInstanceRecord.getBpmnProcessId())
        .setRootProcessInstanceKey(processInstanceRecord.getRootProcessInstanceKey());
  }
}
