/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.query;

import static io.camunda.search.clients.query.SearchQueryBuilders.exists;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.wildcardQuery;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.service.query.filter.FilterOperator;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public abstract class QueryFieldFilterTransformers {
  public static SearchQuery buildStringQuery(final String field, final List<String> value,
      final FilterOperator operator) {
    if (value == null || value.isEmpty()) {
      return null;
    }

    switch (operator) {
      case EQ:  // Equals
        return stringTerms(field, value);

      case LIKE:  // Like (wildcard or pattern matching)
        return wildcardQuery(field, value.get(0));  // Use the first value for wildcard match

      case EXISTS:  // Exists check
          return exists(field);

      case IN:  // In
        return stringTerms(field, value);

      default:
        throw new IllegalArgumentException("Unsupported operator: " + operator);
    }
  }
}
