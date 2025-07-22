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
import io.camunda.search.entities.ProcessFlowNodeStatisticsEntity;
import io.camunda.search.filter.Operation;
import io.camunda.search.filter.ProcessDefinitionStatisticsFilter;
import io.camunda.search.query.ProcessDefinitionFlowNodeStatisticsQuery;
import io.camunda.security.reader.ResourceAccessChecks;
import java.util.ArrayList;
import java.util.List;

public class ProcessDefinitionStatisticsDocumentReader extends DocumentBasedReader
    implements ProcessDefinitionStatisticsReader {

  private final IncidentDocumentReader incidentReader;

  public ProcessDefinitionStatisticsDocumentReader(
      final SearchClientBasedQueryExecutor executor, final IncidentDocumentReader incidentReader) {
    super(executor);
    this.incidentReader = incidentReader;
  }

  @Override
  public List<ProcessFlowNodeStatisticsEntity> aggregate(
      final ProcessDefinitionFlowNodeStatisticsQuery query,
      final ResourceAccessChecks resourceAccessChecks) {

    var filter = query.filter();
    if (filter.incidentErrorHashCodeOperations() != null
        && !filter.incidentErrorHashCodeOperations().isEmpty()) {
      filter = normalizePDTopLevelIncidentHashCodes(filter, resourceAccessChecks);
      if (filter.incidentErrorHashCodeOperations() == null
          || filter.errorMessageOperations().isEmpty()) {
        // If the incidentErrorHashCodes were resolved to null, we can return an empty result
        return List.of();
      }
    }

    if (filter.orFilters() != null && !filter.orFilters().isEmpty()) {
      final var normalizedOr =
          normalizePDOrFilterErrorHashCodes(filter.orFilters(), resourceAccessChecks);
      if (normalizedOr.isEmpty()) {
        // If all OR filters was not resolved, we can return an empty result
        return List.of();
      }
      filter = filter.toBuilder().orFilters(normalizedOr).build();
    }

    return executeAggregate(
        new ProcessDefinitionFlowNodeStatisticsQuery(filter), resourceAccessChecks);
  }

  private List<ProcessFlowNodeStatisticsEntity> executeAggregate(
      final ProcessDefinitionFlowNodeStatisticsQuery query,
      final ResourceAccessChecks resourceAccessChecks) {
    return getSearchExecutor()
        .aggregate(
            query, ProcessDefinitionFlowNodeStatisticsAggregationResult.class, resourceAccessChecks)
        .items();
  }

  private ProcessDefinitionStatisticsFilter normalizePDTopLevelIncidentHashCodes(
      final ProcessDefinitionStatisticsFilter filter,
      final ResourceAccessChecks resourceAccessChecks) {
    if (filter.incidentErrorHashCodeOperations() == null
        || filter.incidentErrorHashCodeOperations().isEmpty()) {
      return filter;
    }

    final var resolvedErrorMessage =
        incidentReader.findErrorMessageByErrorHashCodes(
            filter.incidentErrorHashCodeOperations(), resourceAccessChecks);

    if (resolvedErrorMessage == null || resolvedErrorMessage.isEmpty()) {
      return filter.toBuilder().incidentErrorHashCodeOperations(List.of()).build();
    }
    final var existingOps =
        filter.errorMessageOperations() != null
            ? new ArrayList<>(filter.errorMessageOperations())
            : new ArrayList<Operation<String>>();

    existingOps.add(Operation.eq(resolvedErrorMessage));

    return filter.toBuilder()
        .incidentErrorHashCodeOperations(null)
        .replaceErrorMessageOperations(existingOps)
        .build();
  }

  private List<ProcessDefinitionStatisticsFilter> normalizePDOrFilterErrorHashCodes(
      final List<ProcessDefinitionStatisticsFilter> orFilters,
      final ResourceAccessChecks resourceAccessChecks) {

    final List<ProcessDefinitionStatisticsFilter> normalized = new ArrayList<>();
    for (final var subFilter : orFilters) {
      if (subFilter.incidentErrorHashCodeOperations() == null
          || subFilter.incidentErrorHashCodeOperations().isEmpty()) {
        normalized.add(subFilter);
        continue;
      }

      final var resolvedErrorMessage =
          incidentReader.findErrorMessageByErrorHashCodes(
              subFilter.incidentErrorHashCodeOperations(), resourceAccessChecks);

      if (resolvedErrorMessage == null || resolvedErrorMessage.isBlank()) {
        continue;
      }

      final var existingOps =
          subFilter.errorMessageOperations() != null
              ? new ArrayList<>(subFilter.errorMessageOperations())
              : new ArrayList<Operation<String>>();

      existingOps.add(Operation.eq(resolvedErrorMessage));

      final var updatedSubFilter =
          subFilter.toBuilder()
              .incidentErrorHashCodeOperations(null)
              .replaceErrorMessageOperations(existingOps)
              .build();

      normalized.add(updatedSubFilter);
    }
    return normalized;
  }
}
