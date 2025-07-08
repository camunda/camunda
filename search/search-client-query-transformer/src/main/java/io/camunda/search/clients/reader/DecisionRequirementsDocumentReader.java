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
import io.camunda.search.entities.DecisionRequirementsEntity;
import io.camunda.search.query.DecisionRequirementsQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.result.DecisionRequirementsQueryResultConfig;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;

public class DecisionRequirementsDocumentReader extends DocumentBasedReader
    implements DecisionRequirementsReader {

  public DecisionRequirementsDocumentReader(
      final DocumentBasedSearchClient searchClient,
      final ServiceTransformers transformers,
      final IndexDescriptor indexDescriptor) {
    super(searchClient, transformers, indexDescriptor);
  }

  @Override
  public DecisionRequirementsEntity getByKey(
      final String key, final boolean includeXml, final ResourceAccessChecks resourceAccessChecks) {
    return getSearchExecutor()
        .getById(
            key,
            io.camunda.webapps.schema.entities.dmn.definition.DecisionRequirementsEntity.class,
            indexDescriptor.getFullQualifiedName(),
            resourceAccessChecks,
            DecisionRequirementsQueryResultConfig.of(b -> b.includeXml(includeXml)));
  }

  @Override
  public DecisionRequirementsEntity getByKey(
      final String key, final ResourceAccessChecks resourceAccessChecks) {
    return getByKey(key, false, resourceAccessChecks);
  }

  @Override
  public SearchQueryResult<DecisionRequirementsEntity> search(
      final DecisionRequirementsQuery query, final ResourceAccessChecks resourceAccessChecks) {
    return getSearchExecutor()
        .search(
            query,
            io.camunda.webapps.schema.entities.dmn.definition.DecisionRequirementsEntity.class,
            resourceAccessChecks);
  }
}
