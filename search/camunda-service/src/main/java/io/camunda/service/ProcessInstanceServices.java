/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.data.clients.DataStoreClient;
import io.camunda.service.auth.Authentication;
import io.camunda.service.entities.ProcessInstanceEntity;
import io.camunda.service.query.SearchQuery;
import io.camunda.service.query.SearchQueryResult;
import io.camunda.service.query.core.SearchQueryService;
import io.camunda.service.query.filter.ProcessInstanceFilter;
import io.camunda.util.DataStoreObjectBuilder;
import java.util.function.Function;

public final class ProcessInstanceServices extends SearchQueryService<ProcessInstanceServices> {

  public ProcessInstanceServices(final DataStoreClient dataStoreClient) {
    this(dataStoreClient, null);
  }

  public ProcessInstanceServices(
      final DataStoreClient dataStoreClient, final Authentication authentication) {
    super(dataStoreClient, authentication);
  }

  @Override
  public ProcessInstanceServices withAuthentication(final Authentication authentication) {
    return new ProcessInstanceServices(dataStoreClient, authentication);
  }

  public SearchQueryResult<ProcessInstanceEntity> search(
      final SearchQuery<ProcessInstanceFilter> query) {
    return executor.search(query, ProcessInstanceEntity.class);
  }

  public SearchQueryResult<ProcessInstanceEntity> search(
      final Function<
              SearchQuery.Builder<ProcessInstanceFilter>,
              DataStoreObjectBuilder<SearchQuery<ProcessInstanceFilter>>>
          fn) {
    return search(SearchQuery.of(fn));
  }
}
