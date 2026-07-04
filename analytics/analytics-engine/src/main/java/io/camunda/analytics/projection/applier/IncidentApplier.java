/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.projection.applier;

import io.camunda.analytics.state.mutable.MutableProjectionState;

/**
 * Folds {@code INCIDENT} records into the projection: {@code CREATED} stamps {@code hadIncident} on
 * the element instance's row and opens an incident row at its create time; {@code RESOLVED}
 * finalizes the incident row with its resolve time so the resolution duration can be read off it.
 * The sole mutator of the incident projection.
 */
public final class IncidentApplier {

  public void created(
      final long elementInstanceKey,
      final long createMs,
      final String errorType,
      final MutableProjectionState state) {
    state.markIncident(elementInstanceKey);
    state.openIncident(elementInstanceKey, createMs, errorType);
  }

  public void resolved(
      final long elementInstanceKey, final long resolveMs, final MutableProjectionState state) {
    state.resolveIncident(elementInstanceKey, resolveMs);
  }

  public void evict(final long elementInstanceKey, final MutableProjectionState state) {
    state.evictIncident(elementInstanceKey);
  }
}
