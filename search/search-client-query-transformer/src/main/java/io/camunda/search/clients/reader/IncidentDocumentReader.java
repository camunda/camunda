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
import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.entities.IncidentEntity.IncidentState;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.Operation;
import io.camunda.search.filter.Operator;
import io.camunda.search.query.IncidentQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.util.FilterUtil;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

public class IncidentDocumentReader extends DocumentBasedReader implements IncidentReader {

  public IncidentDocumentReader(
      final DocumentBasedSearchClient searchClient,
      final ServiceTransformers transformers,
      final IndexDescriptor indexDescriptor) {
    super(searchClient, transformers, indexDescriptor);
  }

  @Override
  public IncidentEntity getByKey(
      final String key, final ResourceAccessChecks resourceAccessChecks) {
    return getSearchExecutor()
        .getByQuery(
            IncidentQuery.of(b -> b.filter(f -> f.incidentKeys(Long.valueOf(key))).singleResult()),
            io.camunda.webapps.schema.entities.incident.IncidentEntity.class,
            resourceAccessChecks);
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

  public <R> R mapIncidentErrorHashCodesToProcessInstanceKeys(
      final List<Integer> incidentErrorHashCodes,
      final List<Operation<Long>> existingProcessInstanceKeyOperations,
      final Supplier<R> fnEmptyResult,
      final Function<Set<Long>, R> fnResult) {

    // Search for active incidents that match the given error message hash codes
    final var incidentFilter =
        FilterBuilders.incident(
            f ->
                f.errorMessageHashOperations(
                        FilterUtil.mapDefaultToOperation(incidentErrorHashCodes))
                    .states(IncidentState.ACTIVE.name()));

    final var incidentResult =
        search(IncidentQuery.of(f -> f.filter(incidentFilter)), ResourceAccessChecks.disabled());

    if (incidentResult.items().isEmpty()) {
      return fnEmptyResult.get();
    }

    // Collect all relevant process instance keys (from both incidents and existing filter)
    final Set<Long> processInstanceKeys = new HashSet<>();
    incidentResult.items().forEach(i -> processInstanceKeys.add(i.processInstanceKey()));

    for (final var op : existingProcessInstanceKeyOperations) {
      if (op.operator().equals(Operator.EQUALS)) {
        processInstanceKeys.add(op.value());
      } else if (op.operator().equals(Operator.IN)) {
        processInstanceKeys.addAll(op.values());
      }
    }
    return fnResult.apply(processInstanceKeys);
  }
}
