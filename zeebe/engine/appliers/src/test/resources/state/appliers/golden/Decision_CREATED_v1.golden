/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableDecisionState;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRecord;
import io.camunda.zeebe.protocol.record.intent.DecisionIntent;

public final class DecisionCreatedV1Applier
    implements TypedEventApplier<DecisionIntent, DecisionRecord> {

  private final MutableDecisionState decisionState;

  public DecisionCreatedV1Applier(final MutableDecisionState decisionState) {
    this.decisionState = decisionState;
  }

  @Override
  public void applyState(final long key, final DecisionRecord value) {
    decisionState.storeDecisionRecord(value);
  }
}
