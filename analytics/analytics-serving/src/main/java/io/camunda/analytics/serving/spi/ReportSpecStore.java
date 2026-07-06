/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.serving.spi;

import io.camunda.analytics.report.ReportDefinition;
import java.util.List;
import java.util.Optional;

/**
 * The backend-neutral control-plane store of report specs — the source of truth for the saved
 * multi-dataset reports (ADR 0006), mirroring {@link DatasetSpecStore}. A report is a durable spec
 * in the metadata plane, not webapp storage, so it stays portable across RDBMS / ES / OS. A backend
 * module implements this over its store (a scalar-searchable row / a spec document); callers depend
 * on the interface and the neutral {@link ReportDefinition}, never on SQL or a client.
 *
 * <p>{@code create} allocates a stable {@code reportId} the same way datasets allocate {@code
 * cubeId} — the next id derived from the existing rows — since reports, unlike datasets, have no
 * registry to mint ids ahead of persistence. {@code read}/{@code search}/{@code delete} address a
 * report by that id.
 */
public interface ReportSpecStore {

  /**
   * Persists one report, allocating and assigning its {@code reportId} (the next id after the
   * highest stored one; any id on the passed {@code report} is ignored).
   */
  void create(ReportDefinition report);

  /** Reads a single report by its {@code reportId}, or empty if absent. */
  Optional<ReportDefinition> read(long reportId);

  /** Returns every stored report. */
  List<ReportDefinition> search();

  /** Removes the report with {@code reportId}; a no-op if it does not exist. */
  void delete(long reportId);
}
