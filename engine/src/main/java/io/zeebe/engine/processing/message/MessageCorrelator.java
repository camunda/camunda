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
import io.zeebe.engine.state.message.MessageSubscription;
import io.zeebe.engine.state.message.StoredMessage;
import io.zeebe.engine.state.mutable.MutableMessageState;
import io.zeebe.engine.state.mutable.MutableMessageSubscriptionState;
import io.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.zeebe.util.sched.clock.ActorClock;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class MessageCorrelator {

  private final DirectBuffer messageVariables = new UnsafeBuffer();
  private final MutableMessageState messageState;
  private final MutableMessageSubscriptionState subscriptionState;
  private final SubscriptionCommandSender commandSender;
  private Consumer<SideEffectProducer> sideEffect;
  private MessageSubscriptionRecord subscriptionRecord;
  private MessageSubscription subscription;
  private long messageKey;

  public MessageCorrelator(
      final MutableMessageState messageState,
      final MutableMessageSubscriptionState subscriptionState,
      final SubscriptionCommandSender commandSender) {
    this.messageState = messageState;
    this.subscriptionState = subscriptionState;
    this.commandSender = commandSender;
  }

  public void correlateNextMessage(
      final MessageSubscription subscription,
      final MessageSubscriptionRecord subscriptionRecord,
      final Consumer<SideEffectProducer> sideEffect) {
    this.subscription = subscription;
    this.subscriptionRecord = subscriptionRecord;
    this.sideEffect = sideEffect;

    messageState.visitMessages(
        subscription.getMessageName(), subscription.getCorrelationKey(), this::correlateMessage);
  }

  private boolean correlateMessage(final StoredMessage storedMessage) {
    // correlate the first message which is not correlated to the workflow instance yet
    messageKey = storedMessage.getMessageKey();
    final var message = storedMessage.getMessage();

    final boolean correlateMessage =
        message.getDeadline() > ActorClock.currentTimeMillis()
            && !messageState.existMessageCorrelation(
                messageKey, subscriptionRecord.getBpmnProcessIdBuffer());

    if (correlateMessage) {
      subscriptionState.updateToCorrelatingState(
          subscription, message.getVariablesBuffer(), ActorClock.currentTimeMillis(), messageKey);

      // send the correlate instead of acknowledge command
      messageVariables.wrap(message.getVariablesBuffer());
      sideEffect.accept(this::sendCorrelateCommand);

      messageState.putMessageCorrelation(messageKey, subscriptionRecord.getBpmnProcessIdBuffer());
    }

    return !correlateMessage;
  }

  private boolean sendCorrelateCommand() {
    return commandSender.correlateWorkflowInstanceSubscription(
        subscriptionRecord.getWorkflowInstanceKey(),
        subscriptionRecord.getElementInstanceKey(),
        subscriptionRecord.getBpmnProcessIdBuffer(),
        subscriptionRecord.getMessageNameBuffer(),
        messageKey,
        messageVariables,
        subscription.getCorrelationKey());
  }
}
