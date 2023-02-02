/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.message;

import io.camunda.zeebe.engine.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.engine.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.state.immutable.MessageState;
import io.camunda.zeebe.engine.state.mutable.MutablePendingMessageSubscriptionState;
import java.time.Duration;

public final class MessageObserver implements StreamProcessorLifecycleAware {

  public static final Duration MESSAGE_TIME_TO_LIVE_CHECK_INTERVAL = Duration.ofSeconds(60);

  public static final Duration SUBSCRIPTION_TIMEOUT = Duration.ofSeconds(10);
  public static final Duration SUBSCRIPTION_CHECK_INTERVAL = Duration.ofSeconds(30);

  private final SubscriptionCommandSender subscriptionCommandSender;
  private final MessageState messageState;
  private final MutablePendingMessageSubscriptionState pendingState;

  public MessageObserver(
      final MessageState messageState,
      final MutablePendingMessageSubscriptionState pendingState,
      final SubscriptionCommandSender subscriptionCommandSender) {
    this.subscriptionCommandSender = subscriptionCommandSender;
    this.messageState = messageState;
    this.pendingState = pendingState;
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    final var scheduleService = context.getScheduleService();
    // it is safe to reuse the write because we running in the same actor/thread
    final MessageTimeToLiveChecker timeToLiveChecker =
        new MessageTimeToLiveChecker(messageState, context.getMetrics());
    scheduleService.runAtFixedRate(MESSAGE_TIME_TO_LIVE_CHECK_INTERVAL, timeToLiveChecker);

    final PendingMessageSubscriptionChecker pendingSubscriptionChecker =
        new PendingMessageSubscriptionChecker(
            subscriptionCommandSender, pendingState, SUBSCRIPTION_TIMEOUT.toMillis());
    scheduleService.runAtFixedRate(SUBSCRIPTION_CHECK_INTERVAL, pendingSubscriptionChecker);
  }
}
