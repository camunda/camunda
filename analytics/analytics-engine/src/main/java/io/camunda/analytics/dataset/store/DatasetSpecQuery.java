/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.dataset.store;

import io.camunda.analytics.dataset.DatasetKind;
import io.camunda.analytics.fact.FactType;

/**
 * A backend-neutral query over stored dataset specs — the control plane's search request. Each set
 * field is an equality filter conjoined with the others; a {@code null} field is unconstrained, so
 * {@link #all()} matches everything. Each backend transforms this into its native form (a SQL
 * {@code WHERE} for RDBMS, a term query for ES/OS), the same way OC transforms a neutral {@code
 * SearchQuery} per backend. Kept minimal on purpose; it grows as the control plane needs richer
 * filters.
 *
 * @param name the exact dataset name, or {@code null} for any
 * @param sourceFact the source fact type, or {@code null} for any
 * @param kind the dataset kind (aggregated/projected), or {@code null} for any
 */
public record DatasetSpecQuery(String name, FactType sourceFact, DatasetKind kind) {

  public static DatasetSpecQuery all() {
    return new DatasetSpecQuery(null, null, null);
  }

  public static DatasetSpecQuery byName(final String name) {
    return new DatasetSpecQuery(name, null, null);
  }

  public static DatasetSpecQuery byKind(final DatasetKind kind) {
    return new DatasetSpecQuery(null, null, kind);
  }
}
