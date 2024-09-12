/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.clients;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import io.camunda.search.SearchClientBasedQueryExecutor;
import io.camunda.search.clients.ProcessInstanceSearchClient;
import io.camunda.search.es.transformers.ElasticsearchTransformers;
import io.camunda.search.transformers.ServiceTransformers;
import io.camunda.service.entities.ProcessInstanceEntity;
import io.camunda.service.search.query.ProcessInstanceQuery;
import io.camunda.service.search.query.SearchQueryResult;
import io.camunda.service.security.auth.Authentication;
import io.camunda.zeebe.util.Either;

public final class ElasticsearchProcessInstanceSearchClient extends
    ElasticsearchSearchClient implements ProcessInstanceSearchClient {

  public ElasticsearchProcessInstanceSearchClient(final ElasticsearchClient client) {
    super(client, new ElasticsearchTransformers());
  }

  @Override
  public Either<Exception, SearchQueryResult<ProcessInstanceEntity>> searchProcessInstances(
      final ProcessInstanceQuery filter, final Authentication authentication) {
    final var executor = new SearchClientBasedQueryExecutor(this, ServiceTransformers.newInstance(),
        authentication);
    return executor.search(filter, ProcessInstanceEntity.class);
  }

}
