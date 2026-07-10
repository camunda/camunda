/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.projection.applier;

import io.camunda.analytics.projection.ProjectionMetrics;
import io.camunda.analytics.projection.SourceRecord;
import io.camunda.analytics.state.ElementStatus;
import io.camunda.analytics.state.mutable.MutableProjectionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Finalizes an element row {@code {end, status, durationMs}} on a terminal transition. Declared
 * once per terminal status — {@link ElementStatus#COMPLETED} and {@link ElementStatus#TERMINATED} —
 * so the two transitions share this fold and differ only by the status they stamp.
 *
 * <p>A terminal transition whose activation row is missing is an ordering-invariant breach (ADR
 * 0007), not expected traffic: it is counted via {@link ProjectionMetrics#foldRowMissing()} and
 * logged with the record's Zeebe coordinates for the forensic trail.
 */
public final class ElementCompletedApplier implements EventApplier {

  private static final Logger LOG = LoggerFactory.getLogger(ElementCompletedApplier.class);

  private final MutableProjectionState state;
  private final ElementStatus status;
  private final ProjectionMetrics metrics;

  public ElementCompletedApplier(
      final MutableProjectionState state,
      final ElementStatus status,
      final ProjectionMetrics metrics) {
    this.state = state;
    this.status = status;
    this.metrics = metrics;
  }

  @Override
  public void apply(final SourceRecord source) {
    final long elementInstanceKey = source.record().getKey();
    if (!state.completeElement(elementInstanceKey, source.record().getTimestamp(), status)) {
      metrics.foldRowMissing();
      LOG.warn(
          "Fold met a missing row: no element row for key {} while applying {} "
              + "(zeebe partition {}, position {})",
          elementInstanceKey,
          status,
          source.record().getPartitionId(),
          source.record().getPosition());
    }
  }
}
