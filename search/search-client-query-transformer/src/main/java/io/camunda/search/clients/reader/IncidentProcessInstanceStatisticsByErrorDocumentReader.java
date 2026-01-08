/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import io.camunda.search.aggregation.result.IncidentProcessInstanceStatisticsByErrorAggregationResult;
import io.camunda.search.clients.SearchClientBasedQueryExecutor;
import io.camunda.search.entities.IncidentProcessInstanceStatisticsByErrorEntity;
import io.camunda.search.query.IncidentProcessInstanceStatisticsByErrorQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;

public class IncidentProcessInstanceStatisticsByErrorDocumentReader extends DocumentBasedReader
    implements IncidentProcessInstanceStatisticsByErrorReader {

  public IncidentProcessInstanceStatisticsByErrorDocumentReader(
      final SearchClientBasedQueryExecutor executor, final IndexDescriptor indexDescriptor) {
    super(executor, indexDescriptor);
  }

  @Override
  public SearchQueryResult<IncidentProcessInstanceStatisticsByErrorEntity> aggregate(
      final IncidentProcessInstanceStatisticsByErrorQuery query,
      final ResourceAccessChecks resourceAccessChecks) {

    final var paginatedResult =
        getSearchExecutor()
            .aggregateWithQueryResult(
                query,
                IncidentProcessInstanceStatisticsByErrorAggregationResult.class,
                resourceAccessChecks,
                IncidentProcessInstanceStatisticsByErrorAggregationResult::items);

    return new SearchQueryResult<>(
        paginatedResult.total(),
        paginatedResult.hasMoreTotalItems(),
        paginatedResult.items(),
        paginatedResult.startCursor(),
        paginatedResult.endCursor());
  }
}
