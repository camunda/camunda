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
 * V2 of the REJECTED applier: skips removing the subscription when the stored one belongs to a
 * newer generation (created by a suspend/resume cycle), so that generation survives; the
 * correlation lock is released only if this newer generation hasn't already claimed the message.
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
      // remove() re-fetches by the same key, overwriting the shared buffer `stored` points to.
      // Safe here because newerGenerationAlreadyCorrelatedMessage never reads `stored` when
      // isStaleGeneration is false — the only branch where remove() runs.
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
   * True when the newer generation already correlated exactly this message, so a duplicate/delayed
   * reject for it must not release the lock. Exact match only: the newer generation may have
   * skipped this message while it was still locked and correlated a later one instead, so a
   * numerically-ahead last-correlated key doesn't mean it was ever claimed.
   */
  private boolean newerGenerationAlreadyCorrelatedMessage(
      final MessageSubscription stored, final boolean isStaleGeneration, final long messageKey) {
    return isStaleGeneration && stored.getRecord().getMessageKey() == messageKey;
  }
}
