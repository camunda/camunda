/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.message;

import io.camunda.zeebe.engine.processing.streamprocessor.ReadonlyProcessingContext;
import io.camunda.zeebe.engine.processing.streamprocessor.StreamProcessorLifecycleAware;
import io.camunda.zeebe.engine.state.message.ProcessMessageSubscription;
import io.camunda.zeebe.engine.state.mutable.MutablePendingProcessMessageSubscriptionState;
import io.camunda.zeebe.scheduler.ActorControl;
import io.camunda.zeebe.scheduler.ScheduledTimer;
import io.camunda.zeebe.scheduler.clock.ActorClock;
import java.time.Duration;

public final class PendingProcessMessageSubscriptionChecker
    implements StreamProcessorLifecycleAware {

  private static final Duration SUBSCRIPTION_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration SUBSCRIPTION_CHECK_INTERVAL = Duration.ofSeconds(30);

  private final MutablePendingProcessMessageSubscriptionState pendingState;
  private final long subscriptionTimeoutInMillis;

  private ActorControl actor;
  private ScheduledTimer timer;

  public PendingProcessMessageSubscriptionChecker(
      final MutablePendingProcessMessageSubscriptionState pendingState) {
    this.pendingState = pendingState;
    subscriptionTimeoutInMillis = SUBSCRIPTION_TIMEOUT.toMillis();
  }

  @Override
  public void onRecovered(final ReadonlyProcessingContext context) {
    actor = context.getActor();
    scheduleTimer();
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
    scheduleTimer();
  }

  private void scheduleTimer() {
    if (timer == null) {
      timer = actor.runAtFixedRate(SUBSCRIPTION_CHECK_INTERVAL, this::checkPendingSubscriptions);
    }
  }

  private void cancelTimer() {
    if (timer != null) {
      timer.cancel();
      timer = null;
    }
  }

  private void checkPendingSubscriptions() {
    pendingState.visitSubscriptionBefore(
        ActorClock.currentTimeMillis() - subscriptionTimeoutInMillis, this::sendPendingCommand);
  }

  private boolean sendPendingCommand(final ProcessMessageSubscription subscription) {
    final boolean success;

    // can only be opening/closing as an opened subscription is not indexed in the sent time column
    if (subscription.isOpening()) {
      success = sendOpenCommand(subscription);
    } else {
      success = sendCloseCommand(subscription);
    }

    if (success) {
      final var sentTime = ActorClock.currentTimeMillis();
      pendingState.updateSentTime(subscription.getRecord(), sentTime);
    }

    return success;
  }

  private boolean sendOpenCommand(final ProcessMessageSubscription subscription) {
    return commandSender.openMessageSubscription(
        subscription.getRecord().getSubscriptionPartitionId(),
        subscription.getRecord().getProcessInstanceKey(),
        subscription.getRecord().getElementInstanceKey(),
        subscription.getRecord().getBpmnProcessIdBuffer(),
        subscription.getRecord().getMessageNameBuffer(),
        subscription.getRecord().getCorrelationKeyBuffer(),
        subscription.getRecord().isInterrupting());
  }

  private boolean sendCloseCommand(final ProcessMessageSubscription subscription) {
    return commandSender.closeMessageSubscription(
        subscription.getRecord().getSubscriptionPartitionId(),
        subscription.getRecord().getProcessInstanceKey(),
        subscription.getRecord().getElementInstanceKey(),
        subscription.getRecord().getMessageNameBuffer());
  }
}
