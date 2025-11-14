/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.exists;
import static io.camunda.search.clients.query.SearchQueryBuilders.variableOperations;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.HeaderValueFilter;
import java.util.ArrayList;

/**
 * Transforms {@link HeaderValueFilter} to search queries for flattened field type.
 *
 * <p>Custom headers are stored as flattened fields in ES/OS, allowing queries like:
 *
 * <pre>customHeaders.department: "engineering"</pre>
 */
public final class HeaderValueFilterTransformer implements FilterTransformer<HeaderValueFilter> {

  @Override
  public SearchQuery toSearchQuery(final HeaderValueFilter value) {
    // Default field prefix for customHeaders
    return toSearchQuery(value, "customHeaders");
  }

  public SearchQuery toSearchQuery(final HeaderValueFilter value, final String fieldPrefix) {
    // Build the full field path (e.g., "customHeaders.department")
    final var headerFieldName = fieldPrefix + "." + value.name();

    if (value.valueOperations().isEmpty()) {
      // If no value operations, just check for existence
      return exists(headerFieldName);
    }

    // Apply variable operations to the header value field (handles untyped operations)
    final var queries = new ArrayList<SearchQuery>();
    queries.addAll(variableOperations(headerFieldName, value.valueOperations()));
    return and(queries);
  }
}
