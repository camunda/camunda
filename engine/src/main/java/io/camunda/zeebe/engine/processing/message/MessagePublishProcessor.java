/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.message;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.zeebe.engine.api.TypedRecord;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateBehavior;
import io.camunda.zeebe.engine.processing.common.EventHandle;
import io.camunda.zeebe.engine.processing.common.EventTriggerBehavior;
import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.SideEffectWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.KeyGenerator;
import io.camunda.zeebe.engine.state.immutable.EventScopeInstanceState;
import io.camunda.zeebe.engine.state.immutable.MessageStartEventSubscriptionState;
import io.camunda.zeebe.engine.state.immutable.MessageState;
import io.camunda.zeebe.engine.state.immutable.MessageSubscriptionState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;

public final class MessagePublishProcessor implements TypedRecordProcessor<MessageRecord> {

  private static final String ALREADY_PUBLISHED_MESSAGE =
      "Expected to publish a new message with id '%s', but a message with that id was already published";

  private final MessageState messageState;
  private final MessageSubscriptionState subscriptionState;
  private final MessageStartEventSubscriptionState startEventSubscriptionState;
  private final SubscriptionCommandSender commandSender;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;

  private final EventHandle eventHandle;
  private final Subscriptions correlatingSubscriptions = new Subscriptions();

  private MessageRecord messageRecord;
  private long messageKey;
  private final TypedResponseWriter responseWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final SideEffectWriter sideEffectWriter;
  private final int currentPartitionId;

  public MessagePublishProcessor(
      final int partitionId,
      final MessageState messageState,
      final MessageSubscriptionState subscriptionState,
      final MessageStartEventSubscriptionState startEventSubscriptionState,
      final EventScopeInstanceState eventScopeInstanceState,
      final SubscriptionCommandSender commandSender,
      final KeyGenerator keyGenerator,
      final Writers writers,
      final ProcessState processState,
      final EventTriggerBehavior eventTriggerBehavior,
      final BpmnStateBehavior stateBehavior) {
    this.messageState = messageState;
    this.subscriptionState = subscriptionState;
    this.startEventSubscriptionState = startEventSubscriptionState;
    currentPartitionId = partitionId;
    this.commandSender = commandSender;
    this.keyGenerator = keyGenerator;
    stateWriter = writers.state();
    responseWriter = writers.response();
    rejectionWriter = writers.rejection();
    sideEffectWriter = writers.sideEffect();
    eventHandle =
        new EventHandle(
            keyGenerator,
            eventScopeInstanceState,
            writers,
            processState,
            eventTriggerBehavior,
            stateBehavior);
  }

  @Override
  public void processRecord(final TypedRecord<MessageRecord> command) {
    messageRecord = command.getValue();

    correlatingSubscriptions.clear();

    if (messageRecord.hasMessageId()
        && messageState.exist(
            messageRecord.getNameBuffer(),
            messageRecord.getCorrelationKeyBuffer(),
            messageRecord.getMessageIdBuffer())) {
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

    correlateToSubscriptions(messageKey, messageRecord);
    correlateToMessageStartEvents(messageRecord);

    sendCorrelateCommand();

    if (messageRecord.getTimeToLive() <= 0L) {
      // avoid that the message can be correlated again by writing the EXPIRED event as a follow-up
      stateWriter.appendFollowUpEvent(messageKey, MessageIntent.EXPIRED, messageRecord);
    }
  }

  private void correlateToSubscriptions(final long messageKey, final MessageRecord message) {
    subscriptionState.visitSubscriptions(
        message.getNameBuffer(),
        message.getCorrelationKeyBuffer(),
        subscription -> {

          // correlate the message only once per process
          if (!subscription.isCorrelating()
              && !correlatingSubscriptions.contains(
                  subscription.getRecord().getBpmnProcessIdBuffer())) {

            final var correlatingSubscription =
                subscription
                    .getRecord()
                    .setMessageKey(messageKey)
                    .setVariables(message.getVariablesBuffer());

            stateWriter.appendFollowUpEvent(
                subscription.getKey(),
                MessageSubscriptionIntent.CORRELATING,
                correlatingSubscription);

            correlatingSubscriptions.add(correlatingSubscription);
          }

          return true;
        });
  }

  private void correlateToMessageStartEvents(final MessageRecord messageRecord) {

    startEventSubscriptionState.visitSubscriptionsByMessageName(
        messageRecord.getNameBuffer(),
        subscription -> {
          final var subscriptionRecord = subscription.getRecord();
          final var bpmnProcessIdBuffer = subscriptionRecord.getBpmnProcessIdBuffer();
          final var correlationKeyBuffer = messageRecord.getCorrelationKeyBuffer();

          // create only one instance of a process per correlation key
          // - allow multiple instance if correlation key is empty
          if (!correlatingSubscriptions.contains(bpmnProcessIdBuffer)
              && (correlationKeyBuffer.capacity() == 0
                  || !messageState.existActiveProcessInstance(
                      bpmnProcessIdBuffer, correlationKeyBuffer))) {

            correlatingSubscriptions.add(subscriptionRecord);

            eventHandle.triggerMessageStartEvent(
                subscription.getKey(), subscriptionRecord, messageKey, messageRecord);
          }
        });
  }

  private boolean sendCorrelateCommand() {
    return correlatingSubscriptions.visitSubscriptions(
        subscription ->
            commandSender.correlateProcessMessageSubscription(
                subscription.getProcessInstanceKey(),
                subscription.getElementInstanceKey(),
                subscription.getBpmnProcessId(),
                messageRecord.getNameBuffer(),
                messageKey,
                messageRecord.getVariablesBuffer(),
                messageRecord.getCorrelationKeyBuffer()));
  }
}
