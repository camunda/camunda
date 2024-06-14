/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.exists;
import static io.camunda.search.clients.query.SearchQueryBuilders.hasChildQuery;
import static io.camunda.search.clients.query.SearchQueryBuilders.longTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.not;
import static io.camunda.search.clients.query.SearchQueryBuilders.or;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.service.search.filter.VariableFilter;
import io.camunda.service.search.filter.VariableValueFilter;
import io.camunda.service.transformers.ServiceTransformers;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class VariableFilterTransformer implements FilterTransformer<VariableFilter> {

  private final ServiceTransformers transformers;

  public VariableFilterTransformer(final ServiceTransformers transformers) {
    this.transformers = transformers;
  }

  @Override
  public SearchQuery toSearchQuery(final VariableFilter filter) {
    final var variablesQuery = getVariablesQuery(filter.variableFilters());
    final var scopeKeyQuery = getScopeKeyQuery(filter.scopeKeys());
    final var processInstanceKeyQuery = getProcessInstanceKeyQuery(filter.processInstanceKeys());

    return and(variablesQuery,
        scopeKeyQuery,
        processInstanceKeyQuery
    );
  }

  @Override
  public List<String> toIndices(final VariableFilter filter) {
      return Arrays.asList("operate-variables-8.3.0_");
  }

  private SearchQuery getVariablesQuery(final List<VariableValueFilter> variableFilters) {
    if (variableFilters != null && !variableFilters.isEmpty()) {
      final var transformer = getVariableValueFilterTransformer();
      final var queries =
          variableFilters.stream()
              .map(transformer::apply)
              .map((q) -> hasChildQuery("variable", q))
              .collect(Collectors.toList());
      return and(queries);
    }
    return null;
  }

  private SearchQuery getScopeKeyQuery(final List<Long> scopeKey) {
    return longTerms("scopeKeys", scopeKey);
  }

  private SearchQuery getProcessInstanceKeyQuery(final List<Long> processInstanceKey) {
    return longTerms("processInstanceKey", processInstanceKey);
  }



  private FilterTransformer<VariableValueFilter> getVariableValueFilterTransformer() {
    return transformers.getFilterTransformer(VariableValueFilter.class);
  }
}
