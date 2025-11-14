/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.nested;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;
import static io.camunda.search.clients.query.SearchQueryBuilders.variableOperations;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.HeaderValueFilter;
import java.util.ArrayList;

/**
 * Transforms {@link HeaderValueFilter} to search queries for nested array format.
 *
 * <p>Custom headers are stored as nested arrays in ES/OS:
 * <pre>[{"name": "department", "value": "engineering"}]</pre>
 *
 * <p>This transformer generates nested queries that match on both name and value fields.
 */
public final class HeaderValueFilterTransformer implements FilterTransformer<HeaderValueFilter> {

  @Override
  public SearchQuery toSearchQuery(final HeaderValueFilter value) {
    // Default field prefix for customHeaders
    return toSearchQuery(value, "customHeaders");
  }

  public SearchQuery toSearchQuery(final HeaderValueFilter value, final String fieldPrefix) {
    final var queries = new ArrayList<SearchQuery>();

    // Match the header name
    queries.add(term(fieldPrefix + ".name", value.name()));

    // Match the header value with operations
    if (!value.valueOperations().isEmpty()) {
      queries.addAll(variableOperations(fieldPrefix + ".value", value.valueOperations()));
    }

    // Wrap in nested query
    return nested(fieldPrefix, and(queries));
  }
}
