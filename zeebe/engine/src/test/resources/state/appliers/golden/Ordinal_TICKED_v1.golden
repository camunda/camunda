/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableOrdinalState;
import io.camunda.zeebe.protocol.impl.record.value.ordinal.OrdinalRecord;
import io.camunda.zeebe.protocol.record.intent.OrdinalIntent;

final class OrdinalTickedApplier implements TypedEventApplier<OrdinalIntent, OrdinalRecord> {

  private final MutableOrdinalState ordinalState;

  OrdinalTickedApplier(final MutableOrdinalState ordinalState) {
    this.ordinalState = ordinalState;
  }

  @Override
  public void applyState(final long key, final OrdinalRecord value) {
    ordinalState.incrementOrdinal(value.getDateTime());
  }
}
