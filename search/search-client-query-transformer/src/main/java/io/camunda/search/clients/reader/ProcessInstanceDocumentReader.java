/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import io.camunda.search.clients.SearchClientBasedQueryExecutor;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.filter.Operation;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import java.util.ArrayList;
import java.util.List;

public class ProcessInstanceDocumentReader extends DocumentBasedReader
    implements ProcessInstanceReader {

  private final IncidentDocumentReader incidentReader;

  public ProcessInstanceDocumentReader(
      final SearchClientBasedQueryExecutor executor,
      final IndexDescriptor indexDescriptor,
      final IncidentDocumentReader incidentReader) {
    super(executor, indexDescriptor);
    this.incidentReader = incidentReader;
  }

  @Override
  public ProcessInstanceEntity getByKey(
      final long key, final ResourceAccessChecks resourceAccessChecks) {
    return getSearchExecutor()
        .getByQuery(
            ProcessInstanceQuery.of(b -> b.filter(f -> f.processInstanceKeys(key)).singleResult()),
            ProcessInstanceForListViewEntity.class);
  }

  @Override
  public SearchQueryResult<ProcessInstanceEntity> search(
      final ProcessInstanceQuery query, final ResourceAccessChecks resourceAccessChecks) {

    var filter = query.filter();

    if (filter.incidentErrorHashCodeOperations() != null
        && !filter.incidentErrorHashCodeOperations().isEmpty()) {
      filter = normalizePITopLevelIncidentHashCodes(filter, resourceAccessChecks);
      if (filter.incidentErrorHashCodeOperations().isEmpty()
          || filter.errorMessageOperations().isEmpty()) {
        // If the incidentErrorHashCode was resolved to empty, we can return an empty result
        return SearchQueryResult.empty();
      }
    }

    if (filter.orFilters() != null && !filter.orFilters().isEmpty()) {
      final var normalizedOr =
          normalizePIOrFilterErrorHashCodes(filter.orFilters(), resourceAccessChecks);
      if (normalizedOr.isEmpty()) {
        // If all OR filters was not resolved, we can return an empty result
        return SearchQueryResult.empty();
      }
      filter = filter.toBuilder().orFilters(normalizedOr).build();
    }

    final ProcessInstanceFilter finalFilter = filter;
    final var updatedQuery =
        ProcessInstanceQuery.of(
            q ->
                q.filter(finalFilter)
                    .sort(query.sort())
                    .page(query.page())
                    .resultConfig(query.resultConfig()));

    return executeSearchProcessInstances(updatedQuery, resourceAccessChecks);
  }

  public SearchQueryResult<ProcessInstanceEntity> executeSearchProcessInstances(
      final ProcessInstanceQuery query, final ResourceAccessChecks resourceAccessChecks) {
    return getSearchExecutor()
        .search(query, ProcessInstanceForListViewEntity.class, resourceAccessChecks);
  }

  /**
   * Normalizes a top-level incidentErrorHashCode by resolving it to a full errorMessage, and always
   * adds it as an additional errorMessageOperation (AND), regardless of existing ones. This
   * reflects the fact that a process instance can have multiple incidents, and all error messages
   * can be valid under AND semantics.
   */
  private ProcessInstanceFilter normalizePITopLevelIncidentHashCodes(
      final ProcessInstanceFilter filter, final ResourceAccessChecks resourceAccessChecks) {
    if (filter.incidentErrorHashCodeOperations() == null
        || filter.incidentErrorHashCodeOperations().isEmpty()) {
      return filter;
    }

    final var resolvedErrorMessage =
        incidentReader.findErrorMessageByErrorHashCodes(
            filter.incidentErrorHashCodeOperations(), resourceAccessChecks);

    if (resolvedErrorMessage == null || resolvedErrorMessage.isBlank()) {
      return filter.toBuilder().incidentErrorHashCode(null).build();
    }

    final var existingOps =
        filter.errorMessageOperations() != null
            ? new ArrayList<>(filter.errorMessageOperations())
            : new ArrayList<Operation<String>>();

    existingOps.add(Operation.eq(resolvedErrorMessage));

    return filter.toBuilder()
        .incidentErrorHashCode(null)
        .replaceErrorMessageOperations(existingOps)
        .build();
  }

  /**
   * Given a list of OR filters, normalize any sub-filter that uses incidentErrorHashCode by
   * resolving it to a full errorMessage equals operation, and adding it to any existing
   * errorMessage filters, using AND semantics within the subfilter. If the hash code cannot be
   * resolved, skip that clause.
   */
  private List<ProcessInstanceFilter> normalizePIOrFilterErrorHashCodes(
      final List<ProcessInstanceFilter> orFilters,
      final ResourceAccessChecks resourceAccessChecks) {

    final List<ProcessInstanceFilter> normalized = new ArrayList<>();
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
              .incidentErrorHashCode(null)
              .replaceErrorMessageOperations(existingOps)
              .build();

      normalized.add(updatedSubFilter);
    }
    return normalized;
  }
}
