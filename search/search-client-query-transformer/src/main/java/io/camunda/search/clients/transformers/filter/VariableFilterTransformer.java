/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.longOperations;
import static io.camunda.search.clients.query.SearchQueryBuilders.or;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;
import static io.camunda.search.clients.query.SearchQueryBuilders.variableOperations;
import static java.util.Optional.ofNullable;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.query.SearchQueryBuilders;
import io.camunda.search.filter.Operation;
import io.camunda.search.filter.UntypedOperation;
import io.camunda.search.filter.VariableFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class VariableFilterTransformer implements FilterTransformer<VariableFilter> {

  public static final String VAR_NAME = "name";
  public static final String VAR_VALUE = "value";

  @Override
  public SearchQuery toSearchQuery(final VariableFilter filter) {
    final var queries = new ArrayList<SearchQuery>();
    ofNullable(getVariablesQuery(filter.variableOperations())).ifPresent(queries::add);
    ofNullable(getScopeKeyQuery(filter.scopeKeyOperations())).ifPresent(queries::addAll);
    ofNullable(getProcessInstanceKeyQuery(filter.processInstanceKeyOperations()))
        .ifPresent(queries::addAll);
    ofNullable(getVariableKeyQuery(filter.variableKeyOperations())).ifPresent(queries::addAll);
    ofNullable(getTenantIdQuery(filter.tenantIds())).ifPresent(queries::add);
    ofNullable(getIsTruncatedQuery(filter.isTruncated())).ifPresent(queries::add);
    return and(queries);
  }

  @Override
  public List<String> toIndices(final VariableFilter filter) {
    return Arrays.asList("operate-variable-8.3.0_alias");
  }

  private SearchQuery getVariablesQuery(final Map<String, List<UntypedOperation>> variableFilters) {
    return or(
        variableFilters.entrySet().stream()
            .map(entry -> variableOperations(VAR_NAME, VAR_VALUE, entry.getKey(), entry.getValue()))
            .map(SearchQueryBuilders::and)
            .collect(Collectors.toList()));
  }

  private List<SearchQuery> getScopeKeyQuery(final List<Operation<Long>> scopeKey) {
    return longOperations("scopeKey", scopeKey);
  }

  private List<SearchQuery> getProcessInstanceKeyQuery(
      final List<Operation<Long>> processInstanceKey) {
    return longOperations("processInstanceKey", processInstanceKey);
  }

  private List<SearchQuery> getVariableKeyQuery(final List<Operation<Long>> variableKeys) {
    return longOperations("key", variableKeys);
  }

  private SearchQuery getTenantIdQuery(final List<String> tenant) {
    return stringTerms("tenantId", tenant);
  }

  private SearchQuery getIsTruncatedQuery(final Boolean isTruncated) {
    if (isTruncated == null) {
      return null;
    }
    return term("isPreview", isTruncated);
  }
}
