/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.message;

import io.zeebe.engine.processor.ReadonlyProcessingContext;
import io.zeebe.engine.processor.StreamProcessorLifecycleAware;
import io.zeebe.engine.processor.workflow.message.command.SubscriptionCommandSender;
import io.zeebe.engine.state.message.MessageState;
import io.zeebe.engine.state.message.MessageSubscriptionState;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.ScheduledTimer;
import java.time.Duration;

public final class MessageObserver implements StreamProcessorLifecycleAware {

  public static final Duration MESSAGE_TIME_TO_LIVE_CHECK_INTERVAL = Duration.ofSeconds(60);

  public static final Duration SUBSCRIPTION_TIMEOUT = Duration.ofSeconds(10);
  public static final Duration SUBSCRIPTION_CHECK_INTERVAL = Duration.ofSeconds(30);

  private final SubscriptionCommandSender subscriptionCommandSender;
  private final MessageState messageState;
  private final MessageSubscriptionState subscriptionState;
  private ActorControl actor;
  private ScheduledTimer timeToLiveCheckerTimer;
  private ScheduledTimer pendingSubscriptionCheckerTimer;
  private MessageTimeToLiveChecker timeToLiveChecker;
  private PendingMessageSubscriptionChecker pendingSubscriptionChecker;

  public MessageObserver(
      final MessageState messageState,
      final MessageSubscriptionState subscriptionState,
      final SubscriptionCommandSender subscriptionCommandSender) {
    this.subscriptionCommandSender = subscriptionCommandSender;
    this.messageState = messageState;
    this.subscriptionState = subscriptionState;
  }

  @Override
  public void onRecovered(final ReadonlyProcessingContext context) {
    actor = context.getActor();
    // it is safe to reuse the write because we running in the same actor/thread
    timeToLiveChecker = new MessageTimeToLiveChecker(context.getLogStreamWriter(), messageState);
    timeToLiveCheckerTimer =
        actor.runAtFixedRate(MESSAGE_TIME_TO_LIVE_CHECK_INTERVAL, timeToLiveChecker);

    pendingSubscriptionChecker =
        new PendingMessageSubscriptionChecker(
            subscriptionCommandSender, subscriptionState, SUBSCRIPTION_TIMEOUT.toMillis());
    pendingSubscriptionCheckerTimer =
        actor.runAtFixedRate(SUBSCRIPTION_CHECK_INTERVAL, pendingSubscriptionChecker);
  }

  @Override
  public void onPaused() {
    if (timeToLiveCheckerTimer != null) {
      timeToLiveCheckerTimer.cancel();
      timeToLiveCheckerTimer = null;
    }
  }

  @Override
  public void onResumed() {
    if (timeToLiveCheckerTimer == null) {
      timeToLiveCheckerTimer =
          actor.runAtFixedRate(MESSAGE_TIME_TO_LIVE_CHECK_INTERVAL, timeToLiveChecker);
    }
    if (pendingSubscriptionCheckerTimer == null) {
      pendingSubscriptionCheckerTimer =
          actor.runAtFixedRate(SUBSCRIPTION_CHECK_INTERVAL, pendingSubscriptionChecker);
    }
  }
}
