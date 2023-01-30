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
import io.camunda.zeebe.engine.processing.streamprocessor.writers.SideEffectWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.MessageSubscriptionState;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.util.buffer.BufferUtil;

public final class MessageSubscriptionDeleteProcessor
    implements TypedRecordProcessor<MessageSubscriptionRecord> {

  private static final String NO_SUBSCRIPTION_FOUND_MESSAGE =
      "Expected to close message subscription for element with key '%d' and message name '%s', "
          + "but no such message subscription exists";

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

    if (messageSubscription != null) {
      stateWriter.appendFollowUpEvent(
          messageSubscription.getKey(),
          MessageSubscriptionIntent.DELETED,
          messageSubscription.getRecord());

    } else {
      rejectCommand(record);
    }

    sideEffectWriter.appendSideEffect(this::sendAcknowledgeCommand);
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

  private boolean sendAcknowledgeCommand() {
    return commandSender.closeProcessMessageSubscription(
        subscriptionRecord.getProcessInstanceKey(),
        subscriptionRecord.getElementInstanceKey(),
        subscriptionRecord.getMessageNameBuffer());
  }
}
