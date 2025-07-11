/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import io.camunda.search.clients.DocumentBasedSearchClient;
import io.camunda.search.clients.security.ResourceAccessChecks;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.filter.Operation;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import java.util.List;

public class ProcessInstanceDocumentReader extends DocumentBasedReader
    implements ProcessInstanceReader {

  private final IncidentDocumentReader incidentReader;

  public ProcessInstanceDocumentReader(
      final DocumentBasedSearchClient searchClient,
      final ServiceTransformers transformers,
      final IndexDescriptor indexDescriptor,
      final IncidentDocumentReader incidentReader) {
    super(searchClient, transformers, indexDescriptor);
    this.incidentReader = incidentReader;
  }

  @Override
  public ProcessInstanceEntity getByKey(
      final String key, final ResourceAccessChecks resourceAccessChecks) {
    return getSearchExecutor()
        .getByQuery(
            ProcessInstanceQuery.of(
                b -> b.filter(f -> f.processInstanceKeys(Long.valueOf(key))).singleResult()),
            ProcessInstanceForListViewEntity.class,
            resourceAccessChecks);
  }

  @Override
  public SearchQueryResult<ProcessInstanceEntity> search(
      final ProcessInstanceQuery query, final ResourceAccessChecks resourceAccessChecks) {

    if (!query.filter().incidentErrorHashCodes().isEmpty()) {
      return incidentReader.mapIncidentErrorHashCodesToProcessInstanceKeys(
          query.filter().incidentErrorHashCodes(),
          query.filter().processInstanceKeyOperations(),
          SearchQueryResult::empty,
          processInstanceKeys -> {
            // Create a new filter that narrows the results to only process instances with
            // matching incident error hashes and existing key filters
            final var updatedFilter =
                query.filter().toBuilder()
                    .replaceProcessInstanceKeyOperations(
                        List.of(Operation.in(List.copyOf(processInstanceKeys))))
                    .hasIncident(true)
                    .build();

            final var updatedQuery =
                ProcessInstanceQuery.of(
                    q ->
                        q.filter(updatedFilter)
                            .sort(query.sort())
                            .page(query.page())
                            .resultConfig(query.resultConfig()));

            return executeSearchProcessInstances(updatedQuery, resourceAccessChecks);
          });
    }
    return executeSearchProcessInstances(query, resourceAccessChecks);
  }

  public SearchQueryResult<ProcessInstanceEntity> executeSearchProcessInstances(
      final ProcessInstanceQuery query, final ResourceAccessChecks resourceAccessChecks) {
    return getSearchExecutor()
        .search(query, ProcessInstanceForListViewEntity.class, resourceAccessChecks);
  }
}
