/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.MessageState;
import io.camunda.zeebe.engine.state.immutable.MessageSubscriptionState;
import io.camunda.zeebe.engine.state.message.MessageSubscription;
import io.camunda.zeebe.engine.state.message.StoredMessage;
import io.camunda.zeebe.engine.state.mutable.MutableMessageCorrelationState;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageCorrelationRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.MessageCorrelationIntent;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.concurrent.atomic.AtomicBoolean;

@ExcludeAuthorizationCheck
public final class MessageSubscriptionRejectProcessor
    implements TypedRecordProcessor<MessageSubscriptionRecord> {

  private static final String SUBSCRIPTION_NOT_FOUND =
      "Expected to find subscription for message with name '%s' and correlation key '%s', but none was found.";
  private static final String STALE_REJECT_MESSAGE =
      "Expected to reject message subscription for element with key '%d' and message name '%s' with "
          + "subscription key '%d', but the current subscription has key '%d'; the reject command is "
          + "stale and is ignored";

  private final MessageState messageState;
  private final MessageSubscriptionState subscriptionState;
  private final MutableMessageCorrelationState messageCorrelationState;
  private final SubscriptionCommandSender commandSender;
  private final StateWriter stateWriter;
  private final TypedResponseWriter responseWriter;
  private final TypedRejectionWriter rejectionWriter;

  public MessageSubscriptionRejectProcessor(
      final MessageState messageState,
      final MessageSubscriptionState subscriptionState,
      final MutableMessageCorrelationState messageCorrelationState,
      final SubscriptionCommandSender commandSender,
      final Writers writers) {
    this.messageState = messageState;
    this.subscriptionState = subscriptionState;
    this.messageCorrelationState = messageCorrelationState;
    this.commandSender = commandSender;
    stateWriter = writers.state();
    responseWriter = writers.response();
    rejectionWriter = writers.rejection();
  }

  @Override
  public void processRecord(final TypedRecord<MessageSubscriptionRecord> record) {

    final MessageSubscriptionRecord subscriptionRecord = record.getValue();

    stateWriter.appendFollowUpEvent(
        record.getKey(), MessageSubscriptionIntent.REJECTED, subscriptionRecord);

    // The applier already protects a live replacement subscription from deletion when this
    // reject is stale, so rerouting unconditionally here is safe. It's also necessary: if a
    // replacement subscription was created before this lock was released, it already missed
    // its own one-shot chance to pick up the message and needs this to try again.
    if (!findSubscriptionToCorrelate(subscriptionRecord)) {
      writeNotCorrelatedResponse(record);
    }
  }

  private boolean findSubscriptionToCorrelate(final MessageSubscriptionRecord subscriptionRecord) {

    final var messageKey = subscriptionRecord.getMessageKey();

    // the message TTL may expire after the previous correlation attempt
    final StoredMessage storedMessage = messageState.getMessage(messageKey);
    if (storedMessage == null) {
      return false;
    }

    final var foundSubscription = new AtomicBoolean(false);
    subscriptionState.visitSubscriptions(
        subscriptionRecord.getTenantId(),
        subscriptionRecord.getMessageNameBuffer(),
        subscriptionRecord.getCorrelationKeyBuffer(),
        subscription -> {
          final var correlatingSubscription = subscription.getRecord();

          final var canBeCorrelated =
              correlatingSubscription
                      .getBpmnProcessIdBuffer()
                      .equals(subscriptionRecord.getBpmnProcessIdBuffer())
                  && !subscription.isCorrelating()
                  && !hasAlreadyBeenCorrelated(subscriptionRecord, subscription);

          if (canBeCorrelated) {
            correlatingSubscription
                .setMessageKey(messageKey)
                .setVariables(storedMessage.getMessage().getVariablesBuffer());

            stateWriter.appendFollowUpEvent(
                subscription.getKey(),
                MessageSubscriptionIntent.CORRELATING,
                correlatingSubscription);
            sendCorrelateCommand(correlatingSubscription);
            foundSubscription.set(true);
          }
          return !canBeCorrelated;
        });

    return foundSubscription.get();
  }

  private boolean hasAlreadyBeenCorrelated(
      final MessageSubscriptionRecord subscriptionRecord, final MessageSubscription subscription) {
    // we only want to reroute a subscription once per message (by key)
    final var messageKey = subscriptionRecord.getMessageKey();

    // Exact match only: unlike a subscription's own retried commands (which are strictly ordered,
    // see ProcessMessageSubscriptionCorrelateProcessor#hasAlreadyBeenCorrelated), a candidate found
    // here may belong to a different generation whose own CREATE-time scan skipped this exact
    // message (still locked by the stale subscription) and correlated a later one instead. Its
    // last-correlated key being numerically ahead of this one does not mean it ever saw this
    // message.
    final var lastCorrelatedMessageKey = subscription.getRecord().getMessageKey();
    return messageKey == lastCorrelatedMessageKey;
  }

  private void sendCorrelateCommand(final MessageSubscriptionRecord subscription) {
    commandSender.correlateProcessMessageSubscription(
        subscription.getProcessInstanceKey(),
        subscription.getElementInstanceKey(),
        subscription.getProcessDefinitionKey(),
        subscription.getBpmnProcessIdBuffer(),
        subscription.getMessageNameBuffer(),
        subscription.getMessageKey(),
        subscription.getVariablesBuffer(),
        subscription.getCorrelationKeyBuffer(),
        subscription.getTenantId(),
        subscription.getSubscriptionKey());
  }

  private void writeNotCorrelatedResponse(final TypedRecord<MessageSubscriptionRecord> record) {
    final var messageSubscription = record.getValue();
    final var messageKey = messageSubscription.getMessageKey();

    if (messageCorrelationState.existsRequestDataForMessageKey(messageKey)) {
      final var requestData = messageCorrelationState.getRequestData(messageKey);

      final var messageCorrelationRecord =
          new MessageCorrelationRecord()
              .setName(messageSubscription.getMessageName())
              .setCorrelationKey(messageSubscription.getCorrelationKey())
              .setVariables(messageSubscription.getVariablesBuffer())
              .setTenantId(messageSubscription.getTenantId())
              .setMessageKey(messageKey);

      stateWriter.appendFollowUpEvent(
          messageKey, MessageCorrelationIntent.NOT_CORRELATED, messageCorrelationRecord);
      responseWriter.writeRejectedResponseOnCommand(
          record,
          RejectionType.NOT_FOUND,
          SUBSCRIPTION_NOT_FOUND.formatted(
              messageSubscription.getMessageKey(), messageSubscription.getCorrelationKey()),
          requestData.getRequestId(),
          requestData.getRequestStreamId());
    }
  }
}
