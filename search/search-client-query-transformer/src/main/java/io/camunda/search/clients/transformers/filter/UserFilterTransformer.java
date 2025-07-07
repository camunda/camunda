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
import static io.camunda.search.clients.query.SearchQueryBuilders.stringOperations;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;
import static io.camunda.webapps.schema.descriptors.index.UserIndex.EMAIL;
import static io.camunda.webapps.schema.descriptors.index.UserIndex.KEY;
import static io.camunda.webapps.schema.descriptors.index.UserIndex.NAME;
import static io.camunda.webapps.schema.descriptors.index.UserIndex.USERNAME;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.UserFilter;
import io.camunda.security.auth.Authorization;
import io.camunda.security.resource.ResourceAccessResult;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.util.ArrayList;
import java.util.List;

public class UserFilterTransformer extends IndexFilterTransformer<UserFilter> {

  public UserFilterTransformer(final IndexDescriptor indexDescriptor) {
    this(indexDescriptor, null);
  }

  public UserFilterTransformer(
      final IndexDescriptor indexDescriptor, final ResourceAccessResult resourceAccessManager) {
    super(indexDescriptor, resourceAccessManager);
  }

  @Override
  public UserFilterTransformer withResourceAccessFilter(
      final ResourceAccessResult resourceAccessResult) {
    return new UserFilterTransformer(indexDescriptor, resourceAccessResult);
  }

  @Override
  protected SearchQuery toAuthorizationSearchQuery(final Authorization authorization) {
    final var resourceIds = authorization.resourceIds();
    return stringTerms(USERNAME, resourceIds);
  }

  @Override
  protected SearchQuery toTenantSearchQuery(final List<String> tenantIds) {
    // Users are not tenant-owned => no tenant check required
    return matchAll();
  }

  @Override
  public SearchQuery toSearchQuery(final UserFilter filter) {
    final var queries = new ArrayList<SearchQuery>();
    if (filter.key() != null) {
      queries.add(term(KEY, filter.key()));
    }
    queries.addAll(stringOperations(USERNAME, filter.usernameOperations()));
    queries.addAll(stringOperations(NAME, filter.nameOperations()));
    queries.addAll(stringOperations(EMAIL, filter.emailOperations()));
    return and(queries);
  }
}
