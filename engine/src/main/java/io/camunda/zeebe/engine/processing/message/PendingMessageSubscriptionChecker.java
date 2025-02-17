/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.message;

import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.state.immutable.PendingMessageSubscriptionState;
import io.camunda.zeebe.engine.state.message.MessageSubscription;
import io.camunda.zeebe.scheduler.clock.ActorClock;

public final class PendingMessageSubscriptionChecker implements Runnable {
  private final SubscriptionCommandSender commandSender;
  private final PendingMessageSubscriptionState state;

  /**
   * Specifies the time in ms that no command is sent for a subscription after sending a command for
   * it. This ensures that we don't overload the broker with duplicate command for the same
   * subscription.
   */
  private final long subscriptionTimeout;

  public PendingMessageSubscriptionChecker(
      final SubscriptionCommandSender commandSender,
      final PendingMessageSubscriptionState state,
      final long subscriptionTimeout) {
    this.commandSender = commandSender;
    this.state = state;
    this.subscriptionTimeout = subscriptionTimeout;
  }

  @Override
  public void run() {
    state.visitPending(ActorClock.currentTimeMillis() - subscriptionTimeout, this::sendCommand);
  }

  private boolean sendCommand(final MessageSubscription subscription) {
    final var record = subscription.getRecord();

    commandSender.sendDirectCorrelateProcessMessageSubscription(
        record.getProcessInstanceKey(),
        record.getElementInstanceKey(),
        record.getBpmnProcessIdBuffer(),
        record.getMessageNameBuffer(),
        record.getMessageKey(),
        record.getVariablesBuffer(),
        record.getCorrelationKeyBuffer(),
        record.getTenantId());

    // Update the sent time for the subscription to avoid it being considered for resending too soon
    final var sentTime = ActorClock.currentTimeMillis();
    state.onSent(record, sentTime);

    return true; // to continue visiting
  }
}
