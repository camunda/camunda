/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.message.MessageSubscription;
import io.camunda.zeebe.engine.state.mutable.MutableMessageState;
import io.camunda.zeebe.engine.state.mutable.MutableMessageSubscriptionState;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;

/**
 * V2 of the REJECTED applier. Guards subscription removal against stale generations: when the
 * stored subscription belongs to a newer generation (created by a suspend/resume cycle), the
 * removal is skipped so that newer generation survives, while the correlation lock is still
 * released.
 *
 * <p>The lock release itself is guarded too: a duplicate/delayed stale reject for a message the
 * newer generation already correlated (its stored record's message key is at or past this reject's)
 * must not clear a lock that generation's completed correlation still relies on. Duplicate stale
 * rejects for the same message aren't theoretical — {@code
 * PendingMessageSubscriptionCheckScheduler} resends an un-acked CORRELATE every 30s, and each copy
 * that reaches the stale-generation branch on the process-instance side sends its own REJECT.
 *
 * <p>See <a href="https://github.com/camunda/camunda/issues/61099">#61099</a>.
 */
public final class MessageSubscriptionRejectedV2Applier
    implements TypedEventApplier<MessageSubscriptionIntent, MessageSubscriptionRecord> {

  private final MutableMessageState messageState;
  private final MutableMessageSubscriptionState subscriptionState;

  public MessageSubscriptionRejectedV2Applier(
      final MutableMessageState messageState,
      final MutableMessageSubscriptionState subscriptionState) {
    this.messageState = messageState;
    this.subscriptionState = subscriptionState;
  }

  @Override
  public void applyState(final long key, final MessageSubscriptionRecord value) {
    final var stored =
        subscriptionState.get(value.getElementInstanceKey(), value.getMessageNameBuffer());
    final boolean isStaleGeneration = isStaleGeneration(stored, value.getSubscriptionKey());

    if (!isStaleGeneration) {
      // subscriptionState.remove(...) re-fetches by the same key, overwriting the shared
      // ColumnFamily#get(key) value instance `stored` points to. Safe only because
      // newerGenerationAlreadyCorrelatedMessage short-circuits without reading `stored` whenever
      // isStaleGeneration is false — the one branch where this remove() runs.
      subscriptionState.remove(value.getElementInstanceKey(), value.getMessageNameBuffer());
    }
    if (!newerGenerationAlreadyCorrelatedMessage(
        stored, isStaleGeneration, value.getMessageKey())) {
      messageState.removeMessageCorrelation(value.getMessageKey(), value.getBpmnProcessIdBuffer());
    }
  }

  /**
   * True when {@code stored} is a newer generation created by a suspend/resume cycle after this
   * reject's own subscription generation was superseded.
   */
  private boolean isStaleGeneration(
      final MessageSubscription stored, final long eventSubscriptionKey) {
    return stored != null && eventSubscriptionKey != -1L && stored.getKey() != eventSubscriptionKey;
  }

  /**
   * True when the newer generation has already correlated exactly this message, meaning the
   * correlation lock is legitimately held on its behalf and must not be released by this (possibly
   * duplicate/delayed) reject. Exact match only: the newer generation's own CREATE-time scan may
   * have skipped this exact message (still locked by the stale subscription) and correlated a later
   * one instead, so its last-correlated key being numerically ahead of this one does not mean it
   * ever claimed this message.
   */
  private boolean newerGenerationAlreadyCorrelatedMessage(
      final MessageSubscription stored, final boolean isStaleGeneration, final long messageKey) {
    return isStaleGeneration && stored.getRecord().getMessageKey() == messageKey;
  }
}
