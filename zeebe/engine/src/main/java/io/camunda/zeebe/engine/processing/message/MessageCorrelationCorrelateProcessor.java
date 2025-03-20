/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.common.EventHandle;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.message.MessageCorrelateBehavior.MessageData;
import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.EventScopeInstanceState;
import io.camunda.zeebe.engine.state.immutable.MessageStartEventSubscriptionState;
import io.camunda.zeebe.engine.state.immutable.MessageState;
import io.camunda.zeebe.engine.state.immutable.MessageSubscriptionState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageCorrelationRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.MessageCorrelationIntent;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class MessageCorrelationCorrelateProcessor
    implements TypedRecordProcessor<MessageCorrelationRecord> {

  private static final String SUBSCRIPTION_NOT_FOUND =
      "Expected to find subscription for message with name '%s' and correlation key '%s', but none was found.";

  private final MessageCorrelateBehavior correlateBehavior;
  private final KeyGenerator keyGenerator;
  private final AuthorizationCheckBehavior authCheckBehavior;
  private final StateWriter stateWriter;
  private final TypedResponseWriter responseWriter;
  private final TypedRejectionWriter rejectionWriter;

  public MessageCorrelationCorrelateProcessor(
      final Writers writers,
      final KeyGenerator keyGenerator,
      final EventScopeInstanceState eventScopeInstanceState,
      final ProcessState processState,
      final BpmnBehaviors bpmnBehaviors,
      final MessageStartEventSubscriptionState startEventSubscriptionState,
      final MessageState messageState,
      final MessageSubscriptionState messageSubscriptionState,
      final SubscriptionCommandSender commandSender,
      final AuthorizationCheckBehavior authCheckBehavior) {
    stateWriter = writers.state();
    responseWriter = writers.response();
    rejectionWriter = writers.rejection();
    this.keyGenerator = keyGenerator;
    this.authCheckBehavior = authCheckBehavior;
    final var eventHandle =
        new EventHandle(
            keyGenerator,
            eventScopeInstanceState,
            writers,
            processState,
            bpmnBehaviors.eventTriggerBehavior(),
            bpmnBehaviors.stateBehavior());
    correlateBehavior =
        new MessageCorrelateBehavior(
            startEventSubscriptionState,
            messageState,
            eventHandle,
            stateWriter,
            messageSubscriptionState,
            commandSender);
  }

  @Override
  public void processRecord(final TypedRecord<MessageCorrelationRecord> command) {
    final var messageCorrelationRecord = command.getValue();

    if (!authCheckBehavior.isAssignedToTenant(command, messageCorrelationRecord.getTenantId())) {
      final var message =
          "Expected to correlate message for tenant '%s', but user is not assigned to this tenant."
              .formatted(messageCorrelationRecord.getTenantId());
      rejectionWriter.appendRejection(command, RejectionType.FORBIDDEN, message);
      responseWriter.writeRejectionOnCommand(command, RejectionType.FORBIDDEN, message);
      return;
    }

    final long messageKey = keyGenerator.nextKey();
    messageCorrelationRecord
        .setMessageKey(messageKey)
        .setRequestId(command.getRequestId())
        .setRequestStreamId(command.getRequestStreamId());

    final var messageRecord =
        new MessageRecord()
            .setName(command.getValue().getName())
            .setCorrelationKey(command.getValue().getCorrelationKey())
            .setVariables(command.getValue().getVariablesBuffer())
            .setTenantId(command.getValue().getTenantId())
            .setTimeToLive(-1L);
    stateWriter.appendFollowUpEvent(messageKey, MessageIntent.PUBLISHED, messageRecord);

    stateWriter.appendFollowUpEvent(
        messageKey, MessageCorrelationIntent.CORRELATING, messageCorrelationRecord);

    final var correlatingSubscriptions = new Subscriptions();
    final var messageData = createMessageData(messageKey, messageCorrelationRecord);
    correlateBehavior.correlateToMessageEvents(messageData, correlatingSubscriptions);
    correlateBehavior.correlateToMessageStartEvents(messageData, correlatingSubscriptions);

    final var authorizationRejectionOptional =
        isAuthorizedForAllSubscriptions(
            command, correlatingSubscriptions, messageCorrelationRecord.getTenantId());
    if (authorizationRejectionOptional.isPresent()) {
      final var rejection = authorizationRejectionOptional.get();
      rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
      responseWriter.writeRejectionOnCommand(command, rejection.type(), rejection.reason());
      return;
    }

    if (correlatingSubscriptions.isEmpty()) {
      final var errorMessage =
          SUBSCRIPTION_NOT_FOUND.formatted(
              command.getValue().getName(), command.getValue().getCorrelationKey());
      rejectionWriter.appendRejection(command, RejectionType.NOT_FOUND, errorMessage);
      responseWriter.writeRejectionOnCommand(command, RejectionType.NOT_FOUND, errorMessage);
    } else {
      correlatingSubscriptions
          .getFirstMessageStartEventSubscription()
          .ifPresent(
              subscription -> {
                messageCorrelationRecord.setProcessInstanceKey(
                    subscription.getProcessInstanceKey());

                stateWriter.appendFollowUpEvent(
                    messageKey, MessageCorrelationIntent.CORRELATED, messageCorrelationRecord);
                responseWriter.writeEventOnCommand(
                    messageKey,
                    MessageCorrelationIntent.CORRELATED,
                    messageCorrelationRecord,
                    command);
              });
    }

    correlateBehavior.sendCorrelateCommands(messageData, correlatingSubscriptions);

    // Message Correlate command cannot have a TTL. As a result the message expires immediately.
    stateWriter.appendFollowUpEvent(messageKey, MessageIntent.EXPIRED, messageRecord);
  }

  private MessageData createMessageData(
      final long messageKey, final MessageCorrelationRecord messageCorrelationRecord) {
    return new MessageData(
        messageKey,
        messageCorrelationRecord.getNameBuffer(),
        messageCorrelationRecord.getCorrelationKeyBuffer(),
        messageCorrelationRecord.getVariablesBuffer(),
        messageCorrelationRecord.getTenantId());
  }

  private Optional<Rejection> isAuthorizedForAllSubscriptions(
      final TypedRecord<MessageCorrelationRecord> command,
      final Subscriptions correlatingSubscriptions,
      final String tenantId) {
    final AtomicReference<AuthorizationRequest> request = new AtomicReference<>();
    final AtomicReference<Rejection> rejection = new AtomicReference<>();

    final var isAuthorized =
        correlatingSubscriptions.visitSubscriptions(
            subscription -> {
              final PermissionType permissionType =
                  subscription.isStartEventSubscription()
                      ? PermissionType.CREATE_PROCESS_INSTANCE
                      : PermissionType.UPDATE_PROCESS_INSTANCE;

              request.set(
                  new AuthorizationRequest(
                      command,
                      AuthorizationResourceType.PROCESS_DEFINITION,
                      permissionType,
                      tenantId));

              final var processIdString = bufferAsString(subscription.getBpmnProcessId());
              request.get().addResourceId(processIdString);
              final var rejectionOrAuthorized = authCheckBehavior.isAuthorized(request.get());
              rejectionOrAuthorized.ifLeft(rejection::set);
              return rejectionOrAuthorized.isRight();
            },
            true);

    if (!isAuthorized) {
      return Optional.of(rejection.get());
    }

    return Optional.empty();
  }
}
