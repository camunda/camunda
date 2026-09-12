/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableMessageState;
import io.camunda.zeebe.engine.state.mutable.MutableMessageSubscriptionState;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;

/**
 * V2 of the REJECTED applier. Guards subscription removal against stale generations: when the
 * stored subscription belongs to a newer generation (created by a suspend/resume cycle), the
 * removal is skipped so the live replacement survives, while the correlation lock is still
 * released.
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
    final long eventSubscriptionKey = value.getSubscriptionKey();
    final var stored =
        subscriptionState.get(value.getElementInstanceKey(), value.getMessageNameBuffer());
    if (stored == null || eventSubscriptionKey == -1L || stored.getKey() == eventSubscriptionKey) {
      subscriptionState.remove(value.getElementInstanceKey(), value.getMessageNameBuffer());
    }
    messageState.removeMessageCorrelation(value.getMessageKey(), value.getBpmnProcessIdBuffer());
  }
}
