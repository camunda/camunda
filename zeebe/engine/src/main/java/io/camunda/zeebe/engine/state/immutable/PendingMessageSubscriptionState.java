/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.engine.state.immutable.MessageSubscriptionState.MessageSubscriptionVisitor;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;

public interface PendingMessageSubscriptionState {

  /**
   * Visits all pending message subscriptions where a command hasn't been sent out since a given
   * deadline. The visitor is called for each subscription, from the oldest to the newest.
   */
  void visitPending(final long deadline, final MessageSubscriptionVisitor visitor);

  /**
   * Should be called when a pending subscription is sent out. This is used to keep track of the
   * last time a command was sent out for a subscription. Freshly sent-out subscriptions are not
   * visited by {@link #visitPending(long, MessageSubscriptionVisitor)}.
   */
  void onSent(
      final long elementInstance,
      final String messageName,
      final String tenantId,
      final long timestampMs);

  default void onSent(final MessageSubscriptionRecord subscription, final long timestampMs) {
    onSent(
        subscription.getElementInstanceKey(),
        subscription.getMessageName(),
        subscription.getTenantId(),
        timestampMs);
  }
}
