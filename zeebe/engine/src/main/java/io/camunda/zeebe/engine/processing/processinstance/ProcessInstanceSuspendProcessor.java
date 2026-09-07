/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import io.camunda.security.core.auth.RequiredAuthorization;
import io.camunda.zeebe.engine.metrics.SuspensionMetrics;
import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.identity.AuthorizationRejectionMapper;
import io.camunda.zeebe.engine.processing.identity.authorization.CslAuthorizationCheck;
import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.processing.streamprocessor.SuspensionAware;
import io.camunda.zeebe.engine.processing.streamprocessor.SuspensionAware.SuspensionBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.AsyncRequestState;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.SuspensionState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.engine.state.message.TransientPendingSubscriptionState;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.mapper.AuthzModelMapper;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.time.InstantSource;

public final class ProcessInstanceSuspendProcessor
    implements TypedRecordProcessor<ProcessInstanceRecord>, SuspensionAware<ProcessInstanceRecord> {

  private static final String MESSAGE_PREFIX =
      "Expected to suspend a process instance with key '%d', but ";

  private static final String PROCESS_NOT_FOUND_MESSAGE =
      MESSAGE_PREFIX + "no such process was found";
  private static final String PROCESS_CANCEL_IN_PROGRESS_MESSAGE =
      MESSAGE_PREFIX + "a cancel request is already in progress";
  private static final String PROCESS_ALREADY_SUSPENDED_MESSAGE =
      MESSAGE_PREFIX + "it is already suspended";

  private final ElementInstanceState elementInstanceState;
  private final TypedResponseWriter responseWriter;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final CslAuthorizationCheck cslCheck;
  private final AsyncRequestState asyncRequestState;
  private final SuspensionState suspensionState;
  private final ProcessInstanceSuspensionJobBehavior suspensionJobBehavior;
  private final ProcessInstanceSuspensionMessageSubscriptionBehavior suspensionSubscriptionBehavior;
  private final SuspensionMetrics suspensionMetrics;

  public ProcessInstanceSuspendProcessor(
      final ProcessingState processingState,
      final Writers writers,
      final CslAuthorizationCheck cslCheck,
      final SubscriptionCommandSender subscriptionCommandSender,
      final TransientPendingSubscriptionState transientProcessMessageSubscriptionState,
      final InstantSource clock,
      final SuspensionMetrics suspensionMetrics) {
    elementInstanceState = processingState.getElementInstanceState();
    responseWriter = writers.response();
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    this.cslCheck = cslCheck;
    asyncRequestState = processingState.getAsyncRequestState();
    suspensionState = processingState.getSuspensionState();
    suspensionJobBehavior =
        new ProcessInstanceSuspensionJobBehavior(
            elementInstanceState, processingState.getJobState(), stateWriter);
    suspensionSubscriptionBehavior =
        new ProcessInstanceSuspensionMessageSubscriptionBehavior(
            elementInstanceState,
            processingState.getProcessMessageSubscriptionState(),
            stateWriter,
            writers.sideEffect(),
            subscriptionCommandSender,
            transientProcessMessageSubscriptionState,
            clock);
    this.suspensionMetrics = suspensionMetrics;
  }

  @Override
  public void processRecord(final TypedRecord<ProcessInstanceRecord> command) {
    final var elementInstance = elementInstanceState.getInstance(command.getKey());

    if (!validateCommand(command, elementInstance)) {
      return;
    }

    final ProcessInstanceRecord value = elementInstance.getValue();
    // Park jobs before the instance-level SUSPENDED event so suspension is complete when the
    // marker is written. A later SUSPENDING intermediate state can chunk this work first.
    final int suspendedJobCount = suspensionJobBehavior.suspendJobs(command.getKey());
    suspensionSubscriptionBehavior.closeSubscriptions(command.getKey());
    stateWriter.appendFollowUpEvent(command.getKey(), ProcessInstanceIntent.SUSPENDED, value);
    responseWriter.writeAcceptedResponseOnCommand(
        command.getKey(), ProcessInstanceIntent.SUSPENDED, value, command);
    suspensionMetrics.instanceSuspended();
    if (suspendedJobCount > 0) {
      suspensionMetrics.jobsSuspended(suspendedJobCount);
    }
  }

  private boolean validateCommand(
      final TypedRecord<ProcessInstanceRecord> command, final ElementInstance elementInstance) {

    if (elementInstance == null
        || elementInstance.getParentKey() > 0
        || elementInstance.isTerminating()) {
      final var reason = String.format(PROCESS_NOT_FOUND_MESSAGE, command.getKey());
      rejectionWriter.appendRejection(command, RejectionType.NOT_FOUND, reason);
      responseWriter.writeRejectedResponseOnCommand(command, RejectionType.NOT_FOUND, reason);
      return false;
    }

    final var isAuthorized =
        cslCheck.checkAuthorizationAndTenant(
            command,
            RequiredAuthorization.of(
                b ->
                    b.resourceType(
                            AuthzModelMapper.fromProtocol(
                                AuthorizationResourceType.PROCESS_DEFINITION))
                        .permissionType(
                            AuthzModelMapper.fromProtocol(PermissionType.SUSPEND_PROCESS_INSTANCE))
                        .resourceId(elementInstance.getValue().getBpmnProcessId())),
            elementInstance.getValue(),
            AuthorizationRejectionMapper.forbidden(
                PermissionType.SUSPEND_PROCESS_INSTANCE,
                AuthorizationResourceType.PROCESS_DEFINITION),
            elementInstance.getValue().getTenantId(),
            new Rejection(
                RejectionType.NOT_FOUND,
                PROCESS_NOT_FOUND_MESSAGE.formatted(
                    elementInstance.getValue().getProcessInstanceKey())));
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

    if (suspensionState.isSuspended(command.getKey())) {
      final var reason = String.format(PROCESS_ALREADY_SUSPENDED_MESSAGE, command.getKey());
      enrichRejectionCommand(command, elementInstance.getValue());
      rejectionWriter.appendRejection(command, RejectionType.INVALID_STATE, reason);
      responseWriter.writeRejectedResponseOnCommand(command, RejectionType.INVALID_STATE, reason);
      return false;
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

  @Override
  public SuspensionBehavior suspensionBehavior(final TypedRecord<ProcessInstanceRecord> record) {
    // reject: a repeated suspend while a marker is present must fail like the processor's own
    // "already suspended" rejection; buffering would silently swallow it.
    return SuspensionBehavior.REJECT;
  }
}
