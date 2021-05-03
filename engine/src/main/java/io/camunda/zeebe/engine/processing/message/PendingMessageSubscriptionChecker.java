/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.message;

import io.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.zeebe.engine.state.message.MessageSubscription;
import io.zeebe.engine.state.mutable.MutableMessageSubscriptionState;
import io.zeebe.util.sched.clock.ActorClock;

public final class PendingMessageSubscriptionChecker implements Runnable {
  private final SubscriptionCommandSender commandSender;
  private final MutableMessageSubscriptionState subscriptionState;

  private final long subscriptionTimeout;

  public PendingMessageSubscriptionChecker(
      final SubscriptionCommandSender commandSender,
      final MutableMessageSubscriptionState subscriptionState,
      final long subscriptionTimeout) {
    this.commandSender = commandSender;
    this.subscriptionState = subscriptionState;
    this.subscriptionTimeout = subscriptionTimeout;
  }

  @Override
  public void run() {
    subscriptionState.visitSubscriptionBefore(
        ActorClock.currentTimeMillis() - subscriptionTimeout, this::sendCommand);
  }

  private boolean sendCommand(final MessageSubscription subscription) {
    final var record = subscription.getRecord();

    final boolean success =
        commandSender.correlateProcessMessageSubscription(
            record.getProcessInstanceKey(),
            record.getElementInstanceKey(),
            record.getBpmnProcessIdBuffer(),
            record.getMessageNameBuffer(),
            record.getMessageKey(),
            record.getVariablesBuffer(),
            record.getCorrelationKeyBuffer());

    if (success) {
      // TODO (saig0): the state change of the sent time should be reflected by a record (#6364)
      final var sentTime = ActorClock.currentTimeMillis();
      subscriptionState.updateSentTimeInTransaction(subscription, sentTime);
    }

    return success;
  }
}
