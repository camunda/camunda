/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.dataset.store;

import io.camunda.analytics.dataset.CompiledTable;
import io.camunda.analytics.dataset.FilterPredicate;
import java.util.List;

/**
 * The backend-neutral <b>fetch spec</b> for a {@link io.camunda.analytics.dataset.DatasetKind#TABLE
 * table} that the {@link TableQueryExecutor} produces and the {@link DatasetQueryClient} executes:
 * fetch up to {@code limit} rows of {@code table} that satisfy the equality {@code filters} on
 * declared columns. No windows, tiers, or aggregation — a table is served as its stored rows.
 */
public record TableFetch(CompiledTable table, List<FilterPredicate> filters, int limit) {

  public TableFetch {
    filters = List.copyOf(filters);
  }
}
