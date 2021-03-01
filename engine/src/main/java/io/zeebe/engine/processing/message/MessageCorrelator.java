/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.message;

import io.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.zeebe.engine.processing.streamprocessor.sideeffect.SideEffectProducer;
import io.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.zeebe.engine.state.immutable.MessageState;
import io.zeebe.engine.state.message.StoredMessage;
import io.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.zeebe.util.sched.clock.ActorClock;
import java.util.function.Consumer;
import org.agrona.collections.MutableBoolean;

public final class MessageCorrelator {

  private final MessageState messageState;
  private final SubscriptionCommandSender commandSender;
  private final StateWriter stateWriter;

  private Consumer<SideEffectProducer> sideEffect;
  private MessageSubscriptionRecord subscriptionRecord;

  public MessageCorrelator(
      final MessageState messageState,
      final SubscriptionCommandSender commandSender,
      final StateWriter stateWriter) {
    this.messageState = messageState;
    this.commandSender = commandSender;
    this.stateWriter = stateWriter;
  }

  public boolean correlateNextMessage(
      final MessageSubscriptionRecord subscriptionRecord,
      final Consumer<SideEffectProducer> sideEffect) {
    this.subscriptionRecord = subscriptionRecord;
    this.sideEffect = sideEffect;

    final var isMessageCorrelated = new MutableBoolean(false);

    messageState.visitMessages(
        subscriptionRecord.getMessageNameBuffer(),
        subscriptionRecord.getCorrelationKeyBuffer(),
        storedMessage -> {
          // correlate the first message which is not correlated to the workflow instance yet
          final var isCorrelated = correlateMessage(storedMessage);
          isMessageCorrelated.set(isCorrelated);
          return !isCorrelated;
        });

    return isMessageCorrelated.get();
  }

  private boolean correlateMessage(final StoredMessage storedMessage) {
    final long messageKey = storedMessage.getMessageKey();
    final var message = storedMessage.getMessage();

    final boolean correlateMessage =
        message.getDeadline() > ActorClock.currentTimeMillis()
            && !messageState.existMessageCorrelation(
                messageKey, subscriptionRecord.getBpmnProcessIdBuffer());

    if (correlateMessage) {
      subscriptionRecord.setMessageKey(messageKey).setVariables(message.getVariablesBuffer());

      stateWriter.appendFollowUpEvent(
          -1, MessageSubscriptionIntent.CORRELATING, subscriptionRecord);

      sideEffect.accept(this::sendCorrelateCommand);
    }

    return correlateMessage;
  }

  private boolean sendCorrelateCommand() {
    return commandSender.correlateWorkflowInstanceSubscription(
        subscriptionRecord.getWorkflowInstanceKey(),
        subscriptionRecord.getElementInstanceKey(),
        subscriptionRecord.getBpmnProcessIdBuffer(),
        subscriptionRecord.getMessageNameBuffer(),
        subscriptionRecord.getMessageKey(),
        subscriptionRecord.getVariablesBuffer(),
        subscriptionRecord.getCorrelationKeyBuffer());
  }
}
