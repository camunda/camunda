/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import io.camunda.search.aggregation.result.ProcessDefinitionMessageSubscriptionStatisticsAggregationResult;
import io.camunda.search.clients.SearchClientBasedQueryExecutor;
import io.camunda.search.entities.ProcessDefinitionMessageSubscriptionStatisticsEntity;
import io.camunda.search.query.ProcessDefinitionMessageSubscriptionStatisticsQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;

public class ProcessDefinitionMessageSubscriptionStatisticsDocumentReader
    extends DocumentBasedReader implements ProcessDefinitionMessageSubscriptionStatisticsReader {

  public ProcessDefinitionMessageSubscriptionStatisticsDocumentReader(
      final SearchClientBasedQueryExecutor executor, final IndexDescriptor indexDescriptor) {
    super(executor, indexDescriptor);
  }

  @Override
  public SearchQueryResult<ProcessDefinitionMessageSubscriptionStatisticsEntity> aggregate(
      final ProcessDefinitionMessageSubscriptionStatisticsQuery query,
      final ResourceAccessChecks resourceAccessChecks) {
    final var aggResult =
        getSearchExecutor()
            .aggregate(
                query,
                ProcessDefinitionMessageSubscriptionStatisticsAggregationResult.class,
                resourceAccessChecks);
    return new SearchQueryResult<>(
        aggResult.items().size(),
        !aggResult.items().isEmpty(),
        aggResult.items(),
        null,
        aggResult.endCursor());
  }
}
