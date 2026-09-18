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
 * newer generation has since claimed must not clear a lock that generation's correlation relies on.
 * That claim is tracked primarily by ownership — see {@link
 * MutableMessageState#removeMessageCorrelationOwnedBy} — not by this subscription row's message-key
 * snapshot, because the row only reflects the newer generation's <em>latest</em> message; once it
 * moves on to a later one, the row can no longer vouch for an earlier message it legitimately still
 * owns the lock for. Duplicate stale rejects for the same message aren't theoretical — {@code
 * PendingMessageSubscriptionCheckScheduler} resends an un-acked CORRELATE every 30s, and each copy
 * that reaches the stale-generation branch on the process-instance side sends its own REJECT.
 *
 * <p>Ownership is only recorded going forward (see {@link
 * MessageSubscriptionCorrelatingV2Applier}). A lock claimed by a V1 CORRELATING event — replayed
 * from before owner-tracking existed, or from a snapshot taken before this change — has no recorded
 * owner. For that legacy case, {@link #mayReleaseCorrelationLock} falls back to the live
 * subscription row: if a different, still-alive generation's row currently shows it owns exactly
 * this message, that's trusted as evidence of ownership, the same signal the original
 * (pre-ownership) fix used, safe here specifically because it's checked against <em>this</em>
 * message rather than inferred from a possibly-later one.
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

    // Captured before subscriptionState.remove() below can invalidate the shared, reused `stored`
    // buffer by re-fetching on the same column family.
    final long storedSubscriptionKey = stored == null ? -1L : stored.getKey();
    final long storedMessageKey = stored == null ? -1L : stored.getRecord().getMessageKey();

    if (!isStaleGeneration) {
      subscriptionState.remove(value.getElementInstanceKey(), value.getMessageNameBuffer());
    }

    if (mayReleaseCorrelationLock(storedSubscriptionKey, storedMessageKey, value)) {
      messageState.removeMessageCorrelationOwnedBy(
          value.getMessageKey(), value.getBpmnProcessIdBuffer(), value.getSubscriptionKey());
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

  private boolean mayReleaseCorrelationLock(
      final long storedSubscriptionKey,
      final long storedMessageKey,
      final MessageSubscriptionRecord value) {
    if (value.getSubscriptionKey() == -1L) {
      // subscriptionKey == -1 mirrors isStaleGeneration's own convention: a reject that carries
      // no generation info at all predates/opts out of generation-tracking and is always safe to
      // release, matching the plain, unconditional release semantics this reject type has always
      // had.
      return true;
    }

    final var owner =
        messageState.correlationOwner(value.getMessageKey(), value.getBpmnProcessIdBuffer());
    if (owner != -1L) {
      return owner == value.getSubscriptionKey();
    }

    // legacy fallback (see class javadoc)
    return storedSubscriptionKey == -1L
        || storedSubscriptionKey == value.getSubscriptionKey()
        || storedMessageKey != value.getMessageKey();
  }
}
