/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import io.camunda.search.aggregation.result.ProcessDefinitionFlowNodeStatisticsAggregationResult;
import io.camunda.search.clients.DocumentBasedSearchClient;
import io.camunda.search.clients.security.ResourceAccessChecks;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.entities.ProcessFlowNodeStatisticsEntity;
import io.camunda.search.filter.Operation;
import io.camunda.search.query.ProcessDefinitionStatisticsQuery;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.util.List;

public class ProcessDefinitionStatisticsDocumentReader extends DocumentBasedReader
    implements ProcessDefinitionStatisticsReader {

  private final IncidentDocumentReader incidentReader;

  public ProcessDefinitionStatisticsDocumentReader(
      final DocumentBasedSearchClient searchClient,
      final ServiceTransformers transformers,
      final IndexDescriptor indexDescriptor,
      final IncidentDocumentReader incidentReader) {
    super(searchClient, transformers, indexDescriptor);
    this.incidentReader = incidentReader;
  }

  @Override
  public List<ProcessFlowNodeStatisticsEntity> aggregate(
      final ProcessDefinitionStatisticsQuery query,
      final ResourceAccessChecks resourceAccessChecks) {

    final var filter = query.filter();
    if (!filter.incidentErrorHashCodes().isEmpty()) {
      return incidentReader.mapIncidentErrorHashCodesToProcessInstanceKeys(
          filter.incidentErrorHashCodes(),
          filter.processInstanceKeyOperations(),
          List::of,
          processInstanceKeys -> {
            // Create a new filter that narrows the results to only process instances with
            // matching incident error hashes and existing key filters
            final var updatedFilter =
                filter.toBuilder()
                    .replaceProcessInstanceKeyOperations(
                        List.of(Operation.in(List.copyOf(processInstanceKeys))))
                    .hasIncident(true)
                    .build();
            return executeAggregate(
                new ProcessDefinitionStatisticsQuery(updatedFilter), resourceAccessChecks);
          });
    }
    return executeAggregate(query, resourceAccessChecks);
  }

  private List<ProcessFlowNodeStatisticsEntity> executeAggregate(
      final ProcessDefinitionStatisticsQuery query,
      final ResourceAccessChecks resourceAccessChecks) {
    return getSearchExecutor()
        .aggregate(
            query, ProcessDefinitionFlowNodeStatisticsAggregationResult.class, resourceAccessChecks)
        .items();
  }
}
