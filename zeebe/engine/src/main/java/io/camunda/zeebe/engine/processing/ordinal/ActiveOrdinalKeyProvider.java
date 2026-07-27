/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.ordinal;

import io.camunda.zeebe.engine.state.immutable.OrdinalActiveState;

public class ActiveOrdinalKeyProvider implements OrdinalKeyProvider {

  private final OrdinalActiveState ordinalActiveState;

  public ActiveOrdinalKeyProvider(final OrdinalActiveState ordinalActiveState) {
    this.ordinalActiveState = ordinalActiveState;
  }

  @Override
  public int getOrdinal(final long rootProcessInstanceKey) {
    return ordinalActiveState.getActiveOrdinalKey();
  }
}
