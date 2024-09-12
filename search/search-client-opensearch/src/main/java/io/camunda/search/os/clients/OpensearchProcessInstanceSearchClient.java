/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.os.clients;

import io.camunda.search.SearchClientBasedQueryExecutor;
import io.camunda.search.clients.ProcessInstanceSearchClient;
import io.camunda.search.os.transformers.OpensearchTransformers;
import io.camunda.search.transformers.ServiceTransformers;
import io.camunda.service.entities.ProcessInstanceEntity;
import io.camunda.service.search.query.ProcessInstanceQuery;
import io.camunda.service.search.query.SearchQueryResult;
import io.camunda.service.security.auth.Authentication;
import io.camunda.zeebe.util.Either;
import org.opensearch.client.opensearch.OpenSearchClient;

public final class OpensearchProcessInstanceSearchClient extends OpensearchSearchClient implements
    ProcessInstanceSearchClient {

  public OpensearchProcessInstanceSearchClient(final OpenSearchClient client) {
    this(client, new OpensearchTransformers());
  }

  public OpensearchProcessInstanceSearchClient(
      final OpenSearchClient client, final OpensearchTransformers transformers) {
    super(client, transformers);
  }

  @Override
  public Either<Exception, SearchQueryResult<ProcessInstanceEntity>> searchProcessInstances(
      final ProcessInstanceQuery filter, final Authentication authentication) {
    final var executor = new SearchClientBasedQueryExecutor(this, ServiceTransformers.newInstance(),
        authentication);
    return executor.search(filter, ProcessInstanceEntity.class);
  }

}
