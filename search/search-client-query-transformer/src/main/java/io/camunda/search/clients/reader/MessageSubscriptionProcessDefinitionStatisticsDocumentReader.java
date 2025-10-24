/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import io.camunda.search.aggregation.result.MessageSubscriptionProcessDefinitionStatisticsAggregationResult;
import io.camunda.search.clients.SearchClientBasedQueryExecutor;
import io.camunda.search.entities.MessageSubscriptionProcessDefinitionStatisticsEntity;
import io.camunda.search.query.MessageSubscriptionProcessDefinitionStatisticsQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;

public class MessageSubscriptionProcessDefinitionStatisticsDocumentReader
    extends DocumentBasedReader implements MessageSubscriptionProcessDefinitionStatisticsReader {

  public MessageSubscriptionProcessDefinitionStatisticsDocumentReader(
      final SearchClientBasedQueryExecutor executor, final IndexDescriptor indexDescriptor) {
    super(executor, indexDescriptor);
  }

  @Override
  public SearchQueryResult<MessageSubscriptionProcessDefinitionStatisticsEntity> aggregate(
      final MessageSubscriptionProcessDefinitionStatisticsQuery query,
      final ResourceAccessChecks resourceAccessChecks) {
    final var aggResult =
        getSearchExecutor()
            .aggregate(
                query,
                MessageSubscriptionProcessDefinitionStatisticsAggregationResult.class,
                resourceAccessChecks);
    return new SearchQueryResult<>(
        aggResult.items().size(),
        !aggResult.items().isEmpty(),
        aggResult.items(),
        null,
        aggResult.endCursor());
  }
}
