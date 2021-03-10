/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.message;

import io.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.zeebe.engine.state.message.ProcessInstanceSubscription;
import io.zeebe.engine.state.mutable.MutableProcessInstanceSubscriptionState;
import io.zeebe.util.sched.clock.ActorClock;

public final class PendingProcessInstanceSubscriptionChecker implements Runnable {

  private final SubscriptionCommandSender commandSender;
  private final MutableProcessInstanceSubscriptionState subscriptionState;

  private final long subscriptionTimeout;

  public PendingProcessInstanceSubscriptionChecker(
      final SubscriptionCommandSender commandSender,
      final MutableProcessInstanceSubscriptionState subscriptionState,
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

  private boolean sendCommand(final ProcessInstanceSubscription subscription) {
    final boolean success;

    // can only be opening/closing as an opened subscription is not indexed in the sent time column
    if (subscription.isOpening()) {
      success = sendOpenCommand(subscription);
    } else {
      success = sendCloseCommand(subscription);
    }

    if (success) {
      subscriptionState.updateSentTimeInTransaction(subscription, ActorClock.currentTimeMillis());
    }

    return success;
  }

  private boolean sendOpenCommand(final ProcessInstanceSubscription subscription) {
    return commandSender.openMessageSubscription(
        subscription.getSubscriptionPartitionId(),
        subscription.getProcessInstanceKey(),
        subscription.getElementInstanceKey(),
        subscription.getBpmnProcessId(),
        subscription.getMessageName(),
        subscription.getCorrelationKey(),
        subscription.shouldCloseOnCorrelate());
  }

  private boolean sendCloseCommand(final ProcessInstanceSubscription subscription) {
    return commandSender.closeMessageSubscription(
        subscription.getSubscriptionPartitionId(),
        subscription.getProcessInstanceKey(),
        subscription.getElementInstanceKey(),
        subscription.getMessageName());
  }
}
