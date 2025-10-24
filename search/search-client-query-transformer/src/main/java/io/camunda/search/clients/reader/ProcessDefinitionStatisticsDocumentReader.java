/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import io.camunda.search.aggregation.result.ProcessDefinitionFlowNodeStatisticsAggregationResult;
import io.camunda.search.clients.SearchClientBasedQueryExecutor;
import io.camunda.search.clients.reader.utils.IncidentErrorHashCodeNormalizer;
import io.camunda.search.entities.ProcessFlowNodeStatisticsEntity;
import io.camunda.search.query.ProcessDefinitionFlowNodeStatisticsQuery;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.util.List;

public class ProcessDefinitionStatisticsDocumentReader extends DocumentBasedReader
    implements ProcessDefinitionStatisticsReader {

  private final IncidentErrorHashCodeNormalizer normalizer;

  public ProcessDefinitionStatisticsDocumentReader(
      final SearchClientBasedQueryExecutor executor,
      final IndexDescriptor indexDescriptor,
      final IncidentErrorHashCodeNormalizer normalizer) {
    super(executor, indexDescriptor);
    this.normalizer = normalizer;
  }

  @Override
  public List<ProcessFlowNodeStatisticsEntity> aggregate(
      final ProcessDefinitionFlowNodeStatisticsQuery query,
      final ResourceAccessChecks resourceAccessChecks) {

    final var filter =
        normalizer.normalizeAndValidateProcessDefinitionFilter(
            query.filter(), resourceAccessChecks);
    if (filter.isEmpty()) {
      return List.of();
    }

    return executeAggregate(
        new ProcessDefinitionFlowNodeStatisticsQuery(filter.get()), resourceAccessChecks);
  }

  private List<ProcessFlowNodeStatisticsEntity> executeAggregate(
      final ProcessDefinitionFlowNodeStatisticsQuery query,
      final ResourceAccessChecks resourceAccessChecks) {
    return getSearchExecutor()
        .aggregate(
            query, ProcessDefinitionFlowNodeStatisticsAggregationResult.class, resourceAccessChecks)
        .items();
  }
}
