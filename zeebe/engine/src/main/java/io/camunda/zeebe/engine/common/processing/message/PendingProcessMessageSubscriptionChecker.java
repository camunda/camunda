/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.common.processing.message;

import io.camunda.zeebe.engine.common.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.common.state.immutable.PendingProcessMessageSubscriptionState;
import io.camunda.zeebe.engine.common.state.message.ProcessMessageSubscription;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService;
import java.time.Duration;
import java.time.InstantSource;

public final class PendingProcessMessageSubscriptionChecker
    implements StreamProcessorLifecycleAware {

  private static final Duration SUBSCRIPTION_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration SUBSCRIPTION_CHECK_INTERVAL = Duration.ofSeconds(30);

  private final SubscriptionCommandSender commandSender;
  private final PendingProcessMessageSubscriptionState pendingState;
  private final long subscriptionTimeoutInMillis;

  private ProcessingScheduleService scheduleService;
  private boolean schouldRescheduleTimer = false;
  private final InstantSource clock;

  public PendingProcessMessageSubscriptionChecker(
      final SubscriptionCommandSender commandSender,
      final PendingProcessMessageSubscriptionState pendingState,
      final InstantSource clock) {
    this.commandSender = commandSender;
    this.pendingState = pendingState;
    this.clock = clock;
    subscriptionTimeoutInMillis = SUBSCRIPTION_TIMEOUT.toMillis();
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    scheduleService = context.getScheduleService();
    schouldRescheduleTimer = true;
    rescheduleTimer();
  }

  @Override
  public void onClose() {
    cancelTimer();
  }

  @Override
  public void onFailed() {
    cancelTimer();
  }

  @Override
  public void onPaused() {
    cancelTimer();
  }

  @Override
  public void onResumed() {
    schouldRescheduleTimer = true;
    rescheduleTimer();
  }

  private void rescheduleTimer() {
    if (schouldRescheduleTimer) {
      scheduleService.runAt(
          clock.millis() + SUBSCRIPTION_CHECK_INTERVAL.toMillis(), this::checkPendingSubscriptions);
    }
  }

  private void cancelTimer() {
    schouldRescheduleTimer = false;
  }

  private void checkPendingSubscriptions() {
    pendingState.visitPending(
        clock.millis() - subscriptionTimeoutInMillis, this::sendPendingCommand);
    rescheduleTimer();
  }

  private boolean sendPendingCommand(final ProcessMessageSubscription subscription) {
    // can only be opening/closing as an opened subscription is not indexed in the sent time column
    if (subscription.isOpening()) {
      sendOpenCommand(subscription);
    } else {
      sendCloseCommand(subscription);
    }

    final var sentTime = clock.millis();
    pendingState.onSent(subscription.getRecord(), sentTime);

    return true; // to continue visiting
  }

  private void sendOpenCommand(final ProcessMessageSubscription subscription) {
    commandSender.sendDirectOpenMessageSubscription(
        subscription.getRecord().getSubscriptionPartitionId(),
        subscription.getRecord().getProcessInstanceKey(),
        subscription.getRecord().getElementInstanceKey(),
        subscription.getRecord().getBpmnProcessIdBuffer(),
        subscription.getRecord().getMessageNameBuffer(),
        subscription.getRecord().getCorrelationKeyBuffer(),
        subscription.getRecord().isInterrupting(),
        subscription.getRecord().getTenantId());
  }

  private void sendCloseCommand(final ProcessMessageSubscription subscription) {
    commandSender.sendDirectCloseMessageSubscription(
        subscription.getRecord().getSubscriptionPartitionId(),
        subscription.getRecord().getProcessInstanceKey(),
        subscription.getRecord().getElementInstanceKey(),
        subscription.getRecord().getMessageNameBuffer(),
        subscription.getRecord().getTenantId());
  }
}
