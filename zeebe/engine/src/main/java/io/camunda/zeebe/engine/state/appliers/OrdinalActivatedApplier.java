/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableOrdinalActiveState;
import io.camunda.zeebe.protocol.impl.record.value.ordinal.OrdinalRecord;
import io.camunda.zeebe.protocol.record.intent.OrdinalIntent;

public final class OrdinalActivatedApplier
    implements TypedEventApplier<OrdinalIntent, OrdinalRecord> {

  private final MutableOrdinalActiveState ordinalState;

  public OrdinalActivatedApplier(final MutableOrdinalActiveState ordinalState) {
    this.ordinalState = ordinalState;
  }

  @Override
  public void applyState(final long key, final OrdinalRecord value) {
    // TODO: @yohanfernando >> implement proper activate command
    ordinalState.activate(value.getOrdinalKey());
  }
}
