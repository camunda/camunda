/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.UNAUTHORIZED_ERROR_MESSAGE;

import io.camunda.zeebe.auth.impl.TenantAuthorizationCheckerImpl;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;

public final class ProcessInstanceCancelProcessor
    implements TypedRecordProcessor<ProcessInstanceRecord> {

  private static final String MESSAGE_PREFIX =
      "Expected to cancel a process instance with key '%d', but ";

  private static final String PROCESS_NOT_FOUND_MESSAGE =
      MESSAGE_PREFIX + "no such process was found";

  private static final String PROCESS_NOT_ROOT_MESSAGE =
      MESSAGE_PREFIX
          + "it is created by a parent process instance. Cancel the root process instance '%d' instead.";

  private final ElementInstanceState elementInstanceState;
  private final TypedResponseWriter responseWriter;
  private final TypedCommandWriter commandWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final AuthorizationCheckBehavior authCheckBehavior;

  public ProcessInstanceCancelProcessor(
      final ProcessingState processingState,
      final Writers writers,
      final AuthorizationCheckBehavior authCheckBehavior) {
    elementInstanceState = processingState.getElementInstanceState();
    responseWriter = writers.response();
    commandWriter = writers.command();
    rejectionWriter = writers.rejection();
    this.authCheckBehavior = authCheckBehavior;
  }

  @Override
  public void processRecord(final TypedRecord<ProcessInstanceRecord> command) {
    final var elementInstance = elementInstanceState.getInstance(command.getKey());

    if (!validateCommand(command, elementInstance)) {
      return;
    }

    final ProcessInstanceRecord value = elementInstance.getValue();

    commandWriter.appendFollowUpCommand(
        command.getKey(), ProcessInstanceIntent.TERMINATE_ELEMENT, value);
    responseWriter.writeEventOnCommand(
        command.getKey(), ProcessInstanceIntent.ELEMENT_TERMINATING, value, command);
  }

  private boolean validateCommand(
      final TypedRecord<ProcessInstanceRecord> command, final ElementInstance elementInstance) {

    if (elementInstance == null
        || !elementInstance.canTerminate()
        || elementInstance.getParentKey() > 0) {
      rejectionWriter.appendRejection(
          command,
          RejectionType.NOT_FOUND,
          String.format(PROCESS_NOT_FOUND_MESSAGE, command.getKey()));
      responseWriter.writeRejectionOnCommand(
          command,
          RejectionType.NOT_FOUND,
          String.format(PROCESS_NOT_FOUND_MESSAGE, command.getKey()));
      return false;
    }

    final var request =
        new AuthorizationRequest(
                command, AuthorizationResourceType.PROCESS_DEFINITION, PermissionType.UPDATE)
            .addResourceId(elementInstance.getValue().getBpmnProcessId());
    if (!authCheckBehavior.isAuthorized(request)) {
      final var errorMessage =
          UNAUTHORIZED_ERROR_MESSAGE.formatted(
              request.getPermissionType(), request.getResourceType());
      rejectionWriter.appendRejection(command, RejectionType.UNAUTHORIZED, errorMessage);
      responseWriter.writeRejectionOnCommand(command, RejectionType.UNAUTHORIZED, errorMessage);
      return false;
    }

    if (!TenantAuthorizationCheckerImpl.fromAuthorizationMap(command.getAuthorizations())
        .isAuthorized(elementInstance.getValue().getTenantId())) {
      rejectionWriter.appendRejection(
          command,
          RejectionType.NOT_FOUND,
          String.format(PROCESS_NOT_FOUND_MESSAGE, command.getKey()));
      responseWriter.writeRejectionOnCommand(
          command,
          RejectionType.NOT_FOUND,
          String.format(PROCESS_NOT_FOUND_MESSAGE, command.getKey()));
      return false;
    }

    final var parentProcessInstanceKey = elementInstance.getValue().getParentProcessInstanceKey();
    if (parentProcessInstanceKey > 0) {

      final var rootProcessInstanceKey = getRootProcessInstanceKey(parentProcessInstanceKey);

      rejectionWriter.appendRejection(
          command,
          RejectionType.INVALID_STATE,
          String.format(PROCESS_NOT_ROOT_MESSAGE, command.getKey(), rootProcessInstanceKey));
      responseWriter.writeRejectionOnCommand(
          command,
          RejectionType.INVALID_STATE,
          String.format(PROCESS_NOT_ROOT_MESSAGE, command.getKey(), rootProcessInstanceKey));
      return false;
    }

    return true;
  }

  private long getRootProcessInstanceKey(final long instanceKey) {

    final var instance = elementInstanceState.getInstance(instanceKey);
    if (instance != null) {

      final var parentProcessInstanceKey = instance.getValue().getParentProcessInstanceKey();
      if (parentProcessInstanceKey > 0) {

        return getRootProcessInstanceKey(parentProcessInstanceKey);
      }
    }
    return instanceKey;
  }
}
