/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableMessageStartEventSubscriptionState;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartEventSubscriptionRecord;
import io.camunda.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;

public final class MessageStartEventSubscriptionDeletedApplier
    implements TypedEventApplier<
        MessageStartEventSubscriptionIntent, MessageStartEventSubscriptionRecord> {

  private final MutableMessageStartEventSubscriptionState subscriptionState;

  public MessageStartEventSubscriptionDeletedApplier(
      final MutableMessageStartEventSubscriptionState subscriptionState) {
    this.subscriptionState = subscriptionState;
  }

  @Override
  public void applyState(final long key, final MessageStartEventSubscriptionRecord value) {
    final var processDefinitionKey = value.getProcessDefinitionKey();
    subscriptionState.remove(
        processDefinitionKey, value.getMessageNameBuffer(), value.getTenantId());
  }
}
