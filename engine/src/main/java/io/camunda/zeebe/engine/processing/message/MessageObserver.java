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
import io.camunda.zeebe.engine.state.immutable.ScheduledTaskState;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import java.time.Duration;
import java.util.function.Supplier;

public final class MessageObserver implements StreamProcessorLifecycleAware {

  public static final Duration SUBSCRIPTION_TIMEOUT = Duration.ofSeconds(10);
  public static final Duration SUBSCRIPTION_CHECK_INTERVAL = Duration.ofSeconds(30);

  private final SubscriptionCommandSender subscriptionCommandSender;
  private final Supplier<ScheduledTaskState> scheduledTaskStateFactory;
  private final PendingMessageSubscriptionState pendingState;
  private final int messagesTtlCheckerBatchLimit;
  private final Duration messagesTtlCheckerInterval;
  private final boolean enableMessageTtlCheckerAsync;

  public MessageObserver(
      final Supplier<ScheduledTaskState> scheduledTaskStateFactory,
      final PendingMessageSubscriptionState pendingState,
      final SubscriptionCommandSender subscriptionCommandSender,
      final Duration messagesTtlCheckerInterval,
      final int messagesTtlCheckerBatchLimit,
      final boolean enableMessageTtlCheckerAsync) {
    this.subscriptionCommandSender = subscriptionCommandSender;
    this.scheduledTaskStateFactory = scheduledTaskStateFactory;
    this.pendingState = pendingState;
    this.messagesTtlCheckerInterval = messagesTtlCheckerInterval;
    this.messagesTtlCheckerBatchLimit = messagesTtlCheckerBatchLimit;
    this.enableMessageTtlCheckerAsync = enableMessageTtlCheckerAsync;
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    scheduleMessageTtlChecker(context);
    schedulePendingMessageSubscriptionChecker(context);
  }

  private void scheduleMessageTtlChecker(final ReadonlyStreamProcessorContext context) {
    final var scheduleService = context.getScheduleService();
    final var messageState = scheduledTaskStateFactory.get().getMessageState();
    final var timeToLiveChecker =
        new MessageTimeToLiveChecker(
            messagesTtlCheckerInterval,
            messagesTtlCheckerBatchLimit,
            enableMessageTtlCheckerAsync,
            scheduleService,
            messageState);
    if (enableMessageTtlCheckerAsync) {
      scheduleService.runDelayedAsync(messagesTtlCheckerInterval, timeToLiveChecker);
    } else {
      scheduleService.runDelayed(messagesTtlCheckerInterval, timeToLiveChecker);
    }
  }

  private void schedulePendingMessageSubscriptionChecker(
      final ReadonlyStreamProcessorContext context) {
    final var scheduleService = context.getScheduleService();
    final var pendingSubscriptionChecker =
        new PendingMessageSubscriptionChecker(
            subscriptionCommandSender, pendingState, SUBSCRIPTION_TIMEOUT.toMillis());
    scheduleService.runAtFixedRate(SUBSCRIPTION_CHECK_INTERVAL, pendingSubscriptionChecker);
  }
}
