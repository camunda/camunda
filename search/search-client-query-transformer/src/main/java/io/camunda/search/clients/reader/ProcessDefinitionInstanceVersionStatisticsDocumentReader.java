/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import static io.camunda.search.aggregation.ProcessDefinitionInstanceVersionStatisticsAggregation.AGGREGATION_FIELD_KEY;
import static io.camunda.search.aggregation.ProcessDefinitionInstanceVersionStatisticsAggregation.AGGREGATION_FIELD_PROCESS_DEFINITION_ID;

import io.camunda.search.aggregation.result.ProcessDefinitionInstanceVersionStatisticsAggregationResult;
import io.camunda.search.clients.SearchClientBasedQueryExecutor;
import io.camunda.search.entities.ProcessDefinitionInstanceVersionStatisticsEntity;
import io.camunda.search.query.ProcessDefinitionInstanceVersionStatisticsQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;

public class ProcessDefinitionInstanceVersionStatisticsDocumentReader extends DocumentBasedReader
    implements ProcessDefinitionInstanceVersionStatisticsReader {

  public ProcessDefinitionInstanceVersionStatisticsDocumentReader(
      final SearchClientBasedQueryExecutor executor, final IndexDescriptor indexDescriptor) {
    super(executor, indexDescriptor);
  }

  @Override
  public SearchQueryResult<ProcessDefinitionInstanceVersionStatisticsEntity> aggregate(
      final ProcessDefinitionInstanceVersionStatisticsQuery query,
      final ResourceAccessChecks resourceAccessChecks) {

    // Convert sorting field if needed
    final var updatedQuery =
        query.withConvertedSortingField(
            AGGREGATION_FIELD_PROCESS_DEFINITION_ID, AGGREGATION_FIELD_KEY);

    // Run a single paginated query; total count is provided by the cardinality aggregation
    final var paginatedResult =
        getSearchExecutor()
            .aggregateWithQueryResult(
                updatedQuery,
                ProcessDefinitionInstanceVersionStatisticsAggregationResult.class,
                resourceAccessChecks,
                ProcessDefinitionInstanceVersionStatisticsAggregationResult::items);

    // Return paginated items and total from the single query result
    return new SearchQueryResult<>(
        paginatedResult.total(),
        paginatedResult.hasMoreTotalItems(),
        paginatedResult.items(),
        paginatedResult.startCursor(),
        paginatedResult.endCursor());
  }
}
