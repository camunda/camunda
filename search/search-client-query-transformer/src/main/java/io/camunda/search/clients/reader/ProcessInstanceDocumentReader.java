/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import io.camunda.search.clients.SearchClientBasedQueryExecutor;
import io.camunda.search.clients.reader.utils.IncidentErrorHashCodeNormalizer;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;

public class ProcessInstanceDocumentReader extends DocumentBasedReader
    implements ProcessInstanceReader {

  private final IncidentErrorHashCodeNormalizer normalizer;

  public ProcessInstanceDocumentReader(
      final SearchClientBasedQueryExecutor executor,
      final IndexDescriptor indexDescriptor,
      final IncidentErrorHashCodeNormalizer normalizer) {
    super(executor, indexDescriptor);
    this.normalizer = normalizer;
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

    final var filter =
        normalizer.normalizeAndValidateProcessInstanceFilter(query.filter(), resourceAccessChecks);
    if (filter.isEmpty()) {
      return SearchQueryResult.empty();
    }

    final var updatedQuery =
        ProcessInstanceQuery.of(
            q ->
                q.filter(filter.get())
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

  @Override
  public ProcessInstanceReader withEngineName(final String engineName) {
    return this;
  }
}
