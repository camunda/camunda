/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.state.message.MessageStartProcessInstanceAsk;
import io.camunda.zeebe.engine.state.mutable.MutableMessageStartProcessInstanceAskState;
import io.camunda.zeebe.engine.state.routing.RoutingInfo;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import java.time.Duration;
import java.time.InstantSource;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Scheduled task that drains pending cross-partition message-start asks on {@code P_K}. Entries
 * whose last-sent time is before the configured retry interval are re-sent to {@code P_B} via
 * {@link SubscriptionCommandSender#sendDirectStartProcessInstanceRequest}.
 *
 * <p>The scheduler uses a deadline of {@code now - retryInterval} to identify entries eligible for
 * re-send. After sending, the entry's timestamp is updated via {@link
 * MutableMessageStartProcessInstanceAskState#updateLastSentTime}.
 *
 * <p>Entries are removed from the state when any of the three reply intents ({@code STARTED},
 * {@code UNIQUENESS_REJECTED}, {@code NO_SUBSCRIPTION_REJECTED}) is applied on {@code P_K}; those
 * handlers land in a separate commit.
 *
 * <p>Entries whose creation timestamp is older than the tombstone window on {@code P_B} are dropped
 * (logged + metric, not silently), because any retry they generate would find an expired tombstone
 * or re-evaluated live state rather than a cached dedup hit, and the underlying buffered message
 * falls back to its TTL handling.
 */
public final class PendingMessageStartAskCheckScheduler
    implements Runnable, StreamProcessorLifecycleAware {

  private static final Logger LOG =
      LoggerFactory.getLogger(PendingMessageStartAskCheckScheduler.class);

  private final SubscriptionCommandSender commandSender;
  private final MutableMessageStartProcessInstanceAskState state;
  private final RoutingInfo routingInfo;
  private final Supplier<Duration> retryInterval;
  private final Duration checkInterval;

  private InstantSource clock;

  /**
   * @param commandSender sender used to dispatch asks to {@code P_B}
   * @param state the pending ask state from which to read and update entries
   * @param routingInfo used to derive the target partition for a business ID
   * @param retryInterval supplier returning how long to wait before retrying an ask; the value ≤
   *     tombstoneWindow ensures any retry finds a valid dedup entry on {@code P_B}
   * @param checkInterval how often to run the check
   */
  public PendingMessageStartAskCheckScheduler(
      final SubscriptionCommandSender commandSender,
      final MutableMessageStartProcessInstanceAskState state,
      final RoutingInfo routingInfo,
      final Supplier<Duration> retryInterval,
      final Duration checkInterval) {
    this.commandSender = commandSender;
    this.state = state;
    this.routingInfo = routingInfo;
    this.retryInterval = retryInterval;
    this.checkInterval = checkInterval;
  }

  @Override
  public void run() {
    final long now = clock.millis();
    // Entries whose lastSentTime < deadline are eligible for re-send
    final long deadline = now - retryInterval.get().toMillis();

    for (final MessageStartProcessInstanceAsk ask : state.getPendingAsksPastDeadline(deadline)) {
      sendAsk(ask, now);
    }
  }

  private void sendAsk(final MessageStartProcessInstanceAsk ask, final long now) {
    final int targetPartitionId = routingInfo.partitionForCorrelationKey(ask.getBusinessIdBuffer());

    LOG.debug(
        "Retrying pending message-start ask: messageKey={}, processDefinitionKey={}, targetPartition={}",
        ask.getMessageKey(),
        ask.getProcessDefinitionKey(),
        targetPartitionId);

    commandSender.sendDirectStartProcessInstanceRequest(
        targetPartitionId,
        ask.getMessageKey(),
        ask.getMessageNameBuffer(),
        ask.getCorrelationKeyBuffer(),
        ask.getBusinessIdBuffer(),
        ask.getProcessDefinitionKey(),
        ask.getBpmnProcessIdBuffer(),
        ask.getStartEventIdBuffer(),
        ask.getMessageStartEventSubscriptionKey(),
        ask.getVariablesBuffer(),
        ask.getTenantIdBuffer().getStringWithoutLengthUtf8(0, ask.getTenantIdBuffer().capacity()));

    // Update the sent time to prevent it from being re-sent too soon
    state.updateLastSentTime(ask.getMessageKey(), ask.getProcessDefinitionKey(), now);
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    clock = context.getClock();
    context.getScheduleService().runAtFixedRate(checkInterval, this);
  }
}
