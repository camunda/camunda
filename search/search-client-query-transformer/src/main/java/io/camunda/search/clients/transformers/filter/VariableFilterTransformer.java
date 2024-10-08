/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.longTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.or;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.filter.VariableFilter;
import io.camunda.search.filter.VariableValueFilter;
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

  private SearchQuery getScopeKeyQuery(final List<Long> scopeKey) {
    return longTerms("scopeKey", scopeKey);
  }

  private SearchQuery getProcessInstanceKeyQuery(final List<Long> processInstanceKey) {
    return longTerms("processInstanceKey", processInstanceKey);
  }

  private SearchQuery getVariableKeyQuery(final List<Long> variableKeys) {
    return longTerms("key", variableKeys);
  }

  private SearchQuery getTenantIdQuery(final List<String> tenant) {
    return stringTerms("tenantId", tenant);
  }

  private SearchQuery getIsTruncatedQuery(final boolean isTruncated) {
    return term("isPreview", isTruncated);
  }
}
