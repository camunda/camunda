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
import io.camunda.analytics.state.mutable.MutableProjectionState;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Opens an incident: stamps {@code hadIncident} on the element instance's row <em>and</em> its
 * owning process instance's row, then materializes an incident row with its create time and type so
 * a later resolution can read the duration and type off the row. Flagging the process instance —
 * not only the element that raised the incident — is what lets the process-level end fact (and the
 * no-incident cohort read off it) reflect that the instance had an incident.
 *
 * <p>An incident whose element (or owning process instance) row is missing is an ordering-invariant
 * breach (ADR 0007): each miss is counted via {@link ProjectionMetrics#foldRowMissing()} and logged
 * with the record's Zeebe coordinates, naming which of the two keys was missing.
 */
public final class IncidentCreatedApplier implements EventApplier {

  private static final Logger LOG = LoggerFactory.getLogger(IncidentCreatedApplier.class);

  private final MutableProjectionState state;
  private final ProjectionMetrics metrics;

  public IncidentCreatedApplier(
      final MutableProjectionState state, final ProjectionMetrics metrics) {
    this.state = state;
    this.metrics = metrics;
  }

  @Override
  public void apply(final SourceRecord source) {
    final IncidentRecordValue value = (IncidentRecordValue) source.record().getValue();
    final long elementInstanceKey = value.getElementInstanceKey();
    // Flag both the element that raised the incident and the owning process instance (its own
    // element row), so the process-level end fact carries hadIncident. A process-level incident has
    // the same key for both; marking twice is idempotent.
    if (!state.markIncident(elementInstanceKey)) {
      metrics.foldRowMissing();
      LOG.warn(
          "Fold met a missing row: no element row for the incident's element instance key {} "
              + "(zeebe partition {}, position {})",
          elementInstanceKey,
          source.record().getPartitionId(),
          source.record().getPosition());
    }
    if (!state.markIncident(value.getProcessInstanceKey())) {
      metrics.foldRowMissing();
      LOG.warn(
          "Fold met a missing row: no element row for the incident's process instance key {} "
              + "(zeebe partition {}, position {})",
          value.getProcessInstanceKey(),
          source.record().getPartitionId(),
          source.record().getPosition());
    }
    state.openIncident(elementInstanceKey, source.record().getTimestamp(), errorType(value));
  }

  /** The incident's error type, defaulting to {@code UNKNOWN} when the record carries none. */
  static String errorType(final IncidentRecordValue value) {
    return value.getErrorType() == null ? "UNKNOWN" : value.getErrorType().name();
  }
}
