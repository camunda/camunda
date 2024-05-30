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
import io.camunda.service.query.SearchQueryService;
import io.camunda.service.query.search.ProcessInstanceQuery;
import io.camunda.service.query.search.SearchQueryBuilders;
import io.camunda.service.query.search.SearchQueryResult;
import io.camunda.util.DataStoreObjectBuilder;
import java.util.function.Function;

public final class ProcessInstanceServices
    extends SearchQueryService<
        ProcessInstanceServices, ProcessInstanceQuery, ProcessInstanceEntity> {

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

  @Override
  public SearchQueryResult<ProcessInstanceEntity> search(final ProcessInstanceQuery query) {
    return executor.search(query, ProcessInstanceEntity.class);
  }

  public SearchQueryResult<ProcessInstanceEntity> search(
      final Function<ProcessInstanceQuery.Builder, DataStoreObjectBuilder<ProcessInstanceQuery>>
          fn) {
    return search(SearchQueryBuilders.processInstanceSearchQuery(fn));
  }
}
