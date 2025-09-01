/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import static io.camunda.search.aggregation.ProcessDefinitionProcessInstanceStatisticsAggregation.AGGREGATION_FIELD_KEY;
import static io.camunda.search.aggregation.ProcessDefinitionProcessInstanceStatisticsAggregation.AGGREGATION_FIELD_PROCESS_DEFINITION_ID;

import io.camunda.search.aggregation.result.ProcessDefinitionProcessInstanceStatisticsAggregationResult;
import io.camunda.search.clients.SearchClientBasedQueryExecutor;
import io.camunda.search.entities.ProcessDefinitionProcessInstanceStatisticsEntity;
import io.camunda.search.query.ProcessDefinitionProcessInstanceStatisticsQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;

public class ProcessDefinitionProcessInstanceStatisticsDocumentReader extends DocumentBasedReader
    implements ProcessDefinitionProcessInstanceStatisticsReader {

  public ProcessDefinitionProcessInstanceStatisticsDocumentReader(
      final SearchClientBasedQueryExecutor executor, final IndexDescriptor indexDescriptor) {
    super(executor, indexDescriptor);
  }

  @Override
  public SearchQueryResult<ProcessDefinitionProcessInstanceStatisticsEntity> aggregate(
      final ProcessDefinitionProcessInstanceStatisticsQuery query,
      final ResourceAccessChecks resourceAccessChecks) {
    // Convert sorting field if needed
    final var updatedQuery =
        query.withConvertedSortingField(
            AGGREGATION_FIELD_PROCESS_DEFINITION_ID, AGGREGATION_FIELD_KEY);

    // Run unlimited query to get total item count
    final var unlimitedQuery = updatedQuery.withUnlimitedPage();
    final var unlimitedResult =
        getSearchExecutor()
            .aggregateWithQueryResult(
                unlimitedQuery,
                ProcessDefinitionProcessInstanceStatisticsAggregationResult.class,
                resourceAccessChecks,
                ProcessDefinitionProcessInstanceStatisticsAggregationResult::items);

    // Run paginated query to get paginated items
    final var paginatedResult =
        getSearchExecutor()
            .aggregateWithQueryResult(
                updatedQuery,
                ProcessDefinitionProcessInstanceStatisticsAggregationResult.class,
                resourceAccessChecks,
                ProcessDefinitionProcessInstanceStatisticsAggregationResult::items);

    // Return paginated items with total from unlimited query
    return new SearchQueryResult<>(
        unlimitedResult.total(),
        unlimitedResult.hasMoreTotalItems(),
        paginatedResult.items(),
        paginatedResult.startCursor(),
        paginatedResult.endCursor());
  }
}
