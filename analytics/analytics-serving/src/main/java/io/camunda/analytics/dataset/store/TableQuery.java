/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.dataset.store;

import io.camunda.analytics.dataset.FilterPredicate;
import java.util.List;

/**
 * A read request against a {@link io.camunda.analytics.dataset.DatasetKind#TABLE table}, in domain
 * terms: the equality {@code filters} to apply (each on a declared column) and the maximum number
 * of rows to return. The minimal neutral model for this iteration — it grows alongside {@link
 * ReportQuery} (ordering, paging, richer predicates) as the read layer does.
 */
public record TableQuery(List<FilterPredicate> filters, int limit) {

  public TableQuery {
    filters = List.copyOf(filters == null ? List.of() : filters);
    if (limit <= 0) {
      throw new IllegalArgumentException("table query limit must be positive, was " + limit);
    }
  }
}
