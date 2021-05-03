/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.message;

import io.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.zeebe.engine.processing.streamprocessor.sideeffect.SideEffectProducer;
import io.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedStreamWriter;
import io.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.zeebe.engine.state.immutable.MessageState;
import io.zeebe.engine.state.immutable.MessageSubscriptionState;
import io.zeebe.engine.state.message.StoredMessage;
import io.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import java.util.function.Consumer;

public final class MessageSubscriptionRejectProcessor
    implements TypedRecordProcessor<MessageSubscriptionRecord> {

  private final MessageState messageState;
  private final MessageSubscriptionState subscriptionState;
  private final SubscriptionCommandSender commandSender;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;

  public MessageSubscriptionRejectProcessor(
      final MessageState messageState,
      final MessageSubscriptionState subscriptionState,
      final SubscriptionCommandSender commandSender,
      final Writers writers) {
    this.messageState = messageState;
    this.subscriptionState = subscriptionState;
    this.commandSender = commandSender;
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
  }

  @Override
  public void processRecord(
      final TypedRecord<MessageSubscriptionRecord> record,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter,
      final Consumer<SideEffectProducer> sideEffect) {

    final MessageSubscriptionRecord subscriptionRecord = record.getValue();

    if (!messageState.existMessageCorrelation(
        subscriptionRecord.getMessageKey(), subscriptionRecord.getBpmnProcessIdBuffer())) {

      rejectCommand(record);
      return;
    }

    stateWriter.appendFollowUpEvent(
        record.getKey(), MessageSubscriptionIntent.REJECTED, subscriptionRecord);

    findSubscriptionToCorrelate(sideEffect, subscriptionRecord);
  }

  private void findSubscriptionToCorrelate(
      final Consumer<SideEffectProducer> sideEffect,
      final MessageSubscriptionRecord subscriptionRecord) {

    final var messageKey = subscriptionRecord.getMessageKey();

    // the message TTL may expire after the previous correlation attempt
    final StoredMessage storedMessage = messageState.getMessage(messageKey);
    if (storedMessage == null) {
      return;
    }

    subscriptionState.visitSubscriptions(
        subscriptionRecord.getMessageNameBuffer(),
        subscriptionRecord.getCorrelationKeyBuffer(),
        subscription -> {
          final var correlatingSubscription = subscription.getRecord();

          final var canBeCorrelated =
              correlatingSubscription
                      .getBpmnProcessIdBuffer()
                      .equals(subscriptionRecord.getBpmnProcessIdBuffer())
                  && !subscription.isCorrelating();

          if (canBeCorrelated) {
            correlatingSubscription
                .setMessageKey(messageKey)
                .setVariables(storedMessage.getMessage().getVariablesBuffer());

            stateWriter.appendFollowUpEvent(
                subscription.getKey(),
                MessageSubscriptionIntent.CORRELATING,
                correlatingSubscription);

            sideEffect.accept(() -> sendCorrelateCommand(correlatingSubscription));
          }
          return !canBeCorrelated;
        });
  }

  private boolean sendCorrelateCommand(final MessageSubscriptionRecord subscription) {
    return commandSender.correlateProcessMessageSubscription(
        subscription.getProcessInstanceKey(),
        subscription.getElementInstanceKey(),
        subscription.getBpmnProcessIdBuffer(),
        subscription.getMessageNameBuffer(),
        subscription.getMessageKey(),
        subscription.getVariablesBuffer(),
        subscription.getCorrelationKeyBuffer());
  }

  private void rejectCommand(final TypedRecord<MessageSubscriptionRecord> record) {
    final var subscription = record.getValue();
    final var reason =
        String.format(
            "Expected message '%d' to be correlated for process with BPMN process id '%s' but no correlation was found",
            subscription.getMessageKey(), subscription.getBpmnProcessId());

    rejectionWriter.appendRejection(record, RejectionType.INVALID_STATE, reason);
  }
}
