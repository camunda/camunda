/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.projection.applier;

import io.camunda.analytics.projection.SourceRecord;
import io.camunda.analytics.state.mutable.MutableProjectionState;

/**
 * Evicts a terminal element row after its completion fact has been derived (evict-after-emit):
 * drops the row and the variables scoped to that element instance.
 */
public final class ElementEvictApplier implements EventApplier {

  private final MutableProjectionState state;

  public ElementEvictApplier(final MutableProjectionState state) {
    this.state = state;
  }

  @Override
  public void apply(final SourceRecord source) {
    final long elementInstanceKey = source.record().getKey();
    state.clearVariables(elementInstanceKey);
    state.evictElement(elementInstanceKey);
  }
}
