/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.search.clients.CamundaSearchClient;
import io.camunda.service.entities.ProcessInstanceEntity;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.search.query.ProcessInstanceQuery;
import io.camunda.service.search.query.SearchQueryBuilders;
import io.camunda.service.search.query.SearchQueryResult;
import io.camunda.service.security.auth.Authentication;
import io.camunda.service.transformers.ServiceTransformers;
import io.camunda.util.ObjectBuilder;
import java.util.function.Function;

public final class  ProcessInstanceServices
    extends SearchQueryService<
        ProcessInstanceServices, ProcessInstanceQuery, ProcessInstanceEntity> {

  public ProcessInstanceServices(final CamundaSearchClient dataStoreClient) {
    this(dataStoreClient, null, null);
  }

  public ProcessInstanceServices(
      final CamundaSearchClient searchClient,
      final ServiceTransformers transformers,
      final Authentication authentication) {
    super(searchClient, transformers, authentication);
  }

  @Override
  public ProcessInstanceServices withAuthentication(final Authentication authentication) {
    return new ProcessInstanceServices(searchClient, transformers, authentication);
  }

  @Override
  public SearchQueryResult<ProcessInstanceEntity> search(final ProcessInstanceQuery query) {
    return executor.search(query, ProcessInstanceEntity.class);
  }

  public SearchQueryResult<ProcessInstanceEntity> search(
      final Function<ProcessInstanceQuery.Builder, ObjectBuilder<ProcessInstanceQuery>> fn) {
    return search(SearchQueryBuilders.processInstanceSearchQuery(fn));
  }
}
