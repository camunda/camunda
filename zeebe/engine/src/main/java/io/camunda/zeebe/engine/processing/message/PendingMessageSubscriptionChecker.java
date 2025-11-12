/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.state.immutable.PendingMessageSubscriptionState;
import io.camunda.zeebe.engine.state.message.MessageSubscription;
import java.time.InstantSource;

public final class PendingMessageSubscriptionChecker implements Runnable {
  private final SubscriptionCommandSender commandSender;
  private final PendingMessageSubscriptionState state;

  /**
   * Specifies the time in ms that no command is sent for a subscription after sending a command for
   * it. This ensures that we don't overload the broker with duplicate command for the same
   * subscription.
   */
  private final long subscriptionTimeout;

  private final InstantSource clock;

  public PendingMessageSubscriptionChecker(
      final SubscriptionCommandSender commandSender,
      final PendingMessageSubscriptionState state,
      final long subscriptionTimeout,
      final InstantSource clock) {
    this.commandSender = commandSender;
    this.state = state;
    this.subscriptionTimeout = subscriptionTimeout;
    this.clock = clock;
  }

  @Override
  public void run() {
    state.visitPending(clock.millis() - subscriptionTimeout, this::sendCommand);
  }

  private boolean sendCommand(final MessageSubscription subscription) {
    final var record = subscription.getRecord();

    commandSender.sendDirectCorrelateProcessMessageSubscription(
        record.getProcessInstanceKey(),
        record.getElementInstanceKey(),
        record.getProcessDefinitionKey(),
        record.getBpmnProcessIdBuffer(),
        record.getMessageNameBuffer(),
        record.getMessageKey(),
        record.getVariablesBuffer(),
        record.getCorrelationKeyBuffer(),
        record.getTenantId());

    // Update the sent time for the subscription to avoid it being considered for resending too soon
    final var sentTime = clock.millis();
    state.onSent(record, sentTime);

    return true; // to continue visiting
  }
}
