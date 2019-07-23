/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.message;

import io.zeebe.engine.processor.SideEffectProducer;
import io.zeebe.engine.processor.workflow.message.command.SubscriptionCommandSender;
import io.zeebe.engine.state.message.Message;
import io.zeebe.engine.state.message.MessageState;
import io.zeebe.engine.state.message.MessageSubscription;
import io.zeebe.engine.state.message.MessageSubscriptionState;
import io.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.zeebe.util.sched.clock.ActorClock;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class MessageCorrelator {

  private final DirectBuffer messageVariables = new UnsafeBuffer();
  private final MessageState messageState;
  private final MessageSubscriptionState subscriptionState;
  private final SubscriptionCommandSender commandSender;
  private Consumer<SideEffectProducer> sideEffect;
  private MessageSubscriptionRecord subscriptionRecord;
  private MessageSubscription subscription;
  private long messageKey;

  public MessageCorrelator(
      MessageState messageState,
      MessageSubscriptionState subscriptionState,
      SubscriptionCommandSender commandSender) {
    this.messageState = messageState;
    this.subscriptionState = subscriptionState;
    this.commandSender = commandSender;
  }

  public void correlateNextMessage(
      MessageSubscription subscription,
      MessageSubscriptionRecord subscriptionRecord,
      Consumer<SideEffectProducer> sideEffect) {
    this.subscription = subscription;
    this.subscriptionRecord = subscriptionRecord;
    this.sideEffect = sideEffect;

    messageState.visitMessages(
        subscription.getMessageName(), subscription.getCorrelationKey(), this::correlateMessage);
  }

  private boolean correlateMessage(final Message message) {
    // correlate the first message which is not correlated to the workflow instance yet
    messageKey = message.getKey();
    final boolean isCorrelatedBefore =
        messageState.existMessageCorrelation(
            messageKey, subscriptionRecord.getWorkflowInstanceKey());

    if (!isCorrelatedBefore) {
      subscriptionState.updateToCorrelatingState(
          subscription, message.getVariables(), ActorClock.currentTimeMillis(), messageKey);

      // send the correlate instead of acknowledge command
      messageVariables.wrap(message.getVariables());
      sideEffect.accept(this::sendCorrelateCommand);

      messageState.putMessageCorrelation(messageKey, subscriptionRecord.getWorkflowInstanceKey());
    }

    return isCorrelatedBefore;
  }

  private boolean sendCorrelateCommand() {
    return commandSender.correlateWorkflowInstanceSubscription(
        subscriptionRecord.getWorkflowInstanceKey(),
        subscriptionRecord.getElementInstanceKey(),
        subscriptionRecord.getMessageNameBuffer(),
        messageKey,
        messageVariables);
  }
}
