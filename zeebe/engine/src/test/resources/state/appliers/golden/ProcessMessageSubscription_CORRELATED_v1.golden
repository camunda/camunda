/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableProcessMessageSubscriptionState;
import io.camunda.zeebe.protocol.impl.record.value.message.ProcessMessageSubscriptionRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;

public final class ProcessMessageSubscriptionCorrelatedApplier
    implements TypedEventApplier<
        ProcessMessageSubscriptionIntent, ProcessMessageSubscriptionRecord> {

  private final MutableProcessMessageSubscriptionState subscriptionState;

  public ProcessMessageSubscriptionCorrelatedApplier(
      final MutableProcessMessageSubscriptionState subscriptionState) {
    this.subscriptionState = subscriptionState;
  }

  @Override
  public void applyState(final long key, final ProcessMessageSubscriptionRecord value) {
    final var eventScopeKey = value.getElementInstanceKey();

    if (value.isInterrupting()) {
      subscriptionState.remove(eventScopeKey, value.getMessageNameBuffer(), value.getTenantId());
    } else {
      // if the message subscription is created and a matching message is buffered then it writes a
      // process message subscription CORRELATE instead of a CREATE command
      subscriptionState.updateToOpenedState(value);
    }
  }
}
