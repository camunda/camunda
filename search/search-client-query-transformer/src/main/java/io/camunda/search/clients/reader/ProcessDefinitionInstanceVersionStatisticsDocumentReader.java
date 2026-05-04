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
import static io.camunda.search.aggregation.ProcessDefinitionInstanceVersionStatisticsAggregation.AGGREGATION_FIELD_PROCESS_DEFINITION_NAME;

import io.camunda.search.aggregation.result.ProcessDefinitionInstanceVersionStatisticsAggregationResult;
import io.camunda.search.clients.SearchClientBasedQueryExecutor;
import io.camunda.search.entities.ProcessDefinitionInstanceVersionStatisticsEntity;
import io.camunda.search.query.ProcessDefinitionInstanceVersionStatisticsQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.sort.ProcessDefinitionInstanceVersionStatisticsSort;
import io.camunda.search.sort.SortOrder;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.util.Comparator;
import java.util.List;

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

    // processDefinitionName is a keyword field and cannot be sorted via ES bucket_sort (which only
    // supports numeric metric aggregations). Apply name ordering in Java after fetching from ES.
    final var items = sortByNameIfNeeded(paginatedResult.items(), query.sort());

    return new SearchQueryResult<>(
        paginatedResult.total(),
        paginatedResult.hasMoreTotalItems(),
        items,
        paginatedResult.startCursor(),
        paginatedResult.endCursor());
  }

  private static List<ProcessDefinitionInstanceVersionStatisticsEntity> sortByNameIfNeeded(
      final List<ProcessDefinitionInstanceVersionStatisticsEntity> items,
      final ProcessDefinitionInstanceVersionStatisticsSort sort) {
    if (sort == null || sort.orderings() == null) {
      return items;
    }
    return sort.orderings().stream()
        .filter(o -> AGGREGATION_FIELD_PROCESS_DEFINITION_NAME.equals(o.field()))
        .findFirst()
        .map(
            nameOrdering -> {
              final Comparator<ProcessDefinitionInstanceVersionStatisticsEntity> cmp =
                  Comparator.comparing(
                      ProcessDefinitionInstanceVersionStatisticsEntity::processDefinitionName,
                      Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
              return items.stream()
                  .sorted(nameOrdering.order() == SortOrder.DESC ? cmp.reversed() : cmp)
                  .toList();
            })
        .orElse(items);
  }
}
