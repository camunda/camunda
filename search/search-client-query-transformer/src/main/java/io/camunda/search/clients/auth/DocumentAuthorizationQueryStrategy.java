/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.auth;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.matchNone;
import static io.camunda.search.page.SearchQueryPage.DEFAULT_SIZE;
import static io.camunda.security.auth.Authorization.WILDCARD;

import io.camunda.search.clients.DocumentBasedSearchClient;
import io.camunda.search.clients.core.RequestBuilders;
import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.clients.transformers.auth.AuthorizationQueryTransformers;
import io.camunda.search.clients.transformers.filter.FilterTransformer;
import io.camunda.search.entities.AuthorizationEntity;
import io.camunda.search.filter.AuthorizationFilter;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.query.SearchQueryBase;
import io.camunda.security.auth.Authentication;
import io.camunda.security.auth.SecurityContext;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.ArrayList;
import java.util.List;

/**
 * Document based datastore (ES/OS) strategy implementation of {@link AuthorizationQueryStrategy}.
 * It applies authorization to a search query by fetching the authorized resources for the
 * authenticated user and creating a new search query with the authorization applied.
 */
public class DocumentAuthorizationQueryStrategy implements AuthorizationQueryStrategy {

  private final DocumentBasedSearchClient searchClient;
  private final ServiceTransformers serviceTransformers;

  public DocumentAuthorizationQueryStrategy(
      final DocumentBasedSearchClient searchClient, final ServiceTransformers serviceTransformers) {
    this.searchClient = searchClient;
    this.serviceTransformers = serviceTransformers;
  }

  @Override
  public SearchQueryRequest applyAuthorizationToQuery(
      final SearchQueryRequest searchQueryRequest,
      final SecurityContext securityContext,
      final Class<? extends SearchQueryBase> queryClass) {
    if (!securityContext.requiresAuthorizationChecks()) {
      return searchQueryRequest;
    }
    // fetch the authorization entities for the authenticated user
    final var resourceType = securityContext.authorization().resourceType();
    final var permissionType = securityContext.authorization().permissionType();
    final var resourceKeys =
        retrieveAuthorizedResources(securityContext.authentication(), resourceType, permissionType);

    if (resourceKeys.contains(WILDCARD)) {
      return searchQueryRequest;
    }

    // create a new search query request with the authorization applied
    final SearchQuery authorizedQuery;
    if (resourceKeys.isEmpty()) {
      authorizedQuery = matchNone();
    } else {
      authorizedQuery =
          and(
              searchQueryRequest.query(),
              AuthorizationQueryTransformers.getTransformer(queryClass)
                  .toSearchQuery(resourceType, permissionType, resourceKeys));
    }
    // create a copy of the original search query request with the authorized query
    return SearchQueryRequest.of(
        r ->
            r.index(searchQueryRequest.index())
                .query(authorizedQuery)
                .sort(searchQueryRequest.sort())
                .searchAfter(searchQueryRequest.searchAfter())
                .from(searchQueryRequest.from())
                .size(searchQueryRequest.size())
                .source(searchQueryRequest.source()));
  }

  private List<String> retrieveAuthorizedResources(
      final Authentication authentication,
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType) {
    final List<Long> ownerKeys = collectOwnerKeys(authentication);
    final var authenticationQuery =
        buildAuthorizationSearchQuery(resourceType, permissionType, ownerKeys);
    final var authorizationEntities =
        searchClient.findAll(authenticationQuery, AuthorizationEntity.class);
    return authorizationEntities.stream()
        .flatMap(
            e ->
                e.permissions().stream()
                    .filter(permission -> permissionType.equals(permission.type()))
                    .flatMap(permission -> permission.resourceIds().stream()))
        .toList();
  }

  private SearchQueryRequest buildAuthorizationSearchQuery(
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType,
      final List<Long> ownerKeys) {
    final FilterTransformer<AuthorizationFilter> authorizationFilterTransformer =
        getAuthorizationFilterTransformer();

    final AuthorizationFilter authorizationFilter =
        FilterBuilders.authorization(
            f ->
                f.resourceType(resourceType.name())
                    .ownerKeys(ownerKeys)
                    .permissionType(permissionType));
    return RequestBuilders.searchRequest(
        b ->
            b.index(authorizationFilterTransformer.toIndices(authorizationFilter))
                .query(authorizationFilterTransformer.toSearchQuery(authorizationFilter))
                .size(DEFAULT_SIZE));
  }

  private List<Long> collectOwnerKeys(final Authentication authentication) {
    final List<Long> ownerKeys = new ArrayList<>();
    if (authentication.authenticatedUserKey() != null) {
      ownerKeys.add(authentication.authenticatedUserKey());
    }
    if (authentication.authenticatedGroupKeys() != null) {
      ownerKeys.addAll(authentication.authenticatedGroupKeys());
    }
    return ownerKeys;
  }

  private FilterTransformer<AuthorizationFilter> getAuthorizationFilterTransformer() {
    return serviceTransformers.getFilterTransformer(AuthorizationFilter.class);
  }
}
