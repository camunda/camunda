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
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.AsyncRequestState;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;

public final class ProcessInstanceSuspendProcessor
    implements TypedRecordProcessor<ProcessInstanceRecord> {

  private static final String MESSAGE_PREFIX =
      "Expected to suspend a process instance with key '%d', but ";

  private static final String PROCESS_NOT_FOUND_MESSAGE =
      MESSAGE_PREFIX + "no such process was found";
  private static final String PROCESS_CANCEL_IN_PROGRESS_MESSAGE =
      MESSAGE_PREFIX + "a cancel request is already in progress";

  private final ElementInstanceState elementInstanceState;
  private final TypedResponseWriter responseWriter;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final CslAuthorizationCheck cslCheck;
  private final PermissionsBehavior permissionsBehavior;
  private final AsyncRequestState asyncRequestState;

  public ProcessInstanceSuspendProcessor(
      final ProcessingState processingState,
      final Writers writers,
      final CslAuthorizationCheck cslCheck,
      final PermissionsBehavior permissionsBehavior) {
    elementInstanceState = processingState.getElementInstanceState();
    responseWriter = writers.response();
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    this.cslCheck = cslCheck;
    this.permissionsBehavior = permissionsBehavior;
    asyncRequestState = processingState.getAsyncRequestState();
  }

  @Override
  public void processRecord(final TypedRecord<ProcessInstanceRecord> command) {
    final var elementInstance = elementInstanceState.getInstance(command.getKey());

    if (!validateCommand(command, elementInstance)) {
      return;
    }

    final ProcessInstanceRecord value = elementInstance.getValue();
    stateWriter.appendFollowUpEvent(command.getKey(), ProcessInstanceIntent.SUSPENDED, value);
    responseWriter.writeAcceptedResponseOnCommand(
        command.getKey(), ProcessInstanceIntent.SUSPENDED, value, command);
  }

  private boolean validateCommand(
      final TypedRecord<ProcessInstanceRecord> command, final ElementInstance elementInstance) {

    if (elementInstance == null || elementInstance.isTerminating()) {
      final var reason = String.format(PROCESS_NOT_FOUND_MESSAGE, command.getKey());
      rejectionWriter.appendRejection(command, RejectionType.NOT_FOUND, reason);
      responseWriter.writeRejectedResponseOnCommand(command, RejectionType.NOT_FOUND, reason);
      return false;
    }

    final var isAuthorized =
        cslCheck
            .checkTenant(
                command,
                elementInstance.getValue().getTenantId(),
                elementInstance.getValue(),
                new Rejection(
                    RejectionType.NOT_FOUND,
                    PROCESS_NOT_FOUND_MESSAGE.formatted(
                        elementInstance.getValue().getProcessInstanceKey())))
            .flatMap(
                recordValue ->
                    permissionsBehavior.isAuthorizedWithResourceIdentifiers(
                        command,
                        AuthorizationResourceType.PROCESS_DEFINITION,
                        PermissionType.SUSPEND_PROCESS_INSTANCE,
                        elementInstance.getValue().getBpmnProcessId()));
    if (isAuthorized.isLeft()) {
      final var rejection = isAuthorized.getLeft();
      enrichRejectionCommand(command, elementInstance.getValue());
      rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
      responseWriter.writeRejectedResponseOnCommand(command, rejection.type(), rejection.reason());
      return false;
    }

    final var existingCancelRequest =
        asyncRequestState.findRequest(
            command.getKey(), ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.CANCEL);
    if (existingCancelRequest.isPresent()) {
      final var reason = String.format(PROCESS_CANCEL_IN_PROGRESS_MESSAGE, command.getKey());
      enrichRejectionCommand(command, elementInstance.getValue());
      rejectionWriter.appendRejection(command, RejectionType.INVALID_STATE, reason);
      responseWriter.writeRejectedResponseOnCommand(command, RejectionType.INVALID_STATE, reason);
      return false;
    }

    // TODO(#57517): once SuspensionState exists, reject with INVALID_STATE if the process
    // instance is already suspended.

    return true;
  }

  /**
   * Enriches the command value with fields from the element instance to ensure rejection records
   * have the proper context for audit logs export.
   */
  private void enrichRejectionCommand(
      final TypedRecord<ProcessInstanceRecord> command,
      final ProcessInstanceRecord processInstanceRecord) {
    command.getValue().setTenantId(processInstanceRecord.getTenantId());
    command.getValue().setRootProcessInstanceKey(processInstanceRecord.getRootProcessInstanceKey());
  }
}
