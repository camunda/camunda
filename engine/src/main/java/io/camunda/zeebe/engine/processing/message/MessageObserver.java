/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.message;

import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.processing.streamprocessor.ProcessingResult;
import io.camunda.zeebe.engine.processing.streamprocessor.ReadonlyProcessingContext;
import io.camunda.zeebe.engine.processing.streamprocessor.StreamProcessor.ProcessingSchedulingServiceImpl;
import io.camunda.zeebe.engine.processing.streamprocessor.StreamProcessorLifecycleAware;
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
  private ProcessingSchedulingServiceImpl processingSchedulingService;
  private MessageTimeToLiveChecker timeToLiveChecker;
  private PendingMessageSubscriptionChecker pendingSubscriptionChecker;

  public MessageObserver(
      final MessageState messageState,
      final MutablePendingMessageSubscriptionState pendingState,
      final SubscriptionCommandSender subscriptionCommandSender) {
    this.subscriptionCommandSender = subscriptionCommandSender;
    this.messageState = messageState;
    this.pendingState = pendingState;
  }

  ProcessingResult runMessageTTLCheck() {
    processingSchedulingService.runWithDelay(
        MESSAGE_TIME_TO_LIVE_CHECK_INTERVAL, this::runMessageTTLCheck);

    return timeToLiveChecker.get();
  }

  ProcessingResult runPendingSubscriptionCheck() {
    processingSchedulingService.runWithDelay(
        SUBSCRIPTION_CHECK_INTERVAL, this::runPendingSubscriptionCheck);

    return pendingSubscriptionChecker.get();
  }

  @Override
  public void onRecovered(final ReadonlyProcessingContext context) {
    // it is safe to reuse the write because we running in the same actor/thread
    timeToLiveChecker = new MessageTimeToLiveChecker(context.getWriters().command(), messageState);

    processingSchedulingService = context.getProcessingSchedulingService();
    processingSchedulingService.runWithDelay(
        MESSAGE_TIME_TO_LIVE_CHECK_INTERVAL, this::runMessageTTLCheck);

    pendingSubscriptionChecker =
        new PendingMessageSubscriptionChecker(
            subscriptionCommandSender, pendingState, SUBSCRIPTION_TIMEOUT.toMillis());
    processingSchedulingService.runWithDelay(
        SUBSCRIPTION_CHECK_INTERVAL, this::runPendingSubscriptionCheck);
  }
}
