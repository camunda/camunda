/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import io.camunda.search.clients.SearchClientBasedQueryExecutor;
import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.Operation;
import io.camunda.search.query.IncidentQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.util.List;

public class IncidentDocumentReader extends DocumentBasedReader implements IncidentReader {

  public IncidentDocumentReader(
      final SearchClientBasedQueryExecutor executor, final IndexDescriptor indexDescriptor) {
    super(executor, indexDescriptor);
  }

  @Override
  public IncidentEntity getByKey(final long key, final ResourceAccessChecks resourceAccessChecks) {
    return getSearchExecutor()
        .getByQuery(
            IncidentQuery.of(b -> b.filter(f -> f.incidentKeys(key)).singleResult()),
            io.camunda.webapps.schema.entities.incident.IncidentEntity.class);
  }

  @Override
  public SearchQueryResult<IncidentEntity> search(
      final IncidentQuery query, final ResourceAccessChecks resourceAccessChecks) {
    return getSearchExecutor()
        .search(
            query,
            io.camunda.webapps.schema.entities.incident.IncidentEntity.class,
            resourceAccessChecks);
  }

  public String findErrorMessageByErrorHashCodes(
      final List<Operation<Integer>> hashCodeOperations,
      final ResourceAccessChecks resourceAccessChecks) {
    if (hashCodeOperations == null || hashCodeOperations.isEmpty()) {
      return null;
    }

    final var incidentFilter =
        FilterBuilders.incident(f -> f.errorMessageHashOperations(hashCodeOperations));

    final var incidentResult =
        search(IncidentQuery.of(f -> f.filter(incidentFilter)), resourceAccessChecks);

    if (incidentResult.items().isEmpty()) {
      return null;
    }

    final var incident = incidentResult.items().getFirst();

    if (incident.errorMessage() == null || incident.errorMessage().isBlank()) {
      return null;
    }
    return incident.errorMessage();
  }
}
