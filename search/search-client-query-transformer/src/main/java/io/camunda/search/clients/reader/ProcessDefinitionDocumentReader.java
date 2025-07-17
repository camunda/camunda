/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import io.camunda.search.aggregation.result.ProcessDefinitionLatestVersionAggregationResult;
import io.camunda.search.clients.SearchClientBasedQueryExecutor;
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.query.ProcessDefinitionQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.entities.ProcessEntity;

public class ProcessDefinitionDocumentReader extends DocumentBasedReader
    implements ProcessDefinitionReader {

  public ProcessDefinitionDocumentReader(
      final SearchClientBasedQueryExecutor executor, final IndexDescriptor indexDescriptor) {
    super(executor, indexDescriptor);
  }

  @Override
  public ProcessDefinitionEntity getByKey(
      final long key, final ResourceAccessChecks resourceAccessChecks) {
    return getSearchExecutor()
        .getByQuery(
            ProcessDefinitionQuery.of(
                b -> b.filter(f -> f.processDefinitionKeys(key)).singleResult()),
            ProcessEntity.class);
  }

  @Override
  public SearchQueryResult<ProcessDefinitionEntity> search(
      final ProcessDefinitionQuery query, final ResourceAccessChecks resourceAccessChecks) {

    if (query.filter().isLatestVersion()) {
      return searchWithAggregation(query, resourceAccessChecks);
    } else {
      return getSearchExecutor().search(query, ProcessEntity.class, resourceAccessChecks);
    }
  }

  protected SearchQueryResult<ProcessDefinitionEntity> searchWithAggregation(
      final ProcessDefinitionQuery query, final ResourceAccessChecks resourceAccessChecks) {
    final var aggResult =
        getSearchExecutor()
            .aggregate(
                query, ProcessDefinitionLatestVersionAggregationResult.class, resourceAccessChecks);
    return new SearchQueryResult<>(
        aggResult.items().size(),
        !aggResult.items().isEmpty(),
        aggResult.items(),
        null,
        aggResult.endCursor());
  }
}
