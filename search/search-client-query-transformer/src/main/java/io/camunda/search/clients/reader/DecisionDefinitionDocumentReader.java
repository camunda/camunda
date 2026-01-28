/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import io.camunda.search.aggregation.result.DecisionDefinitionLatestVersionAggregationResult;
import io.camunda.search.clients.SearchClientBasedQueryExecutor;
import io.camunda.search.entities.DecisionDefinitionEntity;
import io.camunda.search.query.DecisionDefinitionQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;

public class DecisionDefinitionDocumentReader extends DocumentBasedReader
    implements DecisionDefinitionReader {

  public DecisionDefinitionDocumentReader(
      final SearchClientBasedQueryExecutor executor, final IndexDescriptor indexDescriptor) {
    super(executor, indexDescriptor);
  }

  @Override
  public DecisionDefinitionEntity getByKey(
      final long key, final ResourceAccessChecks resourceAccessChecks) {
    return getSearchExecutor()
        .getById(
            String.valueOf(key),
            io.camunda.webapps.schema.entities.dmn.definition.DecisionDefinitionEntity.class,
            indexDescriptor.getFullQualifiedName());
  }

  @Override
  public SearchQueryResult<DecisionDefinitionEntity> search(
      final DecisionDefinitionQuery query, final ResourceAccessChecks resourceAccessChecks) {

    if (query.filter().isLatestVersion()) {
      return searchWithAggregation(query, resourceAccessChecks);
    }

    return getSearchExecutor()
        .search(
            query,
            io.camunda.webapps.schema.entities.dmn.definition.DecisionDefinitionEntity.class,
            resourceAccessChecks);
  }

  protected SearchQueryResult<DecisionDefinitionEntity> searchWithAggregation(
      final DecisionDefinitionQuery query, final ResourceAccessChecks resourceAccessChecks) {
    final var aggResult =
        getSearchExecutor()
            .aggregate(
                query,
                DecisionDefinitionLatestVersionAggregationResult.class,
                resourceAccessChecks);

    return new SearchQueryResult<>(
        aggResult.items().size(),
        !aggResult.items().isEmpty(),
        aggResult.items(),
        null,
        aggResult.endCursor());
  }
}
