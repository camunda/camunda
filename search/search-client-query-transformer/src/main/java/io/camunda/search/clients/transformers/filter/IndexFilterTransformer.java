/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.matchAll;
import static io.camunda.search.clients.query.SearchQueryBuilders.matchNone;
import static io.camunda.search.clients.query.SearchQueryBuilders.or;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;
import static io.camunda.search.exception.ErrorMessages.ERROR_INDEX_FILTER_TRANSFORMER_AUTH_CHECK_MISSING;
import static io.camunda.search.exception.ErrorMessages.ERROR_INDEX_FILTER_TRANSFORMER_TENANT_CHECK_MISSING;

import io.camunda.search.clients.query.SearchMatchAllQuery;
import io.camunda.search.clients.query.SearchMatchNoneQuery;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.query.SearchQueryBuilders;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.filter.FilterBase;
import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.condition.AnyOfAuthorizationCondition;
import io.camunda.security.auth.condition.SingleAuthorizationCondition;
import io.camunda.security.reader.AuthorizationCheck;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.security.reader.TenantCheck;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class IndexFilterTransformer<T extends FilterBase> implements FilterTransformer<T> {

  private static final Logger LOG = LoggerFactory.getLogger(IndexFilterTransformer.class);

  private final IndexDescriptor indexDescriptor;

  public IndexFilterTransformer(final IndexDescriptor indexDescriptor) {
    this.indexDescriptor = indexDescriptor;
  }

  public SearchQuery toSearchQuery(
      final T filter, final ResourceAccessChecks resourceAccessChecks) {
    final var filterSearchQuery =
        Optional.ofNullable(toSearchQuery(filter)).orElseGet(SearchQueryBuilders::matchAll);

    if (resourceAccessChecks == null) {
      return rewriteSearchQueries(List.of(filterSearchQuery));
    }

    final var authorizationSearchQuery =
        Optional.of(resourceAccessChecks.authorizationCheck())
            .map(check -> applyAuthorizationChecks(check, resourceAccessChecks.authentication()))
            .orElseThrow(
                () -> {
                  final var message =
                      ERROR_INDEX_FILTER_TRANSFORMER_AUTH_CHECK_MISSING.formatted(
                          getClass().getSimpleName());
                  LOG.error(message);
                  return new CamundaSearchException(message);
                });

    final var tenantSearchQuery =
        Optional.of(resourceAccessChecks.tenantCheck())
            .map(this::applyTenantChecks)
            .orElseThrow(
                () -> {
                  final var message =
                      ERROR_INDEX_FILTER_TRANSFORMER_TENANT_CHECK_MISSING.formatted(
                          getClass().getSimpleName());
                  LOG.error(message);
                  return new CamundaSearchException(message);
                });

    return rewriteSearchQueries(
        List.of(filterSearchQuery, authorizationSearchQuery, tenantSearchQuery));
  }

  private SearchQuery applyAuthorizationChecks(
      final AuthorizationCheck authorizationCheck, final CamundaAuthentication authentication) {
    if (!authorizationCheck.enabled()) {
      return matchAll();
    }

    final var condition = authorizationCheck.authorizationCondition();
    return switch (condition) {
      case final SingleAuthorizationCondition single ->
          applyAuthorizationCheck(single.authorization(), authentication);
      case final AnyOfAuthorizationCondition anyOf ->
          applyAnyOfAuthorizationCheck(anyOf, authentication);
      default ->
          throw new IllegalStateException(
              "Unsupported AuthorizationCondition type: " + condition.getClass().getSimpleName());
    };
  }

  private SearchQuery applyAuthorizationCheck(
      final Authorization<?> authorization, final CamundaAuthentication authentication) {
    if (authorization.hasAnyResourceIds()) {
      return toAuthorizationCheckSearchQuery(authorization);
    }

    if (authorization.hasAnyResourcePropertyNames()) {
      if (authentication == null) {
        LOG.warn(
            "Property-based authorization requires authentication context, "
                + "but none was provided; returning a match-none query.");
        return matchNone();
      }

      return toAuthorizationCheckSearchQueryByProperties(authorization, authentication);
    }

    return matchNone();
  }

  private SearchQuery applyAnyOfAuthorizationCheck(
      final AnyOfAuthorizationCondition anyOf, final CamundaAuthentication authentication) {
    final var queries =
        anyOf.authorizations().stream()
            .map(auth -> applyAuthorizationCheck(auth, authentication))
            .filter(Objects::nonNull)
            .filter(q -> !(q.queryOption() instanceof SearchMatchNoneQuery))
            .toList();

    if (queries.isEmpty()) {
      return matchNone();
    }

    return or(queries);
  }

  private SearchQuery applyTenantChecks(final TenantCheck tenantCheck) {
    final var field = Optional.of(indexDescriptor).flatMap(IndexDescriptor::getTenantIdField);

    if (field.isEmpty() || !tenantCheck.enabled()) {
      return matchAll();
    }

    return toTenantCheckSearchQuery(tenantCheck, field.get());
  }

  protected SearchQuery toTenantCheckSearchQuery(
      final TenantCheck tenantCheck, final String field) {
    return Optional.of(tenantCheck)
        .filter(TenantCheck::hasAnyTenantAccess)
        .map(TenantCheck::tenantIds)
        .map(t -> stringTerms(field, t))
        .orElse(matchNone());
  }

  private SearchQuery rewriteSearchQueries(final List<SearchQuery> queries) {
    final var anyNonMatchQuery =
        queries.stream()
            .filter(Objects::nonNull)
            .map(SearchQuery::queryOption)
            .anyMatch(SearchMatchNoneQuery.class::isInstance);

    if (anyNonMatchQuery) {
      return matchNone();
    }

    final var filteredQueries =
        queries.stream()
            .filter(Objects::nonNull)
            .filter(q -> !(q.queryOption() instanceof SearchMatchAllQuery))
            .toList();

    return and(filteredQueries);
  }

  protected abstract SearchQuery toAuthorizationCheckSearchQuery(Authorization<?> authorization);

  protected SearchQuery toAuthorizationCheckSearchQueryByProperties(
      Authorization<?> authorization, final CamundaAuthentication authentication) {
    LOG.warn(
        "Authorization check by resource properties is not supported by '{}'; returning match-none query.",
        this.getClass().getSimpleName());
    return matchNone();
  }

  @Override
  public IndexDescriptor getIndex() {
    return indexDescriptor;
  }
}
