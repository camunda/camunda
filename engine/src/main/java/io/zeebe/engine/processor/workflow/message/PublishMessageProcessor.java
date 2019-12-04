/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.message;

import static io.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.zeebe.engine.processor.KeyGenerator;
import io.zeebe.engine.processor.SideEffectProducer;
import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.engine.processor.TypedRecordProcessor;
import io.zeebe.engine.processor.TypedResponseWriter;
import io.zeebe.engine.processor.TypedStreamWriter;
import io.zeebe.engine.processor.workflow.message.command.SubscriptionCommandSender;
import io.zeebe.engine.state.instance.EventScopeInstanceState;
import io.zeebe.engine.state.message.Message;
import io.zeebe.engine.state.message.MessageStartEventSubscriptionState;
import io.zeebe.engine.state.message.MessageState;
import io.zeebe.engine.state.message.MessageSubscriptionState;
import io.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.zeebe.protocol.impl.record.value.message.MessageStartEventSubscriptionRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.MessageIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import io.zeebe.util.sched.clock.ActorClock;
import java.util.function.Consumer;

public class PublishMessageProcessor implements TypedRecordProcessor<MessageRecord> {

  private static final String ALREADY_PUBLISHED_MESSAGE =
      "Expected to publish a new message with id '%s', but a message with that id was already published";

  private final MessageState messageState;
  private final MessageSubscriptionState subscriptionState;
  private final MessageStartEventSubscriptionState startEventSubscriptionState;
  private final SubscriptionCommandSender commandSender;
  private final KeyGenerator keyGenerator;
  private final EventScopeInstanceState scopeEventInstanceState;

  private final Subscriptions correlatingSubscriptions = new Subscriptions();
  private final WorkflowInstanceRecord startEventRecord =
      new WorkflowInstanceRecord().setBpmnElementType(BpmnElementType.START_EVENT);

  private TypedResponseWriter responseWriter;
  private MessageRecord messageRecord;
  private long messageKey;

  public PublishMessageProcessor(
      final MessageState messageState,
      final MessageSubscriptionState subscriptionState,
      final MessageStartEventSubscriptionState startEventSubscriptionState,
      final EventScopeInstanceState scopeEventInstanceState,
      final SubscriptionCommandSender commandSender,
      final KeyGenerator keyGenerator) {
    this.messageState = messageState;
    this.subscriptionState = subscriptionState;
    this.startEventSubscriptionState = startEventSubscriptionState;
    this.scopeEventInstanceState = scopeEventInstanceState;
    this.commandSender = commandSender;
    this.keyGenerator = keyGenerator;
  }

  @Override
  public void processRecord(
      final TypedRecord<MessageRecord> command,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter,
      final Consumer<SideEffectProducer> sideEffect) {
    this.responseWriter = responseWriter;
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

      streamWriter.appendRejection(command, RejectionType.ALREADY_EXISTS, rejectionReason);
      responseWriter.writeRejectionOnCommand(
          command, RejectionType.ALREADY_EXISTS, rejectionReason);
    } else {
      handleNewMessage(command, responseWriter, streamWriter, sideEffect);
    }
  }

  private void handleNewMessage(
      final TypedRecord<MessageRecord> command,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter,
      final Consumer<SideEffectProducer> sideEffect) {
    messageKey = keyGenerator.nextKey();

    streamWriter.appendNewEvent(messageKey, MessageIntent.PUBLISHED, command.getValue());
    responseWriter.writeEventOnCommand(
        messageKey, MessageIntent.PUBLISHED, command.getValue(), command);

    correlateToSubscriptions(messageKey, messageRecord);
    correlateToMessageStartEvents(messageRecord, streamWriter);

    sideEffect.accept(this::sendCorrelateCommand);

    if (messageRecord.getTimeToLive() > 0L) {
      final Message message = newMessage(messageKey, messageRecord);
      messageState.put(message);

      // avoid correlating this message to the workflow again
      correlatingSubscriptions.visitBpmnProcessIds(
          bpmnProcessId -> messageState.putMessageCorrelation(messageKey, bpmnProcessId));

    } else {
      // don't need to add the message to the store - it can not be correlated afterwards
      streamWriter.appendFollowUpEvent(messageKey, MessageIntent.DELETED, messageRecord);
    }
  }

  private void correlateToSubscriptions(final long messageKey, final MessageRecord message) {
    subscriptionState.visitSubscriptions(
        message.getNameBuffer(),
        message.getCorrelationKeyBuffer(),
        subscription -> {

          // correlate the message only once per workflow
          if (!subscription.isCorrelating()
              && !correlatingSubscriptions.contains(subscription.getBpmnProcessId())) {

            correlatingSubscriptions.add(subscription);

            subscriptionState.updateToCorrelatingState(
                subscription,
                message.getVariablesBuffer(),
                ActorClock.currentTimeMillis(),
                messageKey);
          }

          return true;
        });
  }

  private void correlateToMessageStartEvents(
      final MessageRecord messageRecord, final TypedStreamWriter streamWriter) {

    startEventSubscriptionState.visitSubscriptionsByMessageName(
        messageRecord.getNameBuffer(),
        subscription -> {
          final var bpmnProcessIdBuffer = subscription.getBpmnProcessIdBuffer();
          final var correlationKeyBuffer = messageRecord.getCorrelationKeyBuffer();

          // create only one instance of a workflow per correlation key
          // - allow multiple instance if correlation key is empty
          if (!correlatingSubscriptions.contains(bpmnProcessIdBuffer)
              && (correlationKeyBuffer.capacity() == 0
                  || !messageState.existActiveWorkflowInstance(
                      bpmnProcessIdBuffer, correlationKeyBuffer))) {

            correlatingSubscriptions.add(subscription);

            createEventTrigger(subscription);
            final long workflowInstanceKey = createNewWorkflowInstance(streamWriter, subscription);

            if (correlationKeyBuffer.capacity() > 0) {
              // lock the workflow for this correlation key
              // - other messages with same correlation key are not correlated to this workflow
              // until the created instance is ended
              messageState.putActiveWorkflowInstance(bpmnProcessIdBuffer, correlationKeyBuffer);
              messageState.putWorkflowInstanceCorrelationKey(
                  workflowInstanceKey, correlationKeyBuffer);
            }
          }
        });
  }

  private void createEventTrigger(final MessageStartEventSubscriptionRecord subscription) {

    final boolean success =
        scopeEventInstanceState.triggerEvent(
            subscription.getWorkflowKey(),
            messageKey,
            subscription.getStartEventIdBuffer(),
            messageRecord.getVariablesBuffer());

    if (!success) {
      throw new IllegalStateException(
          String.format(
              "Expected the event trigger for be created of the workflow with key '%d' but failed.",
              subscription.getWorkflowKey()));
    }
  }

  private long createNewWorkflowInstance(
      final TypedStreamWriter streamWriter,
      final MessageStartEventSubscriptionRecord subscription) {

    final var workflowInstanceKey = keyGenerator.nextKey();
    final var eventKey = keyGenerator.nextKey();

    streamWriter.appendNewEvent(
        eventKey,
        WorkflowInstanceIntent.EVENT_OCCURRED,
        startEventRecord
            .setWorkflowKey(subscription.getWorkflowKey())
            .setWorkflowInstanceKey(workflowInstanceKey)
            .setElementId(subscription.getStartEventId()));

    return workflowInstanceKey;
  }

  private boolean sendCorrelateCommand() {

    final var success =
        correlatingSubscriptions.visitSubscriptions(
            subscription ->
                commandSender.correlateWorkflowInstanceSubscription(
                    subscription.getWorkflowInstanceKey(),
                    subscription.getElementInstanceKey(),
                    subscription.getBpmnProcessId(),
                    messageRecord.getNameBuffer(),
                    messageKey,
                    messageRecord.getVariablesBuffer()));

    return success ? responseWriter.flush() : false;
  }

  private Message newMessage(final long messageKey, final MessageRecord messageRecord) {
    return new Message(
        messageKey,
        messageRecord.getNameBuffer(),
        messageRecord.getCorrelationKeyBuffer(),
        messageRecord.getVariablesBuffer(),
        messageRecord.getMessageIdBuffer(),
        messageRecord.getTimeToLive(),
        messageRecord.getTimeToLive() + ActorClock.currentTimeMillis());
  }
}
