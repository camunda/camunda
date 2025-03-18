/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateBehavior;
import io.camunda.zeebe.engine.processing.common.EventHandle;
import io.camunda.zeebe.engine.processing.common.EventTriggerBehavior;
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
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

public final class MessagePublishProcessor implements TypedRecordProcessor<MessageRecord> {

  private static final String ALREADY_PUBLISHED_MESSAGE =
      "Expected to publish a new message with id '%s', but a message with that id was already published";

  private final MessageState messageState;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final MessageCorrelateBehavior correlateBehavior;

  private MessageRecord messageRecord;
  private long messageKey;
  private final TypedResponseWriter responseWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final AuthorizationCheckBehavior authCheckBehavior;

  public MessagePublishProcessor(
      final MessageState messageState,
      final MessageSubscriptionState subscriptionState,
      final MessageStartEventSubscriptionState startEventSubscriptionState,
      final EventScopeInstanceState eventScopeInstanceState,
      final SubscriptionCommandSender commandSender,
      final KeyGenerator keyGenerator,
      final Writers writers,
      final ProcessState processState,
      final EventTriggerBehavior eventTriggerBehavior,
      final BpmnStateBehavior stateBehavior,
      final AuthorizationCheckBehavior authCheckBehavior) {
    this.messageState = messageState;
    this.keyGenerator = keyGenerator;
    stateWriter = writers.state();
    responseWriter = writers.response();
    rejectionWriter = writers.rejection();
    this.authCheckBehavior = authCheckBehavior;
    final var eventHandle =
        new EventHandle(
            keyGenerator,
            eventScopeInstanceState,
            writers,
            processState,
            eventTriggerBehavior,
            stateBehavior);
    correlateBehavior =
        new MessageCorrelateBehavior(
            startEventSubscriptionState,
            messageState,
            eventHandle,
            stateWriter,
            subscriptionState,
            commandSender);
  }

  @Override
  public void processRecord(final TypedRecord<MessageRecord> command) {
    final var authRequest =
        new AuthorizationRequest(
            command,
            AuthorizationResourceType.MESSAGE,
            PermissionType.CREATE,
            command.getValue().getTenantId(),
            true);
    final var isAuthorized = authCheckBehavior.isAuthorized(authRequest);
    if (isAuthorized.isLeft()) {
      final var rejection = isAuthorized.getLeft();
      rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
      responseWriter.writeRejectionOnCommand(command, rejection.type(), rejection.reason());
      return;
    }

    messageRecord = command.getValue();

    if (messageRecord.hasMessageId()
        && messageState.exist(
            messageRecord.getNameBuffer(),
            messageRecord.getCorrelationKeyBuffer(),
            messageRecord.getMessageIdBuffer(),
            messageRecord.getTenantId())) {
      final String rejectionReason =
          String.format(
              ALREADY_PUBLISHED_MESSAGE, bufferAsString(messageRecord.getMessageIdBuffer()));

      rejectionWriter.appendRejection(command, RejectionType.ALREADY_EXISTS, rejectionReason);
      responseWriter.writeRejectionOnCommand(
          command, RejectionType.ALREADY_EXISTS, rejectionReason);
    } else {
      handleNewMessage(command);
    }
  }

  private void handleNewMessage(final TypedRecord<MessageRecord> command) {
    messageKey = keyGenerator.nextKey();

    // calculate the deadline based on the command's timestamp
    messageRecord.setDeadline(command.getTimestamp() + messageRecord.getTimeToLive());

    stateWriter.appendFollowUpEvent(messageKey, MessageIntent.PUBLISHED, command.getValue());
    responseWriter.writeEventOnCommand(
        messageKey, MessageIntent.PUBLISHED, command.getValue(), command);

    final var correlatingSubscriptions = new Subscriptions();
    final var messageData = createMessageData(messageKey, messageRecord);
    correlateBehavior.correlateToMessageEvents(messageData, correlatingSubscriptions);
    correlateBehavior.correlateToMessageStartEvents(messageData, correlatingSubscriptions);
    correlateBehavior.sendCorrelateCommands(messageData, correlatingSubscriptions);

    if (messageRecord.getTimeToLive() <= 0L) {
      // avoid that the message can be correlated again by writing the EXPIRED event as a follow-up
      stateWriter.appendFollowUpEvent(messageKey, MessageIntent.EXPIRED, messageRecord);
    }
  }

  private MessageData createMessageData(
      final long messageKey, final MessageRecord messageCorrelationRecord) {
    return new MessageData(
        messageKey,
        messageCorrelationRecord.getNameBuffer(),
        messageCorrelationRecord.getCorrelationKeyBuffer(),
        messageCorrelationRecord.getVariablesBuffer(),
        messageCorrelationRecord.getTenantId());
  }
}
