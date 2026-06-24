/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableMessageSubscriptionState;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;

public final class MessageSubscriptionCorrelatedApplier
    implements TypedEventApplier<MessageSubscriptionIntent, MessageSubscriptionRecord> {

  private final MutableMessageSubscriptionState messageSubscriptionState;

  public MessageSubscriptionCorrelatedApplier(
      final MutableMessageSubscriptionState messageSubscriptionState) {
    this.messageSubscriptionState = messageSubscriptionState;
  }

  @Override
  public void applyState(final long key, final MessageSubscriptionRecord value) {
    // TODO (saig0): the record doesn't contain the sent time but it's required for cleaning (#6364)
    // - workaround: load the subscription from the state instead of using the record directly
    final var subscription =
        messageSubscriptionState.get(value.getElementInstanceKey(), value.getMessageNameBuffer());

    if (value.isInterrupting()) {
      messageSubscriptionState.remove(subscription);
    } else {
      messageSubscriptionState.updateToCorrelatedState(subscription);
    }
  }
}
