/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.or;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;

import io.camunda.search.clients.core.QueryFieldFilterTransformers;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.filter.VariableFilter;
import io.camunda.search.filter.VariableValueFilter;
import io.camunda.util.advanced.query.filter.FieldFilter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class VariableFilterTransformer implements FilterTransformer<VariableFilter> {

  private final ServiceTransformers transformers;
  private final VariableValueFilterTransformer variableValueFilterTransformer;

  public VariableFilterTransformer(
      final ServiceTransformers transformers,
      final VariableValueFilterTransformer variableValueFilterTransformer) {
    this.transformers = transformers;
    this.variableValueFilterTransformer = variableValueFilterTransformer;
  }

  @Override
  public SearchQuery toSearchQuery(final VariableFilter filter) {
    final var variablesQuery = getVariablesQuery(filter.variableFilters());
    final var scopeKeyQuery = getScopeKeyQuery(filter.scopeKeys());
    final var processInstanceKeyQuery = getProcessInstanceKeyQuery(filter.processInstanceKeys());
    final var variableKeyQuery = getVariableKeyQuery(filter.variableKeys());
    final var tenantIdQuery = getTenantIdQuery(filter.tenantIds());
    final var isTruncatedQuery = getIsTruncatedQuery(filter.isTruncated());

    return and(
        variablesQuery,
        scopeKeyQuery,
        processInstanceKeyQuery,
        variableKeyQuery,
        tenantIdQuery,
        isTruncatedQuery);
  }

  @Override
  public List<String> toIndices(final VariableFilter filter) {
    return Arrays.asList("operate-variable-8.3.0_alias");
  }

  private SearchQuery getVariablesQuery(final List<VariableValueFilter> variableFilters) {
    if (variableFilters != null && !variableFilters.isEmpty()) {
      final var queries =
          variableFilters.stream()
              .map(v -> variableValueFilterTransformer.toSearchQuery(v, "name", "value"))
              .collect(Collectors.toList());
      return or(queries);
    }
    return null;
  }

  private SearchQuery getScopeKeyQuery(final FieldFilter<Object> scopeKey) {
    if (scopeKey != null) {
      final var operator = scopeKey.getOperator();
      final var value = (List<Long>) scopeKey.getValue();
      return QueryFieldFilterTransformers.buildLongQuery("scopeKey", value, operator);
    }
    return null;
  }

  private SearchQuery getProcessInstanceKeyQuery(final FieldFilter<Object> processInstanceKey) {
    if (processInstanceKey != null) {
      final var operator = processInstanceKey.getOperator();
      final var value = (List<Long>) processInstanceKey.getValue();
      return QueryFieldFilterTransformers.buildLongQuery("processInstanceKey", value, operator);
    }
    return null;
  }

  private SearchQuery getVariableKeyQuery(final FieldFilter<Object> variableKey) {
    if (variableKey != null) {
      final var operator = variableKey.getOperator();
      final var value = (List<Long>) variableKey.getValue();
      return QueryFieldFilterTransformers.buildLongQuery("key", value, operator);
    }
    return null;
  }

  private SearchQuery getTenantIdQuery(final FieldFilter<Object> tenantId) {
    if (tenantId != null) {
      final var operator = tenantId.getOperator();
      final var value = (List<String>) tenantId.getValue();
      return QueryFieldFilterTransformers.buildStringQuery("tenantId", value, operator);
    }
    return null;
  }

  private SearchQuery getIsTruncatedQuery(final boolean isTruncated) {
    return term("isPreview", isTruncated);
  }
}
