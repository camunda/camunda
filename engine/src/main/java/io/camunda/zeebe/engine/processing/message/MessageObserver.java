/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.message;

import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.state.immutable.MessageState;
import io.camunda.zeebe.engine.state.mutable.MutablePendingMessageSubscriptionState;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import java.time.Duration;

public final class MessageObserver implements StreamProcessorLifecycleAware {

  public static final Duration SUBSCRIPTION_TIMEOUT = Duration.ofSeconds(10);
  public static final Duration SUBSCRIPTION_CHECK_INTERVAL = Duration.ofSeconds(30);

  private final SubscriptionCommandSender subscriptionCommandSender;
  private final MessageState messageState;
  private final MutablePendingMessageSubscriptionState pendingState;
  private final int messagesTtlCheckerBatchLimit;
  private final Duration messagesTtlCheckerInterval;

  public MessageObserver(
      final MessageState messageState,
      final MutablePendingMessageSubscriptionState pendingState,
      final SubscriptionCommandSender subscriptionCommandSender,
      final Duration messagesTtlCheckerInterval,
      final int messagesTtlCheckerBatchLimit) {
    this.subscriptionCommandSender = subscriptionCommandSender;
    this.messageState = messageState;
    this.pendingState = pendingState;
    this.messagesTtlCheckerInterval = messagesTtlCheckerInterval;
    this.messagesTtlCheckerBatchLimit = messagesTtlCheckerBatchLimit;
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    final var scheduleService = context.getScheduleService();
    final var timeToLiveChecker =
        new MessageTimeToLiveChecker(
            messagesTtlCheckerInterval,
            messagesTtlCheckerBatchLimit,
            scheduleService,
            messageState);
    scheduleService.runDelayed(messagesTtlCheckerInterval, timeToLiveChecker);

    final var pendingSubscriptionChecker =
        new PendingMessageSubscriptionChecker(
            subscriptionCommandSender, pendingState, SUBSCRIPTION_TIMEOUT.toMillis());
    scheduleService.runAtFixedRate(SUBSCRIPTION_CHECK_INTERVAL, pendingSubscriptionChecker);
  }
}
