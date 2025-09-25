/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.common.processing.message;

import io.camunda.zeebe.engine.common.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.common.processing.streamprocessor.writers.SideEffectWriter;
import io.camunda.zeebe.engine.common.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.common.state.immutable.MessageState;
import io.camunda.zeebe.engine.common.state.message.StoredMessage;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import java.time.InstantSource;
import org.agrona.collections.MutableBoolean;

public final class MessageCorrelator {

  private final MessageState messageState;
  private final SubscriptionCommandSender commandSender;
  private final StateWriter stateWriter;
  private final SideEffectWriter sideEffectWriter;
  private final int currentPartitionId;
  private final InstantSource clock;

  public MessageCorrelator(
      final int currentPartitionId,
      final MessageState messageState,
      final SubscriptionCommandSender commandSender,
      final StateWriter stateWriter,
      final SideEffectWriter sideEffectWriter,
      final InstantSource clock) {
    this.currentPartitionId = currentPartitionId;
    this.messageState = messageState;
    this.commandSender = commandSender;
    this.stateWriter = stateWriter;
    this.sideEffectWriter = sideEffectWriter;
    this.clock = clock;
  }

  public boolean correlateNextMessage(
      final long subscriptionKey, final MessageSubscriptionRecord subscriptionRecord) {

    final var isMessageCorrelated = new MutableBoolean(false);

    messageState.visitMessages(
        subscriptionRecord.getTenantId(),
        subscriptionRecord.getMessageNameBuffer(),
        subscriptionRecord.getCorrelationKeyBuffer(),
        storedMessage -> {
          // correlate the first message which is not correlated to the process instance yet
          final var isCorrelated =
              correlateMessage(subscriptionKey, subscriptionRecord, storedMessage);
          isMessageCorrelated.set(isCorrelated);
          return !isCorrelated;
        });

    return isMessageCorrelated.get();
  }

  private boolean correlateMessage(
      final long subscriptionKey,
      final MessageSubscriptionRecord subscriptionRecord,
      final StoredMessage storedMessage) {
    final long messageKey = storedMessage.getMessageKey();
    final var message = storedMessage.getMessage();

    final boolean correlateMessage =
        message.getDeadline() > clock.millis()
            && !messageState.existMessageCorrelation(
                messageKey, subscriptionRecord.getBpmnProcessIdBuffer());

    if (correlateMessage) {
      subscriptionRecord.setMessageKey(messageKey).setVariables(message.getVariablesBuffer());

      stateWriter.appendFollowUpEvent(
          subscriptionKey, MessageSubscriptionIntent.CORRELATING, subscriptionRecord);

      sendCorrelateCommand(subscriptionRecord);
    }

    return correlateMessage;
  }

  private boolean sendCorrelateCommand(final MessageSubscriptionRecord subscriptionRecord) {
    return commandSender.correlateProcessMessageSubscription(
        subscriptionRecord.getProcessInstanceKey(),
        subscriptionRecord.getElementInstanceKey(),
        subscriptionRecord.getBpmnProcessIdBuffer(),
        subscriptionRecord.getMessageNameBuffer(),
        subscriptionRecord.getMessageKey(),
        subscriptionRecord.getVariablesBuffer(),
        subscriptionRecord.getCorrelationKeyBuffer(),
        subscriptionRecord.getTenantId());
  }
}
