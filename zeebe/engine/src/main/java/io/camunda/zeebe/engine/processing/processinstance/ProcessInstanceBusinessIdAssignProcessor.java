/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.identity.PermissionsBehavior;
import io.camunda.zeebe.engine.processing.identity.authorization.CslAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
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
  private static final String ERROR_NOT_FOUND_FOR_TENANT =
      "Expected to assign a business id to a process instance with key '%d', but no such process instance was found";

  private final TypedResponseWriter responseWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final ElementInstanceState elementInstanceState;
  private final CslAuthorizationCheck cslCheck;
  private final PermissionsBehavior permissionsBehavior;
  private final ProcessInstanceBusinessIdAssignmentBehavior assignmentBehavior;

  public ProcessInstanceBusinessIdAssignProcessor(
      final Writers writers,
      final ProcessingState processingState,
      final CslAuthorizationCheck cslCheck,
      final PermissionsBehavior permissionsBehavior,
      final boolean businessIdUniquenessEnabled) {
    responseWriter = writers.response();
    rejectionWriter = writers.rejection();
    elementInstanceState = processingState.getElementInstanceState();
    this.cslCheck = cslCheck;
    this.permissionsBehavior = permissionsBehavior;
    assignmentBehavior =
        new ProcessInstanceBusinessIdAssignmentBehavior(
            writers.state(), businessIdUniquenessEnabled);
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

    assignmentBehavior
        .validate(processInstance, value.getBusinessId())
        .ifRightOrLeft(
            decision -> {
              assignmentBehavior.enrich(value, processInstanceRecord);
              assignmentBehavior.appendAssignedEvent(processInstanceKey, value);
              responseWriter.writeAcceptedResponseOnCommand(
                  processInstanceKey, ProcessInstanceBusinessIdIntent.ASSIGNED, value, command);
            },
            rejection -> {
              enrichRejectionCommand(command, processInstanceRecord);
              reject(command, rejection.type(), rejection.reason());
            });
  }

  private boolean isAuthorized(
      final TypedRecord<ProcessInstanceBusinessIdRecord> command,
      final ProcessInstanceRecord processInstanceRecord) {
    final var isAuthorized =
        cslCheck
            .checkTenant(
                command,
                processInstanceRecord.getTenantId(),
                processInstanceRecord,
                new Rejection(
                    RejectionType.NOT_FOUND,
                    ERROR_NOT_FOUND_FOR_TENANT.formatted(
                        processInstanceRecord.getProcessInstanceKey())))
            .flatMap(
                recordValue ->
                    permissionsBehavior.isAuthorizedWithResourceIdentifiers(
                        command,
                        AuthorizationResourceType.PROCESS_DEFINITION,
                        PermissionType.UPDATE_PROCESS_INSTANCE,
                        processInstanceRecord.getBpmnProcessId()));
    if (isAuthorized.isRight()) {
      return true;
    }

    final var rejection = isAuthorized.getLeft();
    enrichRejectionCommand(command, processInstanceRecord);
    reject(command, rejection.type(), rejection.reason());
    return false;
  }

  private void reject(
      final TypedRecord<ProcessInstanceBusinessIdRecord> command,
      final RejectionType rejectionType,
      final String reason) {
    rejectionWriter.appendRejection(command, rejectionType, reason);
    responseWriter.writeRejectedResponseOnCommand(command, rejectionType, reason);
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
