/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.message;

import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.state.message.MessageSubscription;
import io.camunda.zeebe.engine.state.mutable.MutablePendingMessageSubscriptionState;
import java.time.InstantSource;

public final class PendingMessageSubscriptionChecker implements Runnable {
  private final SubscriptionCommandSender commandSender;
  private final MutablePendingMessageSubscriptionState transientState;

  private final long subscriptionTimeout;
  private final InstantSource clock;

  public PendingMessageSubscriptionChecker(
      final SubscriptionCommandSender commandSender,
      final MutablePendingMessageSubscriptionState transientState,
      final long subscriptionTimeout,
      final InstantSource clock) {
    this.commandSender = commandSender;
    this.transientState = transientState;
    this.subscriptionTimeout = subscriptionTimeout;
    this.clock = clock;
  }

  @Override
  public void run() {
    transientState.visitSubscriptionBefore(clock.millis() - subscriptionTimeout, this::sendCommand);
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
        record.getCorrelationKeyBuffer());

    // TODO (saig0): the state change of the sent time should be reflected by a record (#6364)
    final var sentTime = clock.millis();
    transientState.updateCommandSentTime(subscription.getRecord(), sentTime);

    return true; // to continue visiting
  }
}
