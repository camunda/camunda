/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.state.appliers;

import io.zeebe.engine.state.TypedEventApplier;
import io.zeebe.engine.state.message.MessageSubscription;
import io.zeebe.engine.state.mutable.MutableMessageSubscriptionState;
import io.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.zeebe.protocol.record.intent.MessageSubscriptionIntent;

public final class MessageSubscriptionCreatedApplier
    implements TypedEventApplier<MessageSubscriptionIntent, MessageSubscriptionRecord> {

  private final MutableMessageSubscriptionState subscriptionState;

  public MessageSubscriptionCreatedApplier(
      final MutableMessageSubscriptionState subscriptionState) {
    this.subscriptionState = subscriptionState;
  }

  @Override
  public void applyState(final long key, final MessageSubscriptionRecord record) {

    final var subscription =
        new MessageSubscription(
            record.getWorkflowInstanceKey(),
            record.getElementInstanceKey(),
            record.getBpmnProcessIdBuffer(),
            record.getMessageNameBuffer(),
            record.getCorrelationKeyBuffer(),
            record.shouldCloseOnCorrelate());

    // TODO (saig0): reuse the subscription record in the state (#6180)
    subscriptionState.put(subscription);
  }
}
