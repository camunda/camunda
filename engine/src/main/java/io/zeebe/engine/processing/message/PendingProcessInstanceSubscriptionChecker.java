/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.message;

import io.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.zeebe.engine.processing.streamprocessor.ReadonlyProcessingContext;
import io.zeebe.engine.processing.streamprocessor.StreamProcessorLifecycleAware;
import io.zeebe.engine.state.message.ProcessInstanceSubscription;
import io.zeebe.engine.state.mutable.MutableProcessInstanceSubscriptionState;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.ScheduledTimer;
import io.zeebe.util.sched.clock.ActorClock;
import java.time.Duration;

public final class PendingProcessInstanceSubscriptionChecker
    implements StreamProcessorLifecycleAware {

  private static final Duration SUBSCRIPTION_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration SUBSCRIPTION_CHECK_INTERVAL = Duration.ofSeconds(30);

  private final SubscriptionCommandSender commandSender;
  private final MutableProcessInstanceSubscriptionState subscriptionState;
  private final long subscriptionTimeoutInMillis;

  private ActorControl actor;
  private ScheduledTimer timer;

  public PendingProcessInstanceSubscriptionChecker(
      final SubscriptionCommandSender commandSender,
      final MutableProcessInstanceSubscriptionState subscriptionState) {
    this.commandSender = commandSender;
    this.subscriptionState = subscriptionState;
    subscriptionTimeoutInMillis = SUBSCRIPTION_TIMEOUT.toMillis();
  }

  @Override
  public void onRecovered(final ReadonlyProcessingContext context) {
    actor = context.getActor();
    scheduleTimer();
  }

  @Override
  public void onResumed() {
    scheduleTimer();
  }

  @Override
  public void onPaused() {
    cancelTimer();
  }

  @Override
  public void onClose() {
    cancelTimer();
  }

  @Override
  public void onFailed() {
    cancelTimer();
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
    subscriptionState.visitSubscriptionBefore(
        ActorClock.currentTimeMillis() - subscriptionTimeoutInMillis, this::sendPendingCommand);
  }

  private boolean sendPendingCommand(final ProcessInstanceSubscription subscription) {
    final boolean success;

    // can only be opening/closing as an opened subscription is not indexed in the sent time column
    if (subscription.isOpening()) {
      success = sendOpenCommand(subscription);
    } else {
      success = sendCloseCommand(subscription);
    }

    if (success) {
      // TODO (saig0): the state change of the sent time should be reflected by a record (#6364)
      final var sentTime = ActorClock.currentTimeMillis();
      subscriptionState.updateSentTimeInTransaction(subscription, sentTime);
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
