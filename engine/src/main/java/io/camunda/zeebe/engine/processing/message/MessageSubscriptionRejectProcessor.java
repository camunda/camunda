/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.message;

import io.camunda.zeebe.engine.api.TypedRecord;
import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.MessageState;
import io.camunda.zeebe.engine.state.immutable.MessageSubscriptionState;
import io.camunda.zeebe.engine.state.message.StoredMessage;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;

public final class MessageSubscriptionRejectProcessor
    implements TypedRecordProcessor<MessageSubscriptionRecord> {

  private final MessageState messageState;
  private final MessageSubscriptionState subscriptionState;
  private final SubscriptionCommandSender commandSender;
  private final StateWriter stateWriter;

  public MessageSubscriptionRejectProcessor(
      final MessageState messageState,
      final MessageSubscriptionState subscriptionState,
      final SubscriptionCommandSender commandSender,
      final Writers writers) {
    this.messageState = messageState;
    this.subscriptionState = subscriptionState;
    this.commandSender = commandSender;
    stateWriter = writers.state();
  }

  @Override
  public void processRecord(final TypedRecord<MessageSubscriptionRecord> record) {

    final MessageSubscriptionRecord subscriptionRecord = record.getValue();
    stateWriter.appendFollowUpEvent(
        record.getKey(), MessageSubscriptionIntent.REJECTED, subscriptionRecord);

    findSubscriptionToCorrelate(subscriptionRecord);
  }

  private void findSubscriptionToCorrelate(final MessageSubscriptionRecord subscriptionRecord) {

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
            sendCorrelateCommand(correlatingSubscription);
          }
          return !canBeCorrelated;
        });
  }

  private void sendCorrelateCommand(final MessageSubscriptionRecord subscription) {
    commandSender.correlateProcessMessageSubscription(
        subscription.getProcessInstanceKey(),
        subscription.getElementInstanceKey(),
        subscription.getBpmnProcessIdBuffer(),
        subscription.getMessageNameBuffer(),
        subscription.getMessageKey(),
        subscription.getVariablesBuffer(),
        subscription.getCorrelationKeyBuffer());
  }
}
