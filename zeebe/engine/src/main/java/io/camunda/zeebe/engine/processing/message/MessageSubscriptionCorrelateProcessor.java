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
import io.camunda.zeebe.engine.state.immutable.MessageCorrelationState;
import io.camunda.zeebe.engine.state.immutable.MessageState;
import io.camunda.zeebe.engine.state.immutable.MessageSubscriptionState;
import io.camunda.zeebe.engine.state.message.MessageSubscription;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageCorrelationRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.MessageCorrelationIntent;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.InstantSource;

@ExcludeAuthorizationCheck
public final class MessageSubscriptionCorrelateProcessor
    implements TypedRecordProcessor<MessageSubscriptionRecord> {

  private static final String NO_SUBSCRIPTION_FOUND_MESSAGE =
      "Expected to correlate subscription for element with key '%d' and message name '%s', "
          + "but no such message subscription exists";

  private static final String SUBSCRIPTION_ACKNOWLEDGE_ERROR_MESSAGE =
      "Expected to acknowledge correlating message with key '%d' to subscription with key '%d'";
  private static final String SUBSCRIPTION_ALREADY_CORRELATING_AGAIN_MESSAGE =
      "but the subscription is already correlating to another message with key '%d'";
  private static final String SUBSCRIPTION_ALREADY_CORRELATED_MESSAGE =
      "but the subscription has already been correlated'";

  private final MessageSubscriptionState subscriptionState;
  private final MessageCorrelationState messageCorrelationState;
  private final MessageCorrelator messageCorrelator;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;

  public MessageSubscriptionCorrelateProcessor(
      final int partitionId,
      final MessageState messageState,
      final MessageCorrelationState messageCorrelationState,
      final MessageSubscriptionState subscriptionState,
      final SubscriptionCommandSender commandSender,
      final Writers writers,
      final InstantSource clock) {
    this.subscriptionState = subscriptionState;
    this.messageCorrelationState = messageCorrelationState;
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    messageCorrelator =
        new MessageCorrelator(
            partitionId, messageState, commandSender, stateWriter, writers.sideEffect(), clock);
  }

  @Override
  public void processRecord(final TypedRecord<MessageSubscriptionRecord> record) {

    final MessageSubscriptionRecord command = record.getValue();
    final MessageSubscription subscription =
        subscriptionState.get(command.getElementInstanceKey(), command.getMessageNameBuffer());

    if (subscription == null) {
      final var reason = formatNoSubscriptionFoundReason(record);
      rejectionWriter.appendRejection(record, RejectionType.NOT_FOUND, reason);
      return;

    } else if (subscription.getRecord().getMessageKey() != record.getValue().getMessageKey()) {
      // This concerns the acknowledgement of a retried correlate process message subscription
      // command. The message subscription was already marked as correlated for this message, and
      // another message has started correlating. There's no need to update the state.
      final var reason = formatSubscriptionAlreadyCorrelatingAgainReason(record, subscription);
      rejectionWriter.appendRejection(record, RejectionType.INVALID_STATE, reason);
      return;

    } else if (!subscription.isCorrelating()) {
      // This concerns the acknowledgement of a retried correlate process message subscription
      // command. The message subscription was already marked as correlated. No need to update the
      // state.
      final var reason = formatSubscriptionAlreadyCorrelatedReason(record, subscription);
      rejectionWriter.appendRejection(record, RejectionType.INVALID_STATE, reason);
      return;
    }

    final var messageSubscription = subscription.getRecord();
    stateWriter.appendFollowUpEvent(
        subscription.getKey(), MessageSubscriptionIntent.CORRELATED, messageSubscription);
    writeCorrelationResponse(record, messageSubscription);

    if (!messageSubscription.isInterrupting()) {
      messageCorrelator.correlateNextMessage(subscription.getKey(), messageSubscription);
    }
  }

  private static String formatNoSubscriptionFoundReason(
      final TypedRecord<MessageSubscriptionRecord> record) {
    return String.format(
        NO_SUBSCRIPTION_FOUND_MESSAGE,
        record.getValue().getElementInstanceKey(),
        BufferUtil.bufferAsString(record.getValue().getMessageNameBuffer()));
  }

  private static String formatSubscriptionAlreadyCorrelatingAgainReason(
      final TypedRecord<MessageSubscriptionRecord> record, final MessageSubscription subscription) {
    return "%s %s"
        .formatted(
            SUBSCRIPTION_ACKNOWLEDGE_ERROR_MESSAGE.formatted(
                record.getValue().getMessageKey(), subscription.getKey()),
            SUBSCRIPTION_ALREADY_CORRELATING_AGAIN_MESSAGE.formatted(
                subscription.getRecord().getMessageKey()));
  }

  private static String formatSubscriptionAlreadyCorrelatedReason(
      final TypedRecord<MessageSubscriptionRecord> record, final MessageSubscription subscription) {
    return "%s %s"
        .formatted(
            SUBSCRIPTION_ACKNOWLEDGE_ERROR_MESSAGE.formatted(
                record.getValue().getMessageKey(), subscription.getKey()),
            SUBSCRIPTION_ALREADY_CORRELATED_MESSAGE);
  }

  private void writeCorrelationResponse(
      final TypedRecord<MessageSubscriptionRecord> record,
      final MessageSubscriptionRecord messageSubscription) {
    final var messageKey = messageSubscription.getMessageKey();
    if (messageCorrelationState.existsRequestDataForMessageKey(messageKey)) {
      final var requestData = messageCorrelationState.getRequestData(messageKey);
      final var messageCorrelationRecord =
          new MessageCorrelationRecord()
              .setName(messageSubscription.getMessageName())
              .setCorrelationKey(messageSubscription.getCorrelationKey())
              .setVariables(messageSubscription.getVariablesBuffer())
              .setTenantId(messageSubscription.getTenantId())
              .setMessageKey(messageKey)
              .setProcessInstanceKey(messageSubscription.getProcessInstanceKey())
              .setProcessDefinitionKey(messageSubscription.getProcessDefinitionKey());

      stateWriter.appendFollowUpEvent(
          messageKey, MessageCorrelationIntent.CORRELATED, messageCorrelationRecord);
      responseWriter.writeResponse(
          record.getValue().getMessageKey(),
          MessageCorrelationIntent.CORRELATED,
          messageCorrelationRecord,
          ValueType.MESSAGE_CORRELATION,
          requestData.getRequestId(),
          requestData.getRequestStreamId());
    }
  }
}
