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
import static io.camunda.search.clients.query.SearchQueryBuilders.stringOperations;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;
import static io.camunda.search.clients.query.SearchQueryBuilders.variableOperations;
import static java.util.Optional.ofNullable;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.ClusterVariableFilter;
import io.camunda.search.filter.Operation;
import io.camunda.search.filter.UntypedOperation;
import io.camunda.security.auth.Authorization;
import io.camunda.security.reader.TenantCheck;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.index.ClusterVariableIndex;
import io.camunda.webapps.schema.entities.clustervariable.ClusterVariableScope;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public final class ClusterVariableFilterTransformer
    extends IndexFilterTransformer<ClusterVariableFilter> {

  public ClusterVariableFilterTransformer(final IndexDescriptor indexDescriptor) {
    super(indexDescriptor);
  }

  @Override
  public SearchQuery toSearchQuery(final ClusterVariableFilter filter) {
    final var queries = new ArrayList<SearchQuery>();
    queries.addAll(getNamesQuery(filter.nameOperations()));
    queries.addAll(getValuesQuery(filter.valueOperations()));
    queries.addAll(getScopeQuery(filter.scopeOperations()));
    queries.addAll(getTenantQuery(filter.tenantIdOperations()));
    ofNullable(getIsTruncatedQuery(filter.isTruncated())).ifPresent(queries::add);
    return and(queries);
  }

  @Override
  protected SearchQuery toTenantCheckSearchQuery(
      final TenantCheck tenantCheck, final String field) {
    final var tenantCheckQuery =
        Optional.of(tenantCheck)
            .map(TenantCheck::tenantIds)
            .filter(t -> !t.isEmpty())
            .map(t -> stringTerms(field, t))
            .orElse(null);

    final var matchGlobalQuery =
        term(ClusterVariableIndex.SCOPE, ClusterVariableScope.GLOBAL.name());

    return or(matchGlobalQuery, tenantCheckQuery);
  }

  @Override
  protected SearchQuery toAuthorizationCheckSearchQuery(final Authorization<?> authorization) {
    return stringTerms(ClusterVariableIndex.NAME, authorization.resourceIds());
  }

  private Collection<SearchQuery> getNamesQuery(final List<Operation<String>> operations) {
    return stringOperations(ClusterVariableIndex.NAME, operations);
  }

  private Collection<SearchQuery> getTenantQuery(final List<Operation<String>> operations) {
    return stringOperations(ClusterVariableIndex.TENANT_ID, operations);
  }

  private Collection<SearchQuery> getScopeQuery(final List<Operation<String>> operations) {
    return stringOperations(ClusterVariableIndex.SCOPE, operations);
  }

  private List<SearchQuery> getValuesQuery(final List<UntypedOperation> variableFilters) {
    return variableOperations(ClusterVariableIndex.VALUE, variableFilters);
  }

  private SearchQuery getIsTruncatedQuery(final Boolean isTruncated) {
    if (isTruncated == null) {
      return null;
    }
    return term(ClusterVariableIndex.IS_PREVIEW, isTruncated);
  }
}
