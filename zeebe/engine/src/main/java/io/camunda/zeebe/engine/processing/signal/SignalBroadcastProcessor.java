/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.signal;

import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateBehavior;
import io.camunda.zeebe.engine.processing.common.EventHandle;
import io.camunda.zeebe.engine.processing.common.EventTriggerBehavior;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCatchEvent;
import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.ForbiddenException;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.NotFoundException;
import io.camunda.zeebe.engine.processing.streamprocessor.DistributedTypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.SignalSubscriptionState;
import io.camunda.zeebe.protocol.impl.record.value.signal.SignalRecord;
import io.camunda.zeebe.protocol.impl.record.value.signal.SignalSubscriptionRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.SignalIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import org.agrona.DirectBuffer;

public class SignalBroadcastProcessor implements DistributedTypedRecordProcessor<SignalRecord> {

  private final StateWriter stateWriter;
  private final KeyGenerator keyGenerator;
  private final EventHandle eventHandle;
  private final TypedResponseWriter responseWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final SignalSubscriptionState signalSubscriptionState;
  private final CommandDistributionBehavior commandDistributionBehavior;
  private final ProcessState processState;
  private final ElementInstanceState elementInstanceState;
  private final AuthorizationCheckBehavior authCheckBehavior;

  public SignalBroadcastProcessor(
      final Writers writers,
      final KeyGenerator keyGenerator,
      final ProcessingState processingState,
      final BpmnStateBehavior stateBehavior,
      final EventTriggerBehavior eventTriggerBehavior,
      final CommandDistributionBehavior commandDistributionBehavior,
      final AuthorizationCheckBehavior authCheckBehavior) {
    stateWriter = writers.state();
    responseWriter = writers.response();
    rejectionWriter = writers.rejection();
    processState = processingState.getProcessState();
    signalSubscriptionState = processingState.getSignalSubscriptionState();
    this.keyGenerator = keyGenerator;
    this.commandDistributionBehavior = commandDistributionBehavior;
    elementInstanceState = processingState.getElementInstanceState();
    this.authCheckBehavior = authCheckBehavior;
    eventHandle =
        new EventHandle(
            keyGenerator,
            processingState.getEventScopeInstanceState(),
            writers,
            processState,
            eventTriggerBehavior,
            stateBehavior);
  }

  @Override
  public void processNewCommand(final TypedRecord<SignalRecord> command) {
    final long eventKey = keyGenerator.nextKey();
    final var signalRecord = command.getValue();

    stateWriter.appendFollowUpEvent(eventKey, SignalIntent.BROADCASTED, signalRecord);

    signalSubscriptionState.visitBySignalName(
        signalRecord.getSignalNameBuffer(),
        signalRecord.getTenantId(),
        subscription -> {
          final var subscriptionRecord = subscription.getRecord();
          final var isStartEvent = subscriptionRecord.getCatchEventInstanceKey() == -1;
          checkAuthorization(command, isStartEvent, subscriptionRecord);

          if (isStartEvent) {
            eventHandle.activateProcessInstanceForStartEvent(
                subscriptionRecord.getProcessDefinitionKey(),
                keyGenerator.nextKey(),
                subscriptionRecord.getCatchEventIdBuffer(),
                signalRecord.getVariablesBuffer(),
                signalRecord.getTenantId());
          } else {
            activateElement(subscriptionRecord, signalRecord.getVariablesBuffer());
          }
        });

    if (command.hasRequestMetadata()) {
      responseWriter.writeEventOnCommand(eventKey, SignalIntent.BROADCASTED, signalRecord, command);
    }

    commandDistributionBehavior.withKey(eventKey).unordered().distribute(command);
  }

  @Override
  public void processDistributedCommand(final TypedRecord<SignalRecord> command) {
    final var value = command.getValue();
    signalSubscriptionState.visitBySignalName(
        value.getSignalNameBuffer(),
        value.getTenantId(),
        subscription -> activateElement(subscription.getRecord(), value.getVariablesBuffer()));

    stateWriter.appendFollowUpEvent(command.getKey(), SignalIntent.BROADCASTED, command.getValue());
    commandDistributionBehavior.acknowledgeCommand(command);
  }

  private void checkAuthorization(
      final TypedRecord<SignalRecord> command,
      final boolean isStartEvent,
      final SignalSubscriptionRecord subscriptionRecord) {
    final var permissionType =
        isStartEvent
            ? PermissionType.CREATE_PROCESS_INSTANCE
            : PermissionType.UPDATE_PROCESS_INSTANCE;
    final var authRequest =
        new AuthorizationRequest(
                command,
                AuthorizationResourceType.PROCESS_DEFINITION,
                permissionType,
                command.getValue().getTenantId())
            .addResourceId(subscriptionRecord.getBpmnProcessId());

    final var isAuthorized = authCheckBehavior.isAuthorized(authRequest);
    if (isAuthorized.isLeft()) {
      final var rejection = isAuthorized.getLeft();
      if (RejectionType.NOT_FOUND.equals(rejection.type())) {
        throw new NotFoundException(
            "Expected to broadcast signal with name '%s', but no such signal was found"
                .formatted(command.getValue().getSignalName()));
      }
      throw new ForbiddenException(authRequest);
    }
  }

  private void activateElement(
      final SignalSubscriptionRecord subscription, final DirectBuffer variables) {
    final var processDefinitionKey = subscription.getProcessDefinitionKey();
    final var catchEventInstanceKey = subscription.getCatchEventInstanceKey();
    final var catchEventId = subscription.getCatchEventIdBuffer();
    final var catchEvent =
        processState.getFlowElement(
            processDefinitionKey,
            subscription.getTenantId(),
            catchEventId,
            ExecutableCatchEvent.class);

    final var elementInstance = elementInstanceState.getInstance(catchEventInstanceKey);
    final var canTriggerElement = eventHandle.canTriggerElement(elementInstance, catchEventId);

    if (canTriggerElement) {
      eventHandle.activateElement(
          catchEvent, catchEventInstanceKey, elementInstance.getValue(), variables);
    }
  }

  @Override
  public ProcessingError tryHandleError(
      final TypedRecord<SignalRecord> command, final Throwable error) {

    switch (error) {
      case final NotFoundException exception:
        rejectionWriter.appendRejection(command, RejectionType.NOT_FOUND, exception.getMessage());
        responseWriter.writeRejectionOnCommand(
            command, RejectionType.NOT_FOUND, exception.getMessage());
        return ProcessingError.EXPECTED_ERROR;
      case final ForbiddenException exception:
        rejectionWriter.appendRejection(
            command, exception.getRejectionType(), exception.getMessage());
        responseWriter.writeRejectionOnCommand(
            command, exception.getRejectionType(), exception.getMessage());
        return ProcessingError.EXPECTED_ERROR;
      default:
        return ProcessingError.UNEXPECTED_ERROR;
    }
  }
}
