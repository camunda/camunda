/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import static io.camunda.search.aggregation.ProcessDefinitionInstanceStatisticsAggregation.AGGREGATION_FIELD_KEY;
import static io.camunda.search.aggregation.ProcessDefinitionInstanceStatisticsAggregation.AGGREGATION_FIELD_PROCESS_DEFINITION_ID;

import io.camunda.search.aggregation.result.ProcessDefinitionInstanceStatisticsAggregationResult;
import io.camunda.search.clients.SearchClientBasedQueryExecutor;
import io.camunda.search.clients.reader.utils.IncidentErrorHashCodeNormalizer;
import io.camunda.search.entities.ProcessDefinitionInstanceStatisticsEntity;
import io.camunda.search.query.ProcessDefinitionInstanceStatisticsQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;

public class ProcessDefinitionInstanceStatisticsDocumentReader extends DocumentBasedReader
    implements ProcessDefinitionInstanceStatisticsReader {

  private final IncidentErrorHashCodeNormalizer normalizer;

  public ProcessDefinitionInstanceStatisticsDocumentReader(
      final SearchClientBasedQueryExecutor executor,
      final IndexDescriptor indexDescriptor,
      final IncidentErrorHashCodeNormalizer normalizer) {
    super(executor, indexDescriptor);
    this.normalizer = normalizer;
  }

  @Override
  public SearchQueryResult<ProcessDefinitionInstanceStatisticsEntity> aggregate(
      final ProcessDefinitionInstanceStatisticsQuery query,
      final ResourceAccessChecks resourceAccessChecks) {

    final var filter =
        normalizer.normalizeAndValidateProcessInstanceFilter(query.filter(), resourceAccessChecks);
    if (filter.isEmpty()) {
      return SearchQueryResult.empty();
    }

    // Update the query to use the normalized filter
    final var normalizedQuery =
        new ProcessDefinitionInstanceStatisticsQuery(filter.get(), query.sort(), query.page());

    // Convert sorting field if needed
    final var updatedQuery =
        normalizedQuery.withConvertedSortingField(
            AGGREGATION_FIELD_PROCESS_DEFINITION_ID, AGGREGATION_FIELD_KEY);

    // Run a single paginated query; total count is provided by the cardinality aggregation
    final var paginatedResult =
        getSearchExecutor()
            .aggregateWithQueryResult(
                updatedQuery,
                ProcessDefinitionInstanceStatisticsAggregationResult.class,
                resourceAccessChecks,
                ProcessDefinitionInstanceStatisticsAggregationResult::items);

    // Return paginated items and total from the single query result
    return new SearchQueryResult<>(
        paginatedResult.total(),
        paginatedResult.hasMoreTotalItems(),
        paginatedResult.items(),
        paginatedResult.startCursor(),
        paginatedResult.endCursor());
  }
}
