/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.query;

import static io.camunda.search.clients.query.SearchQueryBuilders.exists;
import static io.camunda.search.clients.query.SearchQueryBuilders.gt;
import static io.camunda.search.clients.query.SearchQueryBuilders.gte;
import static io.camunda.search.clients.query.SearchQueryBuilders.gteLte;
import static io.camunda.search.clients.query.SearchQueryBuilders.longTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.lt;
import static io.camunda.search.clients.query.SearchQueryBuilders.lte;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.wildcardQuery;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.service.query.filter.FilterOperator;
import java.util.List;

public abstract class QueryFieldFilterTransformers {
  public static SearchQuery buildStringQuery(final String field, final List<String> value,
      final FilterOperator operator) {
    if (value == null || value.isEmpty()) {
      return null;
    }

    return switch (operator) {
      case EQ ->  // Equals
          stringTerms(field, value);
      case LIKE ->  // Like (wildcard or pattern matching)
          wildcardQuery(field, value.getFirst());  // Use the first value for wildcard match
      case EXISTS ->  // Exists check
          exists(field);
      case IN ->  // In
          stringTerms(field, value);
      default -> throw new IllegalArgumentException("Unsupported operator: " + operator);
    };
  }

  public static SearchQuery buildLongQuery(final String field, final List<Long> value,
      final FilterOperator operator) {
    if (value == null || value.isEmpty()) {
      return null;
    }
    return switch (operator) {
      case EQ ->  // Equals
          longTerms(field, value);
      case GTE ->  // Greater than or equal to
          gte(field, value);
      case GT ->  // Greater than
          gt(field, value);
      case LTE ->  // Less than or equal to
          lte(field, value);
      case LT ->  // Less than
          lt(field, value);
      case IN ->
          longTerms(field, value);
      case EXISTS ->  // Exists check
          exists(field);
      default -> throw new IllegalArgumentException("Unsupported operator: " + operator);
    };
  }
}
