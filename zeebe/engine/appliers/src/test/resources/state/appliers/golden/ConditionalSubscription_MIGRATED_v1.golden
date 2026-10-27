/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableConditionalSubscriptionState;
import io.camunda.zeebe.protocol.impl.record.value.conditional.ConditionalSubscriptionRecord;
import io.camunda.zeebe.protocol.record.intent.ConditionalSubscriptionIntent;

public final class ConditionalSubscriptionMigratedApplier
    implements TypedEventApplier<ConditionalSubscriptionIntent, ConditionalSubscriptionRecord> {
  private final MutableConditionalSubscriptionState conditionalSubscriptionState;

  public ConditionalSubscriptionMigratedApplier(
      final MutableConditionalSubscriptionState conditionalSubscriptionState) {
    this.conditionalSubscriptionState = conditionalSubscriptionState;
  }

  @Override
  public void applyState(final long key, final ConditionalSubscriptionRecord value) {
    conditionalSubscriptionState.migrate(key, value);
  }
}
