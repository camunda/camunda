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
import io.camunda.zeebe.engine.processing.streamprocessor.writers.SideEffectWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.MessageSubscriptionState;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;

@ExcludeAuthorizationCheck
public final class MessageSubscriptionDeleteProcessor
    implements TypedRecordProcessor<MessageSubscriptionRecord> {

  private static final String NO_SUBSCRIPTION_FOUND_MESSAGE =
      "Expected to close message subscription for element with key '%d' and message name '%s', "
          + "but no such message subscription exists";
  private static final String STALE_DELETE_MESSAGE =
      "Expected to close message subscription for element with key '%d' and message name '%s' "
          + "with subscription key '%d', but the current subscription has key '%d'; "
          + "the delete command is stale and is ignored";

  private final MessageSubscriptionState subscriptionState;
  private final SubscriptionCommandSender commandSender;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final SideEffectWriter sideEffectWriter;

  private MessageSubscriptionRecord subscriptionRecord;

  public MessageSubscriptionDeleteProcessor(
      final MessageSubscriptionState subscriptionState,
      final SubscriptionCommandSender commandSender,
      final Writers writers) {
    this.subscriptionState = subscriptionState;
    this.commandSender = commandSender;
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    sideEffectWriter = writers.sideEffect();
  }

  @Override
  public void processRecord(final TypedRecord<MessageSubscriptionRecord> record) {
    subscriptionRecord = record.getValue();

    final var messageSubscription =
        subscriptionState.get(
            subscriptionRecord.getElementInstanceKey(), subscriptionRecord.getMessageNameBuffer());

    if (messageSubscription == null) {
      rejectCommand(record);
    } else {
      final long requestedKey = subscriptionRecord.getSubscriptionKey();
      final long storedKey = messageSubscription.getKey();
      if (requestedKey != -1L && storedKey != requestedKey) {
        // The delete quotes a specific subscription key, but the stored subscription has a
        // different key, meaning a resume already replaced it with a new subscription. Silently
        // ack so the PI side stops retrying; do NOT delete the live subscription.
        rejectStaleCommand(record, requestedKey, storedKey);
        sendAcknowledgeCommand();
        return;
      }
      stateWriter.appendFollowUpEvent(
          storedKey, MessageSubscriptionIntent.DELETED, messageSubscription.getRecord());
    }

    sendAcknowledgeCommand();
  }

  private void rejectCommand(final TypedRecord<MessageSubscriptionRecord> record) {
    final var subscription = record.getValue();
    final var reason =
        String.format(
            NO_SUBSCRIPTION_FOUND_MESSAGE,
            subscription.getElementInstanceKey(),
            BufferUtil.bufferAsString(subscription.getMessageNameBuffer()));

    rejectionWriter.appendRejection(record, RejectionType.NOT_FOUND, reason);
  }

  private void rejectStaleCommand(
      final TypedRecord<MessageSubscriptionRecord> record,
      final long requestedKey,
      final long storedKey) {
    final var subscription = record.getValue();
    final var reason =
        String.format(
            STALE_DELETE_MESSAGE,
            subscription.getElementInstanceKey(),
            BufferUtil.bufferAsString(subscription.getMessageNameBuffer()),
            requestedKey,
            storedKey);

    rejectionWriter.appendRejection(record, RejectionType.INVALID_STATE, reason);
  }

  private boolean sendAcknowledgeCommand() {
    return commandSender.closeProcessMessageSubscription(
        subscriptionRecord.getProcessInstanceKey(),
        subscriptionRecord.getElementInstanceKey(),
        subscriptionRecord.getProcessDefinitionKey(),
        subscriptionRecord.getMessageNameBuffer(),
        subscriptionRecord.getTenantId());
  }
}
