/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.exception.ErrorMessages.ERROR_RESOURCE_ACCESS_CHECKS_AUTHORIZATION_NOT_DEFINED;
import static io.camunda.search.exception.ErrorMessages.ERROR_RESOURCE_ACCESS_CHECKS_TENANT_NOT_DEFINED;

import io.camunda.search.clients.query.SearchMatchAllQuery;
import io.camunda.search.clients.query.SearchMatchNoneQuery;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.query.SearchQueryBuilders;
import io.camunda.search.clients.security.ResourceAccessChecks;
import io.camunda.search.clients.security.ResourceAccessChecks.AuthorizationChecks;
import io.camunda.search.clients.security.ResourceAccessChecks.TenantChecks;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.exception.ErrorMessages;
import io.camunda.search.filter.FilterBase;
import io.camunda.security.auth.Authorization;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public abstract class IndexFilterTransformer<T extends FilterBase> implements FilterTransformer<T> {

  protected final IndexDescriptor indexDescriptor;

  public IndexFilterTransformer(final IndexDescriptor indexDescriptor) {
    this.indexDescriptor = indexDescriptor;
  }

  @Override
  public IndexDescriptor getIndex() {
    return indexDescriptor;
  }

  public SearchQuery toSearchQuery(
      final T filter, final ResourceAccessChecks resourceAccessChecks) {
    final var filterSearchQuery = toSearchQuery(filter);

    if (resourceAccessChecks == null) {
      throw new CamundaSearchException(ErrorMessages.ERROR_RESOURCE_ACCESS_CHECKS_NOT_DEFINED);
    }

    final var authorizationSearchQuery =
        Optional.of(resourceAccessChecks)
            .map(ResourceAccessChecks::authorizationChecks)
            .map(this::applyAuthorizationChecks)
            .orElseThrow(
                () ->
                    new CamundaSearchException(
                        ERROR_RESOURCE_ACCESS_CHECKS_AUTHORIZATION_NOT_DEFINED));

    final var tenantSearchQuery =
        Optional.of(resourceAccessChecks)
            .map(ResourceAccessChecks::tenantChecks)
            .map(this::applyTenantChecks)
            .orElseThrow(
                () -> new CamundaSearchException(ERROR_RESOURCE_ACCESS_CHECKS_TENANT_NOT_DEFINED));

    final var anyNonMatchQuery =
        Stream.of(authorizationSearchQuery, tenantSearchQuery)
            .anyMatch(s -> s.queryOption() instanceof SearchMatchNoneQuery);

    if (anyNonMatchQuery) {
      return SearchQueryBuilders.matchNone();
    }

    final var filteredQueries =
        Stream.of(filterSearchQuery, authorizationSearchQuery, tenantSearchQuery)
            .filter(q -> q != null && !(q.queryOption() instanceof SearchMatchAllQuery))
            .toList();

    if (filteredQueries.isEmpty()) {
      return null;
    } else if (filteredQueries.size() == 1) {
      return filteredQueries.getFirst();
    } else {
      return and(filteredQueries);
    }
  }

  protected SearchQuery applyAuthorizationChecks(final AuthorizationChecks authorizationCheck) {
    if (!authorizationCheck.required()) {
      return SearchQueryBuilders.matchAll();
    }

    final var authorization = authorizationCheck.authorization();
    if (authorization == null) {
      throw new CamundaSearchException(ERROR_RESOURCE_ACCESS_CHECKS_AUTHORIZATION_NOT_DEFINED);
    }

    final var resourceIds = authorization.resourceIds();
    if (indexDescriptor.getTenantIdField().isPresent()
        && (resourceIds == null || resourceIds.isEmpty())) {
      return SearchQueryBuilders.matchNone();
    }

    return toAuthorizationCheckSearchQuery(authorization);
  }

  protected SearchQuery applyTenantChecks(final TenantChecks tenantCheck) {
    if (!tenantCheck.required()) {
      return SearchQueryBuilders.matchAll();
    }

    final var tenantIds = tenantCheck.tenantIds();
    if (tenantIds == null || tenantIds.isEmpty()) {
      return SearchQueryBuilders.matchNone();
    }

    return toTenantCheckSearchQuery(tenantIds);
  }

  protected abstract SearchQuery toAuthorizationCheckSearchQuery(
      final Authorization<?> authorization);

  protected abstract SearchQuery toTenantCheckSearchQuery(final List<String> tenantIds);
}
