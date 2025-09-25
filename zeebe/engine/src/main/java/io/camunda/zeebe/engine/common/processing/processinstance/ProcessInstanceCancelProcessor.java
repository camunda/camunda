/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.common.processing.processinstance;

import io.camunda.zeebe.engine.common.processing.AsyncRequestBehavior;
import io.camunda.zeebe.engine.common.processing.identity.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.common.processing.identity.AuthorizationCheckBehavior.AuthorizationRequest;
import io.camunda.zeebe.engine.common.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.common.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.common.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.common.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.common.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.common.state.immutable.AsyncRequestState;
import io.camunda.zeebe.engine.common.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.common.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.common.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.Optional;

public final class ProcessInstanceCancelProcessor
    implements TypedRecordProcessor<ProcessInstanceRecord> {

  private static final String MESSAGE_PREFIX =
      "Expected to cancel a process instance with key '%d', but ";

  private static final String PROCESS_NOT_FOUND_MESSAGE =
      MESSAGE_PREFIX + "no such process was found";

  private static final String PROCESS_NOT_ROOT_MESSAGE =
      MESSAGE_PREFIX
          + "it is created by a parent process instance. Cancel the root process instance '%d' instead.";

  private static final String PROCESS_CANCEL_IN_PROGRESS_MESSAGE =
      MESSAGE_PREFIX + "a cancel request is already in progress";

  private final ElementInstanceState elementInstanceState;
  private final AsyncRequestState asyncRequestState;
  private final TypedResponseWriter responseWriter;
  private final TypedCommandWriter commandWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final AsyncRequestBehavior asyncRequestBehavior;
  private final AuthorizationCheckBehavior authCheckBehavior;

  public ProcessInstanceCancelProcessor(
      final ProcessingState processingState,
      final Writers writers,
      final AsyncRequestBehavior asyncRequestBehavior,
      final AuthorizationCheckBehavior authCheckBehavior) {
    elementInstanceState = processingState.getElementInstanceState();
    asyncRequestState = processingState.getAsyncRequestState();
    responseWriter = writers.response();
    commandWriter = writers.command();
    rejectionWriter = writers.rejection();
    this.asyncRequestBehavior = asyncRequestBehavior;
    this.authCheckBehavior = authCheckBehavior;
  }

  @Override
  public void processRecord(final TypedRecord<ProcessInstanceRecord> command) {
    final var elementInstance = elementInstanceState.getInstance(command.getKey());

    if (!validateCommand(command, elementInstance)) {
      return;
    }

    asyncRequestBehavior.writeAsyncRequestReceived(command.getKey(), command);

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
                command,
                AuthorizationResourceType.PROCESS_DEFINITION,
                PermissionType.CANCEL_PROCESS_INSTANCE,
                elementInstance.getValue().getTenantId())
            .addResourceId(elementInstance.getValue().getBpmnProcessId());
    final var isAuthorized = authCheckBehavior.isAuthorized(request);
    if (isAuthorized.isLeft()) {
      final var rejection = isAuthorized.getLeft();
      final String errorMessage =
          RejectionType.NOT_FOUND.equals(rejection.type())
              ? AuthorizationCheckBehavior.NOT_FOUND_ERROR_MESSAGE.formatted(
                  "cancel a process instance",
                  elementInstance.getValue().getProcessInstanceKey(),
                  "such process")
              : rejection.reason();
      rejectionWriter.appendRejection(command, rejection.type(), errorMessage);
      responseWriter.writeRejectionOnCommand(command, rejection.type(), errorMessage);
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

    final var existingAsyncRequest =
        asyncRequestState.findRequest(
            command.getKey(), ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.CANCEL);
    if (existingAsyncRequest.isPresent()) {
      final String reason = String.format(PROCESS_CANCEL_IN_PROGRESS_MESSAGE, command.getKey());
      rejectionWriter.appendRejection(command, RejectionType.INVALID_STATE, reason);
      responseWriter.writeRejectionOnCommand(command, RejectionType.INVALID_STATE, reason);
      return false;
    }

    return true;
  }

  private long getRootProcessInstanceKey(long instanceKey) {
    var parentInstanceKey = getParentInstanceKey(instanceKey);
    while (parentInstanceKey.isPresent()) {
      instanceKey = parentInstanceKey.get();
      parentInstanceKey = getParentInstanceKey(instanceKey);
    }

    return instanceKey;
  }

  private Optional<Long> getParentInstanceKey(final long instanceKey) {
    final var instance = elementInstanceState.getInstance(instanceKey);
    if (instance != null) {
      final var parentProcessInstanceKey = instance.getValue().getParentProcessInstanceKey();
      if (parentProcessInstanceKey > 0) {
        return Optional.of(parentProcessInstanceKey);
      }
    }
    return Optional.empty();
  }
}
