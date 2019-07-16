/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.message;

import io.zeebe.engine.processor.workflow.message.command.SubscriptionCommandSender;
import io.zeebe.engine.state.message.MessageSubscription;
import io.zeebe.engine.state.message.MessageSubscriptionState;
import io.zeebe.util.sched.clock.ActorClock;

public class PendingMessageSubscriptionChecker implements Runnable {
  private final SubscriptionCommandSender commandSender;
  private final MessageSubscriptionState subscriptionState;

  private final long subscriptionTimeout;

  public PendingMessageSubscriptionChecker(
      SubscriptionCommandSender commandSender,
      MessageSubscriptionState subscriptionState,
      long subscriptionTimeout) {
    this.commandSender = commandSender;
    this.subscriptionState = subscriptionState;
    this.subscriptionTimeout = subscriptionTimeout;
  }

  @Override
  public void run() {
    subscriptionState.visitSubscriptionBefore(
        ActorClock.currentTimeMillis() - subscriptionTimeout, this::sendCommand);
  }

  private boolean sendCommand(MessageSubscription subscription) {
    final boolean success =
        commandSender.correlateWorkflowInstanceSubscription(
            subscription.getWorkflowInstanceKey(),
            subscription.getElementInstanceKey(),
            subscription.getMessageName(),
            subscription.getMessageKey(),
            subscription.getMessageVariables());

    if (success) {
      subscriptionState.updateSentTimeInTransaction(subscription, ActorClock.currentTimeMillis());
    }

    return success;
  }
}
