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
import static io.camunda.search.clients.query.SearchQueryBuilders.stringOperations;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;
import static io.camunda.search.clients.query.SearchQueryBuilders.variableOperations;
import static java.util.Optional.ofNullable;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.Operation;
import io.camunda.search.filter.UntypedOperation;
import io.camunda.search.filter.VariableFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class VariableFilterTransformer implements FilterTransformer<VariableFilter> {

  @Override
  public SearchQuery toSearchQuery(final VariableFilter filter) {
    final var queries = new ArrayList<SearchQuery>();
    ofNullable(stringOperations("name", filter.nameOperations())).ifPresent(queries::addAll);
    ofNullable(getVariablesQuery(filter.valueOperations())).ifPresent(queries::addAll);
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

  private List<SearchQuery> getVariablesQuery(final List<UntypedOperation> variableFilters) {
    return variableOperations("value", variableFilters);
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
