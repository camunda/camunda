/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import io.camunda.search.aggregation.result.IncidentProcessInstanceStatisticsByDefinitionAggregationResult;
import io.camunda.search.clients.SearchClientBasedQueryExecutor;
import io.camunda.search.entities.IncidentProcessInstanceStatisticsByDefinitionEntity;
import io.camunda.search.query.IncidentProcessInstanceStatisticsByDefinitionQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;

public class IncidentProcessInstanceStatisticsByDefinitionDocumentReader extends DocumentBasedReader
    implements IncidentProcessInstanceStatisticsByDefinitionReader {

  public IncidentProcessInstanceStatisticsByDefinitionDocumentReader(
      final SearchClientBasedQueryExecutor executor, final IndexDescriptor indexDescriptor) {
    super(executor, indexDescriptor);
  }

  @Override
  public SearchQueryResult<IncidentProcessInstanceStatisticsByDefinitionEntity> aggregate(
      final IncidentProcessInstanceStatisticsByDefinitionQuery query,
      final ResourceAccessChecks resourceAccessChecks) {

    final var paginatedResult =
        getSearchExecutor()
            .aggregateWithQueryResult(
                query,
                IncidentProcessInstanceStatisticsByDefinitionAggregationResult.class,
                resourceAccessChecks,
                IncidentProcessInstanceStatisticsByDefinitionAggregationResult::items);

    return new SearchQueryResult<>(
        paginatedResult.total(),
        paginatedResult.hasMoreTotalItems(),
        paginatedResult.items(),
        paginatedResult.startCursor(),
        paginatedResult.endCursor());
  }
}
