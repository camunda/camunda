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
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.processing.usertask.UserTaskSuspensionBehavior;
import io.camunda.zeebe.engine.state.immutable.AsyncRequestState;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.SuspensionState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.engine.state.message.TransientPendingSubscriptionState;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.BufferedCommandRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BufferedCommandIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.mapper.AuthzModelMapper;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
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
  private final TypedCommandWriter commandWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final CslAuthorizationCheck cslCheck;
  private final AsyncRequestState asyncRequestState;
  private final SuspensionState suspensionState;
  private final ProcessInstanceSuspensionJobBehavior suspensionJobBehavior;
  private final ProcessInstanceSuspensionMessageSubscriptionBehavior suspensionSubscriptionBehavior;
  private final UserTaskSuspensionBehavior userTaskSuspensionBehavior;
  private final SuspensionMetrics suspensionMetrics;

  public ProcessInstanceSuspendProcessor(
      final ProcessingState processingState,
      final Writers writers,
      final CslAuthorizationCheck cslCheck,
      final SubscriptionCommandSender subscriptionCommandSender,
      final TransientPendingSubscriptionState transientProcessMessageSubscriptionState,
      final InstantSource clock,
      final KeyGenerator keyGenerator,
      final SuspensionMetrics suspensionMetrics) {
    elementInstanceState = processingState.getElementInstanceState();
    responseWriter = writers.response();
    stateWriter = writers.state();
    commandWriter = writers.command();
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
    userTaskSuspensionBehavior =
        new UserTaskSuspensionBehavior(elementInstanceState, stateWriter, keyGenerator);
    this.suspensionMetrics = suspensionMetrics;
  }

  @Override
  public void processRecord(final TypedRecord<ProcessInstanceRecord> command) {
    final var elementInstance = elementInstanceState.getInstance(command.getKey());

    if (!validateCommand(command, elementInstance)) {
      return;
    }

    final ProcessInstanceRecord value = elementInstance.getValue();
    stateWriter.appendFollowUpEvent(command.getKey(), ProcessInstanceIntent.SUSPENDING, value);
    final int suspendedJobCount = closeSubscriptionsAndSuspendJobs(command.getKey());

    // buffer user task suspension events and drain them in a follow-up command to avoid writing
    // too many events in a single transaction
    bufferUserTasksSuspension(command, elementInstance, value);
    responseWriter.writeAcceptedResponseOnCommand(
        command.getKey(), ProcessInstanceIntent.SUSPENDING, value, command);
    if (suspendedJobCount > 0) {
      suspensionMetrics.jobsSuspended(suspendedJobCount);
    }
  }

  private void bufferUserTasksSuspension(
      final TypedRecord<ProcessInstanceRecord> command,
      final ElementInstance elementInstance,
      final ProcessInstanceRecord value) {
    final var userTaskKeys = userTaskSuspensionBehavior.collectUserTaskKeys(elementInstance);
    if (userTaskKeys.isEmpty()) {
      commandWriter.appendFollowUpCommand(
          command.getKey(), ProcessInstanceIntent.COMPLETE_SUSPENDING, value);
    } else {
      userTaskSuspensionBehavior.bufferEvents(value, userTaskKeys, UserTaskIntent.SUSPENDED);
      commandWriter.appendFollowUpCommand(
          command.getKey(),
          BufferedCommandIntent.DRAIN,
          new BufferedCommandRecord()
              .setProcessInstanceKey(command.getKey())
              .setProcessDefinitionKey(value.getProcessDefinitionKey())
              .setTenantId(value.getTenantId()));
    }
  }

  /**
   * Keep this order, closing subscriptions first keeps RocksDB seeks cheap. Currently, subscription
   * closures visit all element instance subscriptions which runs a RocksDB seek command. If the
   * order is reversed, job suspensions will write to the transaction batch first, which requires
   * the seek command to also check against those batched writes. See <a
   * href="https://github.com/camunda/camunda/issues/62933">#62933</a>.
   */
  private int closeSubscriptionsAndSuspendJobs(final long processInstanceKey) {
    suspensionSubscriptionBehavior.closeSubscriptions(processInstanceKey);
    return suspensionJobBehavior.suspendJobs(processInstanceKey);
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

    // Check marker presence so all duplicate suspend requests are rejected.
    if (suspensionState.getSuspensionState(command.getKey()) != null) {
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
  public SuspensionAction onSuspended(final TypedRecord<ProcessInstanceRecord> record) {
    // reject: a repeated suspend while a marker is present must fail like the processor's own
    // "already suspended" rejection; buffering would silently swallow it.
    return SuspensionAction.REJECT;
  }

  @Override
  public SuspensionAction onResuming(final TypedRecord<ProcessInstanceRecord> record) {
    return SuspensionAction.REJECT;
  }
}
