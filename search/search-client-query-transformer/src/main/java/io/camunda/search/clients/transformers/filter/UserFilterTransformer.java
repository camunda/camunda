/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringOperations;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;
import static io.camunda.webapps.schema.descriptors.index.UserIndex.EMAIL;
import static io.camunda.webapps.schema.descriptors.index.UserIndex.NAME;
import static io.camunda.webapps.schema.descriptors.index.UserIndex.USERNAME;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.UserFilter;
import io.camunda.security.core.auth.RequiredAuthorization;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.util.ArrayList;
import java.util.List;

public class UserFilterTransformer extends IndexFilterTransformer<UserFilter>
    implements OrFilterTransformer<UserFilter> {

  public UserFilterTransformer(final IndexDescriptor indexDescriptor) {
    super(indexDescriptor);
  }

  @Override
  public SearchQuery toSearchQuery(final UserFilter filter) {
    final var queries = new ArrayList<>(toSearchQueryFields(filter));

    toOrClause(filter).ifPresent(queries::add);

    return and(queries);
  }

  @Override
  public List<SearchQuery> toSearchQueryFields(final UserFilter filter) {
    final var queries = new ArrayList<SearchQuery>();
    queries.addAll(stringOperations(USERNAME, filter.usernameOperations()));
    queries.addAll(stringOperations(NAME, filter.nameOperations()));
    queries.addAll(stringOperations(EMAIL, filter.emailOperations()));
    return queries;
  }

  @Override
  protected SearchQuery toAuthorizationCheckSearchQuery(
      final RequiredAuthorization<?> authorization) {
    return stringTerms(USERNAME, authorization.resourceIds());
  }
}
