/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import io.camunda.search.clients.DocumentBasedSearchClient;
import io.camunda.search.clients.security.ResourceAccessChecks;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.entities.DecisionInstanceEntity;
import io.camunda.search.query.DecisionInstanceQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;

public class DecisionInstanceDocumentReader extends DocumentBasedReader
    implements DecisionInstanceReader {

  public DecisionInstanceDocumentReader(
      final DocumentBasedSearchClient searchClient,
      final ServiceTransformers transformers,
      final IndexDescriptor indexDescriptor) {
    super(searchClient, transformers, indexDescriptor);
  }

  @Override
  public DecisionInstanceEntity getByKey(
      final String key, final ResourceAccessChecks resourceAccessChecks) {
    return getSearchExecutor()
        .getByQuery(
            DecisionInstanceQuery.of(b -> b.filter(f -> f.decisionInstanceIds(key)).singleResult()),
            io.camunda.webapps.schema.entities.dmn.DecisionInstanceEntity.class,
            resourceAccessChecks);
  }

  @Override
  public SearchQueryResult<DecisionInstanceEntity> search(
      final DecisionInstanceQuery query, final ResourceAccessChecks resourceAccessChecks) {
    return getSearchExecutor()
        .search(
            query,
            io.camunda.webapps.schema.entities.dmn.DecisionInstanceEntity.class,
            resourceAccessChecks);
  }
}
