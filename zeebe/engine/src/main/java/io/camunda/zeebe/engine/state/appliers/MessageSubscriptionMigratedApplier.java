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

public class MessageSubscriptionMigratedApplier
    implements TypedEventApplier<MessageSubscriptionIntent, MessageSubscriptionRecord> {

  private final MutableMessageSubscriptionState subscriptionState;

  public MessageSubscriptionMigratedApplier(
      final MutableMessageSubscriptionState subscriptionState) {
    this.subscriptionState = subscriptionState;
  }

  @Override
  public void applyState(final long key, final MessageSubscriptionRecord record) {
    subscriptionState.update(key, record);
  }
}
