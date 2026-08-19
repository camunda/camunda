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
 * Applies a suspension-deferred correlation: unlike {@link MessageSubscriptionRejectedApplier},
 * this is non-destructive - the process-instance's catch element is still there, only suspended, so
 * the subscription itself must survive. Resets it to the same shape as after a normal correlation
 * ({@link MutableMessageSubscriptionState#updateToCorrelatedState}: {@code correlating} cleared,
 * record otherwise untouched) and releases the per-process correlation lock on the message so it is
 * eligible to correlate to this process again, either via a resume re-poll or a future publish.
 */
public final class MessageSubscriptionCorrelationDeferredApplier
    implements TypedEventApplier<MessageSubscriptionIntent, MessageSubscriptionRecord> {

  private final MutableMessageState messageState;
  private final MutableMessageSubscriptionState subscriptionState;

  public MessageSubscriptionCorrelationDeferredApplier(
      final MutableMessageState messageState,
      final MutableMessageSubscriptionState subscriptionState) {
    this.messageState = messageState;
    this.subscriptionState = subscriptionState;
  }

  @Override
  public void applyState(final long key, final MessageSubscriptionRecord value) {
    final var subscription =
        subscriptionState.get(value.getElementInstanceKey(), value.getMessageNameBuffer());
    subscriptionState.updateToCorrelatedState(subscription);
    messageState.removeMessageCorrelation(value.getMessageKey(), value.getBpmnProcessIdBuffer());
  }
}
