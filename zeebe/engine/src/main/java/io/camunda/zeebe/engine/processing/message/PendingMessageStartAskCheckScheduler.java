/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.state.immutable.MessageStartProcessInstanceAskState;
import io.camunda.zeebe.engine.state.message.MessageStartProcessInstanceAsk;
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
 * MessageStartProcessInstanceAskState#updateLastSentTime}.
 *
 * <p>Entries are removed from the state when any of the three reply intents ({@code STARTED},
 * {@code UNIQUENESS_REJECTED}, {@code NO_SUBSCRIPTION_REJECTED}) is applied on {@code P_K}; those
 * handlers land in a separate commit. Entries are also cleared when the originating buffered
 * message expires on {@code P_K}, so a retry never outlives the buffered message it refers to.
 *
 * <p>Each retry re-emits the same {@code messageDeadline} that the original ask carried (sourced
 * from the buffered message's {@code publishTime + ttl}). The dedup row on {@code P_B} is keyed by
 * that same deadline, so any retry either lands on a live dedup row (cache hit, same outcome) or
 * arrives after both the dedup row and the buffered message have expired together — in which case
 * the pending-ask has already been cleared locally and no retry is emitted.
 */
public final class PendingMessageStartAskCheckScheduler
    implements Runnable, StreamProcessorLifecycleAware {

  private static final Logger LOG =
      LoggerFactory.getLogger(PendingMessageStartAskCheckScheduler.class);

  private final SubscriptionCommandSender commandSender;
  private final MessageStartProcessInstanceAskState state;
  private final RoutingInfo routingInfo;
  private final Supplier<Duration> retryInterval;
  private final Duration checkInterval;

  private InstantSource clock;

  /**
   * @param commandSender sender used to dispatch asks to {@code P_B}
   * @param state the pending ask state from which to read and update entries
   * @param routingInfo used to derive the target partition for a business ID
   * @param retryInterval supplier returning how long to wait before retrying an ask; retries always
   *     re-emit the original {@code messageDeadline}, so correctness does not depend on this
   *     interval relative to any window — it only controls the retry cadence
   * @param checkInterval how often to run the check
   */
  public PendingMessageStartAskCheckScheduler(
      final SubscriptionCommandSender commandSender,
      final MessageStartProcessInstanceAskState state,
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
        ask.getMessageDeadline(),
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
