/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.ordinal;

import io.camunda.zeebe.engine.state.immutable.OrdinalState;

// TODO: @yohanfernando >> probably need a better name for this
public class StateBasedOrdinalKeyProvider implements OrdinalKeyProvider {

  private final OrdinalState ordinalState;

  public StateBasedOrdinalKeyProvider(final OrdinalState ordinalState) {
    this.ordinalState = ordinalState;
  }

  @Override
  public int getOrdinal(final long rootProcessInstanceKey) {
    return ordinalState.getActiveOrdinalKey();
  }
}
