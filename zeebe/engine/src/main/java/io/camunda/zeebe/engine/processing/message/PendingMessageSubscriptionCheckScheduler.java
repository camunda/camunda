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
import io.camunda.zeebe.engine.state.immutable.ScheduledTaskState;
import io.camunda.zeebe.engine.state.message.MessageSubscription;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import java.time.Duration;
import java.time.InstantSource;
import java.util.function.Supplier;

public final class PendingMessageSubscriptionCheckScheduler
    implements Runnable, StreamProcessorLifecycleAware {

  public static final Duration SUBSCRIPTION_TIMEOUT = Duration.ofSeconds(10);
  public static final Duration SUBSCRIPTION_CHECK_INTERVAL = Duration.ofSeconds(30);

  private final SubscriptionCommandSender commandSender;
  private final PendingMessageSubscriptionState state;

  /**
   * Specifies the time in ms that no command is sent for a subscription after sending a command for
   * it. This ensures that we don't overload the broker with duplicate command for the same
   * subscription.
   */
  private final long subscriptionTimeout;

  private final InstantSource clock;

  public PendingMessageSubscriptionCheckScheduler(
      final SubscriptionCommandSender commandSender,
      final PendingMessageSubscriptionState state,
      final long subscriptionTimeout,
      final InstantSource clock) {
    this.commandSender = commandSender;
    this.state = state;
    this.subscriptionTimeout = subscriptionTimeout;
    this.clock = clock;
  }

  public PendingMessageSubscriptionCheckScheduler(
      final Supplier<ScheduledTaskState> scheduledTaskStateFactory,
      final PendingMessageSubscriptionState pendingState,
      final SubscriptionCommandSender subscriptionCommandSender,
      final Duration messagesTtlCheckerInterval,
      final int messagesTtlCheckerBatchLimit,
      final boolean enableMessageTtlCheckerAsync,
      final InstantSource clock) {
    this(subscriptionCommandSender, pendingState, SUBSCRIPTION_TIMEOUT.toMillis(), clock);
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

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    scheduleMessageTtlChecker(context);
    schedulePendingMessageSubscriptionChecker(context);
  }

  private void scheduleMessageTtlChecker(final ReadonlyStreamProcessorContext context) {
    /* NOT APPLICABLE HERE
    final var scheduleService = context.getScheduleService();
    final var messageState = scheduledTaskStateFactory.get().getMessageState();
    final var timestamp = clock.millis() + messagesTtlCheckerInterval.toMillis();
    final var timeToLiveChecker =
        new MessageTimeToLiveCheckScheduler(
            messagesTtlCheckerInterval,
            messagesTtlCheckerBatchLimit,
            enableMessageTtlCheckerAsync,
            scheduleService,
            messageState,
            context.getClock());
    if (enableMessageTtlCheckerAsync) {
      scheduleService.runAtAsync(timestamp, timeToLiveChecker);
    } else {
      scheduleService.runAt(timestamp, timeToLiveChecker);
    }
    */
  }

  private void schedulePendingMessageSubscriptionChecker(
      final ReadonlyStreamProcessorContext context) {
    final var scheduleService = context.getScheduleService();
    final var pendingSubscriptionChecker =
        new PendingMessageSubscriptionCheckScheduler(
            commandSender,
            state,
            SUBSCRIPTION_TIMEOUT.toMillis(),
            context.getClock());
    scheduleService.runAtFixedRate(SUBSCRIPTION_CHECK_INTERVAL, pendingSubscriptionChecker);
  }
}
