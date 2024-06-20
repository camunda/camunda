/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.search.clients.CamundaSearchClient;
import io.camunda.service.entities.ProcessDefinitionEntity;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.search.query.ProcessDefinitionQuery;
import io.camunda.service.search.query.ProcessDefinitionQuery.Builder;
import io.camunda.service.search.query.SearchQueryBuilders;
import io.camunda.service.search.query.SearchQueryResult;
import io.camunda.service.security.auth.Authentication;
import io.camunda.service.transformers.ServiceTransformers;
import io.camunda.util.ObjectBuilder;
import java.util.function.Function;

public final class ProcessDefinitionServices
    extends SearchQueryService<
        ProcessDefinitionServices, ProcessDefinitionQuery, ProcessDefinitionEntity> {

  public ProcessDefinitionServices(final CamundaSearchClient dataStoreClient) {
    this(dataStoreClient, null, null);
  }

  public ProcessDefinitionServices(
      final CamundaSearchClient searchClient,
      final ServiceTransformers transformers,
      final Authentication authentication) {
    super(searchClient, transformers, authentication);
  }

  @Override
  public ProcessDefinitionServices withAuthentication(final Authentication authentication) {
    return new ProcessDefinitionServices(searchClient, transformers, authentication);
  }

  @Override
  public SearchQueryResult<ProcessDefinitionEntity> search(final ProcessDefinitionQuery query) {
    return executor.search(query, ProcessDefinitionEntity.class);
  }

  public SearchQueryResult<ProcessDefinitionEntity> search(
      final Function<Builder, ObjectBuilder<ProcessDefinitionQuery>> fn) {
    return search(SearchQueryBuilders.processDefinitionSearchQuery(fn));
  }
}
