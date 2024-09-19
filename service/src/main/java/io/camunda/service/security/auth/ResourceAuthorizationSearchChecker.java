/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.security.auth;

import static io.camunda.search.clients.query.SearchQueryBuilders.matchAll;
import static io.camunda.search.clients.query.SearchQueryBuilders.matchNone;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;
import static io.camunda.service.search.query.SearchQueryBuilders.authorizationSearchQuery;

import io.camunda.search.clients.CamundaSearchClient;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.query.SearchQueryBuilders;
import io.camunda.service.entities.AuthorizationEntity;
import io.camunda.service.search.core.SearchClientBasedQueryExecutor;
import io.camunda.service.transformers.ServiceTransformers;
import java.util.Arrays;
import java.util.List;

public class ResourceAuthorizationSearchChecker {

  private final SearchClientBasedQueryExecutor searchExecutor;

  public ResourceAuthorizationSearchChecker(
      final CamundaSearchClient camundaSearchClient,
      final ServiceTransformers serviceTransformers) {
    searchExecutor =
        new SearchClientBasedQueryExecutor(camundaSearchClient, serviceTransformers, null);
  }

  /**
   * Creates a search query that checks the user's authorization for accessing the resource entity.
   *
   * <p>This method looks for fields in the resource entity class that are annotated with
   * {@code @ResourceAuthorizationKey}. For each such field, it generates a query that ensures the
   * authenticated user has permission to access the resource.
   */
  public SearchQuery getResourceAuthorizationCheck(
      final Authentication authentication, final Class<?> resourceEntityClass) {
    return Arrays.stream(resourceEntityClass.getFields())
        .filter(field -> field.isAnnotationPresent(ResourceAuthorizationKey.class))
        .map(
            field ->
                createResourceAuthorizationQuery(
                    field.getAnnotation(ResourceAuthorizationKey.class),
                    field.getName(),
                    authentication.authenticatedUserId()))
        .reduce(matchAll(), SearchQueryBuilders::and);
  }

  private SearchQuery createResourceAuthorizationQuery(
      final ResourceAuthorizationKey resourceAuthorizationKey,
      final String fieldName,
      final String userId) {
    final String resourceType = resourceAuthorizationKey.forResourceType();
    final List<AuthorizationEntity> authorizations = getAuthorizationEntities(userId, resourceType);
    final var authorizedResourceKeys =
        authorizations.stream()
            .map(AuthorizationEntity::value)
            .map(AuthorizationEntity.Authorization::resourceKey)
            .toList();
    if (authorizedResourceKeys.isEmpty()) {
      return matchNone();
    }
    if (authorizedResourceKeys.contains("*")) {
      return matchAll();
    }
    return stringTerms(fieldName, authorizedResourceKeys);
  }

  private List<AuthorizationEntity> getAuthorizationEntities(
      final String userId, final String resourceType) {
    final var searchQuery =
        authorizationSearchQuery(s -> s.filter(f -> f.ownerKey(userId).resourceType(resourceType)));
    return searchExecutor.search(searchQuery, AuthorizationEntity.class).items();
  }
}
