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
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService;
import java.time.Duration;
import java.time.InstantSource;

public final class PendingMessageSubscriptionChecker implements StreamProcessorLifecycleAware {

  public static final Duration SUBSCRIPTION_TIMEOUT = Duration.ofSeconds(10);
  public static final Duration SUBSCRIPTION_CHECK_INTERVAL = Duration.ofSeconds(30);

  private final SubscriptionCommandSender commandSender;
  private final PendingMessageSubscriptionState pendingState;
  private final long subscriptionTimeout;

  private ProcessingScheduleService scheduleService;
  private InstantSource clock;

  public PendingMessageSubscriptionChecker(
      final SubscriptionCommandSender commandSender,
      final PendingMessageSubscriptionState pendingState) {
    this.commandSender = commandSender;
    this.pendingState = pendingState;
    subscriptionTimeout = SUBSCRIPTION_TIMEOUT.toMillis();
  }

  private void checkPendingSubscriptions() {
    pendingState.visitPending(clock.millis() - subscriptionTimeout, this::sendCommand);
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
    pendingState.onSent(record, sentTime);

    return true; // to continue visiting
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    scheduleService = context.getScheduleService();
    clock = context.getClock();
    scheduleService.runAtFixedRate(SUBSCRIPTION_CHECK_INTERVAL, this::checkPendingSubscriptions);
  }

  @Override
  public void onClose() {
    // runAtFixedRate automatically stops when the scheduler is closed
  }

  @Override
  public void onFailed() {
    // runAtFixedRate automatically stops when the scheduler fails
  }

  @Override
  public void onPaused() {
    // runAtFixedRate will continue running, but pending messages won't be processed
    // because the stream processor is paused
  }

  @Override
  public void onResumed() {
    // No additional action needed as runAtFixedRate continues automatically
  }
}
