/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import io.camunda.zeebe.engine.processing.AsyncRequestBehavior;
import io.camunda.zeebe.engine.processing.identity.authorization.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.authorization.request.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.AsyncRequestState;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.JobState.State;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.protocol.record.value.JobListenerEventType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

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

  private static final Set<State> CANCELABLE_STATES =
      EnumSet.of(State.ACTIVATABLE, State.ACTIVATED, State.FAILED, State.ERROR_THROWN);

  private final ElementInstanceState elementInstanceState;
  private final AsyncRequestState asyncRequestState;
  private final TypedResponseWriter responseWriter;
  private final TypedCommandWriter commandWriter;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final AsyncRequestBehavior asyncRequestBehavior;
  private final AuthorizationCheckBehavior authCheckBehavior;
  private final JobState jobState;

  public ProcessInstanceCancelProcessor(
      final ProcessingState processingState,
      final Writers writers,
      final AsyncRequestBehavior asyncRequestBehavior,
      final AuthorizationCheckBehavior authCheckBehavior) {
    elementInstanceState = processingState.getElementInstanceState();
    asyncRequestState = processingState.getAsyncRequestState();
    jobState = processingState.getJobState();
    responseWriter = writers.response();
    commandWriter = writers.command();
    stateWriter = writers.state();
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

    final ProcessInstanceRecord value = elementInstance.getValue();

    if (elementInstance.isTerminating()) {
      // Only force-cancel when a cancel execution listener job is active.
      // Otherwise reject — the instance is terminating normally (e.g., waiting for children).
      if (hasActiveCancelListenerJob(elementInstance)) {
        commandWriter.appendFollowUpCommand(
            command.getKey(), ProcessInstanceIntent.CONTINUE_TERMINATING_ELEMENT, value);
        responseWriter.writeEventOnCommand(
            command.getKey(), ProcessInstanceIntent.ELEMENT_TERMINATING, value, command);
      } else {
        final String reason = String.format(PROCESS_CANCEL_IN_PROGRESS_MESSAGE, command.getKey());
        enrichRejectionCommand(command, value);
        rejectionWriter.appendRejection(command, RejectionType.INVALID_STATE, reason);
        responseWriter.writeRejectionOnCommand(command, RejectionType.INVALID_STATE, reason);
      }
    } else {
      asyncRequestBehavior.writeAsyncRequestReceived(command.getKey(), command);
      stateWriter.appendFollowUpEvent(command.getKey(), ProcessInstanceIntent.CANCELING, value);
      commandWriter.appendFollowUpCommand(
          command.getKey(), ProcessInstanceIntent.TERMINATE_ELEMENT, value);
      responseWriter.writeEventOnCommand(
          command.getKey(), ProcessInstanceIntent.ELEMENT_TERMINATING, value, command);
    }
  }

  private boolean validateCommand(
      final TypedRecord<ProcessInstanceRecord> command, final ElementInstance elementInstance) {

    if (elementInstance == null
        || (!elementInstance.canTerminate() && !elementInstance.isTerminating())
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
        AuthorizationRequest.builder()
            .command(command)
            .resourceType(AuthorizationResourceType.PROCESS_DEFINITION)
            .permissionType(PermissionType.CANCEL_PROCESS_INSTANCE)
            .tenantId(elementInstance.getValue().getTenantId())
            .addResourceId(elementInstance.getValue().getBpmnProcessId())
            .build();
    final var isAuthorized = authCheckBehavior.isAuthorizedOrInternalCommand(request);
    if (isAuthorized.isLeft()) {
      final var rejection = isAuthorized.getLeft();
      final String errorMessage =
          RejectionType.NOT_FOUND.equals(rejection.type())
              ? AuthorizationCheckBehavior.NOT_FOUND_ERROR_MESSAGE.formatted(
                  "cancel a process instance",
                  elementInstance.getValue().getProcessInstanceKey(),
                  "such process")
              : rejection.reason();
      enrichRejectionCommand(command, elementInstance.getValue());
      rejectionWriter.appendRejection(command, rejection.type(), errorMessage);
      responseWriter.writeRejectionOnCommand(command, rejection.type(), errorMessage);
      return false;
    }

    final var parentProcessInstanceKey = elementInstance.getValue().getParentProcessInstanceKey();
    if (parentProcessInstanceKey > 0) {

      final var rootProcessInstanceKey = getRootProcessInstanceKey(parentProcessInstanceKey);

      enrichRejectionCommand(command, elementInstance.getValue());
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

    // Reject duplicate cancel when element is not yet in terminating state (follow-ups from
    // the first cancel haven't been processed yet). When the element IS terminating (e.g.,
    // cancel ELs are blocking), allow through for force-cancel.
    if (!elementInstance.isTerminating()) {
      final var existingAsyncRequest =
          asyncRequestState.findRequest(
              command.getKey(), ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.CANCEL);
      if (existingAsyncRequest.isPresent()) {
        final String reason = String.format(PROCESS_CANCEL_IN_PROGRESS_MESSAGE, command.getKey());
        enrichRejectionCommand(command, elementInstance.getValue());
        rejectionWriter.appendRejection(command, RejectionType.INVALID_STATE, reason);
        responseWriter.writeRejectionOnCommand(command, RejectionType.INVALID_STATE, reason);
        return false;
      }
    }

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

  private boolean hasActiveCancelListenerJob(final ElementInstance elementInstance) {
    final long jobKey = elementInstance.getJobKey();
    if (jobKey <= 0) {
      return false;
    }
    final State state = jobState.getState(jobKey);
    if (!CANCELABLE_STATES.contains(state)) {
      return false;
    }
    final var job = jobState.getJob(jobKey);
    return job.getJobKind() == JobKind.EXECUTION_LISTENER
        && job.getJobListenerEventType() == JobListenerEventType.CANCEL;
  }
}
