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
 * that are due for retry are re-sent to {@code P_B} via {@link
 * SubscriptionCommandSender#sendDirectStartProcessInstanceRequest}.
 *
 * <p>The scheduler ticks every base {@code retryInterval} and, on each tick, re-sends every ask
 * that is due. An ask is due when its transient last-sent time plus its back-off interval has
 * elapsed; the back-off interval grows with the ask's persisted {@code rejectionCount} as {@code
 * baseInterval * 2^min(rejectionCount, MAX_BACKOFF_EXPONENT)}. An un-replied ask has a rejection
 * count of {@code 0} and so retries at the base interval (at-least-once delivery), while a
 * repeatedly-rejected ask backs off exponentially up to {@code baseInterval *
 * 2^MAX_BACKOFF_EXPONENT} so it does not storm {@code P_B} while its Business ID stays held. After
 * sending, the entry's transient last-sent time is updated via {@link
 * MessageStartProcessInstanceAskState#updateLastSentTime}; the back-off magnitude itself is
 * event-sourced (advanced by the rejection applier), so it survives leader changes.
 *
 * <p>Entries are removed from the state when the ask succeeds ({@code STARTED}) or the originating
 * buffered message expires on {@code P_K}; a rejection keeps the entry and increments its rejection
 * count. Each retry re-emits the same {@code messageDeadline} the original ask carried, so any
 * retry either lands on a live dedup row on {@code P_B} (same outcome) or arrives after both the
 * dedup row and the buffered message have expired together — in which case the pending-ask has
 * already been cleared locally and no retry is emitted.
 */
public final class PendingMessageStartAskCheckScheduler
    implements Runnable, StreamProcessorLifecycleAware {

  /**
   * Caps the back-off exponent so the retry interval saturates at {@code baseInterval * 2^6} (64x
   * the base). Bounds the maximum re-probe interval independently of how long an ask stays blocked,
   * and keeps {@code 2^exponent} from overflowing.
   */
  private static final long MAX_BACKOFF_EXPONENT = 6L;

  private static final Logger LOG =
      LoggerFactory.getLogger(PendingMessageStartAskCheckScheduler.class);

  private final SubscriptionCommandSender commandSender;
  private final MessageStartProcessInstanceAskState state;
  private final RoutingInfo routingInfo;
  private final Supplier<Duration> retryInterval;

  private InstantSource clock;

  /**
   * @param commandSender sender used to dispatch asks to {@code P_B}
   * @param state the pending ask state from which to read and update entries
   * @param routingInfo used to derive the target partition for a business ID
   * @param retryInterval supplier returning the base retry interval; also the scheduler tick
   *     cadence. The per-ask interval is this value scaled by the ask's rejection count. Retries
   *     always re-emit the original {@code messageDeadline}, so correctness does not depend on this
   *     interval relative to any window — it only controls the retry cadence
   */
  public PendingMessageStartAskCheckScheduler(
      final SubscriptionCommandSender commandSender,
      final MessageStartProcessInstanceAskState state,
      final RoutingInfo routingInfo,
      final Supplier<Duration> retryInterval) {
    this.commandSender = commandSender;
    this.state = state;
    this.routingInfo = routingInfo;
    this.retryInterval = retryInterval;
  }

  @Override
  public void run() {
    final long now = clock.millis();
    state.forEachPendingAsk(
        (lastSentTime, ask) -> {
          if (lastSentTime + retryIntervalMillis(ask.getRejectionCount()) <= now) {
            sendAsk(ask, now);
          }
        });
  }

  /**
   * Returns the back-off interval for an ask with the given rejection count: {@code baseInterval *
   * 2^min(rejectionCount, MAX_BACKOFF_EXPONENT)}. A rejection count of {@code 0} yields the base
   * interval.
   */
  private long retryIntervalMillis(final long rejectionCount) {
    final long base = retryInterval.get().toMillis();
    final long exponent = Math.min(rejectionCount, MAX_BACKOFF_EXPONENT);
    long interval = base;
    for (long i = 0; i < exponent; i++) {
      interval *= 2;
    }
    return interval;
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
    context.getScheduleService().runAtFixedRate(retryInterval.get(), this);
  }
}
