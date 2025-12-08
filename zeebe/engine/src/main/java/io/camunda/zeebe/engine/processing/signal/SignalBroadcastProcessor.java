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
import io.camunda.zeebe.engine.processing.identity.authorization.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.authorization.exception.ForbiddenException;
import io.camunda.zeebe.engine.processing.identity.authorization.request.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.streamprocessor.DistributedTypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.SignalSubscriptionState;
import io.camunda.zeebe.engine.state.signal.SignalSubscription;
import io.camunda.zeebe.protocol.impl.encoding.AuthInfo;
import io.camunda.zeebe.protocol.impl.encoding.AuthInfo.AuthDataFormat;
import io.camunda.zeebe.protocol.impl.record.value.signal.SignalRecord;
import io.camunda.zeebe.protocol.impl.record.value.signal.SignalSubscriptionRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.SignalIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.util.ArrayList;
import java.util.List;
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

    final var isAuthNeeded = !command.isInternalCommand();

    // Check tenant authorization if not an internal command
    if (isAuthNeeded) {
      if (!authCheckBehavior.isAssignedToTenant(command, signalRecord.getTenantId())) {
        final var message =
            "Expected to broadcast signal for tenant '%s', but user is not assigned to this tenant."
                .formatted(signalRecord.getTenantId());
        rejectionWriter.appendRejection(command, RejectionType.FORBIDDEN, message);
        responseWriter.writeRejectionOnCommand(command, RejectionType.FORBIDDEN, message);
        return;
      }
    }

    triggerSignal(command, eventKey, isAuthNeeded);

    // differentiate between internal and external commands for distribution
    // if the command is external we distribute the authInfo coming from the command
    // if the command is internal we distribute the authInfo as PRE_AUTHORIZED
    if (isAuthNeeded) {
      commandDistributionBehavior.withKey(eventKey).unordered().distribute(command);
    } else {
      final var authInfo = new AuthInfo();
      authInfo.setFormat(AuthDataFormat.PRE_AUTHORIZED);
      commandDistributionBehavior
          .withKey(eventKey)
          .unordered()
          .distribute(command.getValueType(), command.getIntent(), command.getValue(), authInfo);
    }
  }

  @Override
  public void processDistributedCommand(final TypedRecord<SignalRecord> command) {
    final var isAuthNeeded = !(command.getAuthInfo().getFormat() == AuthDataFormat.PRE_AUTHORIZED);
    triggerSignal(command, command.getKey(), isAuthNeeded);
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
        AuthorizationRequest.builder()
            .command(command)
            .resourceType(AuthorizationResourceType.PROCESS_DEFINITION)
            .permissionType(permissionType)
            .tenantId(command.getValue().getTenantId())
            .addResourceId(subscriptionRecord.getBpmnProcessId())
            .build();
    final var isAuthorized = authCheckBehavior.isAuthorized(authRequest);
    if (isAuthorized.isLeft()) {
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
    if (error instanceof final ForbiddenException exception) {
      rejectionWriter.appendRejection(
          command, exception.getRejectionType(), exception.getMessage());
      responseWriter.writeRejectionOnCommand(
          command, exception.getRejectionType(), exception.getMessage());
      if (command.isCommandDistributed()) {
        // If the command was distributed but doesn't pass auth checks on this partition, we still
        // need to acknowledge the command to avoid it being stuck in retry.
        commandDistributionBehavior.acknowledgeCommand(command);
      }
      return ProcessingError.EXPECTED_ERROR;
    }
    return ProcessingError.UNEXPECTED_ERROR;
  }

  private void triggerSignal(
      final TypedRecord<SignalRecord> command, final long eventKey, final boolean isAuthNeeded) {
    final var signalRecord = command.getValue();

    final List<SignalSubscription> subscriptions = new ArrayList<>();
    signalSubscriptionState.visitBySignalName(
        signalRecord.getSignalNameBuffer(),
        signalRecord.getTenantId(),
        subscription -> {
          final var subscriptionRecord = subscription.getRecord();
          final var isStartEvent = subscriptionRecord.getCatchEventInstanceKey() == -1;
          if (isAuthNeeded) {
            checkAuthorization(command, isStartEvent, subscriptionRecord);
          }
          final var copiedSubscription = new SignalSubscription();
          copiedSubscription.copyFrom(subscription);
          subscriptions.add(copiedSubscription);
        });

    stateWriter.appendFollowUpEvent(eventKey, SignalIntent.BROADCASTED, signalRecord);

    subscriptions.forEach(
        subscription -> {
          final var subscriptionRecord = subscription.getRecord();
          final var isStartEvent = subscriptionRecord.getCatchEventInstanceKey() == -1;
          if (isStartEvent && !command.isCommandDistributed()) {
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
  }
}
