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
import io.camunda.service.query.search.ProcessInstanceSearchQuery;
import io.camunda.service.query.search.SearchQueryResult;

public final class ProcessInstanceServices extends SearchQueryService<ProcessInstanceServices, ProcessInstanceSearchQuery, ProcessInstanceEntity> {

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
  public SearchQueryResult<ProcessInstanceEntity> search(final ProcessInstanceSearchQuery query) {
    return executor.search(query, ProcessInstanceEntity.class);
  }

}
