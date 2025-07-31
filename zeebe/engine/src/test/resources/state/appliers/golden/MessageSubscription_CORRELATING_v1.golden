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

public final class MessageSubscriptionCorrelatingApplier
    implements TypedEventApplier<MessageSubscriptionIntent, MessageSubscriptionRecord> {

  private final MutableMessageSubscriptionState messageSubscriptionState;
  private final MutableMessageState messageState;

  public MessageSubscriptionCorrelatingApplier(
      final MutableMessageSubscriptionState messageSubscriptionState,
      final MutableMessageState messageState) {
    this.messageSubscriptionState = messageSubscriptionState;
    this.messageState = messageState;
  }

  @Override
  public void applyState(final long key, final MessageSubscriptionRecord value) {
    messageSubscriptionState.updateToCorrelatingState(value);

    // avoid correlating this message to one instance of this process again
    messageState.putMessageCorrelation(value.getMessageKey(), value.getBpmnProcessIdBuffer());
  }
}
