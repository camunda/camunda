/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.state.appliers;

import io.zeebe.engine.state.TypedEventApplier;
import io.zeebe.engine.state.mutable.MutableEventScopeInstanceState;
import io.zeebe.engine.state.mutable.MutableProcessInstanceSubscriptionState;
import io.zeebe.protocol.impl.record.value.message.ProcessInstanceSubscriptionRecord;
import io.zeebe.protocol.record.intent.ProcessInstanceSubscriptionIntent;

public final class ProcessInstanceSubscriptionCorrelatedApplier
    implements TypedEventApplier<
        ProcessInstanceSubscriptionIntent, ProcessInstanceSubscriptionRecord> {

  private final MutableProcessInstanceSubscriptionState subscriptionState;
  private final MutableEventScopeInstanceState eventScopeInstanceState;

  public ProcessInstanceSubscriptionCorrelatedApplier(
      final MutableProcessInstanceSubscriptionState subscriptionState,
      final MutableEventScopeInstanceState eventScopeInstanceState) {
    this.subscriptionState = subscriptionState;
    this.eventScopeInstanceState = eventScopeInstanceState;
  }

  @Override
  public void applyState(final long key, final ProcessInstanceSubscriptionRecord value) {

    if (value.isInterrupting()) {
      subscriptionState.remove(value.getElementInstanceKey(), value.getMessageNameBuffer());
    }

    final var eventScopeKey = value.getElementInstanceKey();
    // use the element instance key as the unique key of the event
    final var eventKey = value.getElementInstanceKey();
    eventScopeInstanceState.triggerEvent(
        eventScopeKey, eventKey, value.getElementIdBuffer(), value.getVariablesBuffer());
  }
}
