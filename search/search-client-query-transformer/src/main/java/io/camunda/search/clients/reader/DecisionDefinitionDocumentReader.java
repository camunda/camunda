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
import io.camunda.search.entities.DecisionDefinitionEntity;
import io.camunda.search.query.DecisionDefinitionQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;

public class DecisionDefinitionDocumentReader extends DocumentBasedReader
    implements DecisionDefinitionReader {

  public DecisionDefinitionDocumentReader(
      final DocumentBasedSearchClient searchClient,
      final ServiceTransformers transformers,
      final IndexDescriptor indexDescriptor) {
    super(searchClient, transformers, indexDescriptor);
  }

  @Override
  public DecisionDefinitionEntity getByKey(
      final String key, final ResourceAccessChecks resourceAccessChecks) {
    return getSearchExecutor()
        .getById(
            key,
            io.camunda.webapps.schema.entities.dmn.definition.DecisionDefinitionEntity.class,
            indexDescriptor.getFullQualifiedName(),
            resourceAccessChecks);
  }

  @Override
  public SearchQueryResult<DecisionDefinitionEntity> search(
      final DecisionDefinitionQuery query, final ResourceAccessChecks resourceAccessChecks) {
    return getSearchExecutor()
        .search(
            query,
            io.camunda.webapps.schema.entities.dmn.definition.DecisionDefinitionEntity.class,
            resourceAccessChecks);
  }
}
