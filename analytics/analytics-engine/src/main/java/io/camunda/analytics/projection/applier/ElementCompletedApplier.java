/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.projection.applier;

import io.camunda.analytics.projection.SourceRecord;
import io.camunda.analytics.state.ElementStatus;
import io.camunda.analytics.state.mutable.MutableProjectionState;

/**
 * Finalizes an element row {@code {end, status, durationMs}} on a terminal transition. Declared
 * once per terminal status — {@link ElementStatus#COMPLETED} and {@link ElementStatus#TERMINATED} —
 * so the two transitions share this fold and differ only by the status they stamp.
 */
public final class ElementCompletedApplier implements EventApplier {

  private final MutableProjectionState state;
  private final ElementStatus status;

  public ElementCompletedApplier(final MutableProjectionState state, final ElementStatus status) {
    this.state = state;
    this.status = status;
  }

  @Override
  public void apply(final SourceRecord source) {
    state.completeElement(source.record().getKey(), source.record().getTimestamp(), status);
  }
}
