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

public final class HeaderValueFilterTransformer implements FilterTransformer<HeaderValueFilter> {

  @Override
  public SearchQuery toSearchQuery(final HeaderValueFilter value) {
    // Default field prefix for customHeaders
    return toSearchQuery(value, "customHeaders");
  }

  public SearchQuery toSearchQuery(final HeaderValueFilter value, final String fieldPrefix) {
    // Build the full field path (e.g., "customHeaders.headerName")
    final var headerFieldName = fieldPrefix + "." + value.name();

    if (value.valueOperations().isEmpty()) {
      // If no value operations, just check for existence
      return exists(headerFieldName);
    }

    // Apply string operations to the header value field
    final var queries = new ArrayList<SearchQuery>();
    queries.addAll(variableOperations(headerFieldName, value.valueOperations()));
    return and(queries);
  }
}
