/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.search.clients.CamundaSearchClient;
import io.camunda.service.entities.UserTaskEntity;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.search.query.SearchQueryBuilders;
import io.camunda.service.search.query.SearchQueryResult;
import io.camunda.service.search.query.UserTaskQuery;
import io.camunda.service.search.query.UserTaskQuery.Builder;
import io.camunda.service.security.auth.Authentication;
import io.camunda.service.transformers.ServiceTransformers;
import io.camunda.util.ObjectBuilder;
import java.util.function.Function;

public final class UserTaskServices
    extends SearchQueryService<UserTaskServices, UserTaskQuery, UserTaskEntity> {

  public UserTaskServices(final CamundaSearchClient dataStoreClient) {
    this(dataStoreClient, null, null);
  }

  public UserTaskServices(
      final CamundaSearchClient searchClient,
      final ServiceTransformers transformers,
      final Authentication authentication) {
    super(searchClient, transformers, authentication);
  }

  @Override
  public UserTaskServices withAuthentication(final Authentication authentication) {
    return new UserTaskServices(searchClient, transformers, authentication);
  }

  @Override
  public SearchQueryResult<UserTaskEntity> search(final UserTaskQuery query) {
    return executor.search(query, UserTaskEntity.class);
  }

  public SearchQueryResult<UserTaskEntity> search(
      final Function<Builder, ObjectBuilder<UserTaskQuery>> fn) {
    return search(SearchQueryBuilders.userTaskSearchQuery(fn));
  }
}
