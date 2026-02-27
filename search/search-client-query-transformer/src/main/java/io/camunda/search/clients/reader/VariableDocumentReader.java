/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import static io.camunda.search.query.SearchQueryBuilders.variableSearchQuery;

import io.camunda.search.clients.SearchClientBasedQueryExecutor;
import io.camunda.search.entities.VariableEntity;
import io.camunda.search.filter.Operation;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.VariableQuery;
import io.camunda.search.sort.SortOrder;
import io.camunda.search.sort.SortOption.FieldSorting;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VariableDocumentReader extends DocumentBasedReader implements VariableReader {

  public VariableDocumentReader(
      final SearchClientBasedQueryExecutor executor, final IndexDescriptor indexDescriptor) {
    super(executor, indexDescriptor);
  }

  @Override
  public VariableEntity getByKey(final long key, final ResourceAccessChecks resourceAccessChecks) {
    return getSearchExecutor()
        .getByQuery(
            VariableQuery.of(b -> b.filter(f -> f.variableKeys(key)).singleResult()),
            io.camunda.webapps.schema.entities.VariableEntity.class);
  }

  @Override
  public SearchQueryResult<VariableEntity> search(
      final VariableQuery query, final ResourceAccessChecks resourceAccessChecks) {
    if (query.filter().scopeKeyOperations() == null || query.filter().scopeKeyOperations().isEmpty()) {
      return getSearchExecutor()
          .search(
              query, io.camunda.webapps.schema.entities.VariableEntity.class, resourceAccessChecks);
    }

    final var queryWithAllItems = variableSearchQuery(q -> q.filter(query.filter()).unlimited());
    final SearchQueryResult<VariableEntity> variables =
        getSearchExecutor()
            .search(
                queryWithAllItems,
                io.camunda.webapps.schema.entities.VariableEntity.class,
                resourceAccessChecks);

    final var scopePriorities = createScopePriorities(query.filter().scopeKeyOperations());
    final var variablesByName = new HashMap<String, VariableEntity>();
    variables
        .items()
        .forEach(
            variable ->
                variablesByName.merge(
                    variable.name(),
                    variable,
                    (existing, candidate) ->
                        isCloserScope(candidate, existing, scopePriorities) ? candidate : existing));

    final var deduplicatedVariables = new ArrayList<>(variablesByName.values());
    final var sortingComparator = createSortingComparator(query.sort().orderings());
    if (sortingComparator != null) {
      deduplicatedVariables.sort(sortingComparator);
    }

    final var from = query.page().from();
    final var to = Math.min(from + query.page().size(), deduplicatedVariables.size());
    final var items =
        from >= deduplicatedVariables.size()
            ? List.<VariableEntity>of()
            : deduplicatedVariables.subList(from, to);
    return new SearchQueryResult<>(deduplicatedVariables.size(), false, items, null, null);
  }

  private Map<Long, Integer> createScopePriorities(final List<Operation<Long>> scopeOperations) {
    final Map<Long, Integer> priorities = new HashMap<>();
    if (scopeOperations == null) {
      return priorities;
    }

    final var scopeKeys =
        scopeOperations.stream()
            .filter(operation -> operation.values() != null)
            .flatMap(operation -> operation.values().stream())
            .toList();
    for (int i = 0; i < scopeKeys.size(); i++) {
      priorities.put(scopeKeys.get(i), i);
    }
    return priorities;
  }

  private boolean isCloserScope(
      final VariableEntity candidate,
      final VariableEntity existing,
      final Map<Long, Integer> scopePriorities) {
    final int candidatePriority = scopePriorities.getOrDefault(candidate.scopeKey(), 0);
    final int existingPriority = scopePriorities.getOrDefault(existing.scopeKey(), 0);
    if (candidatePriority == existingPriority) {
      final var candidateKey = candidate.variableKey();
      final var existingKey = existing.variableKey();
      if (candidateKey == null) {
        return false;
      }
      if (existingKey == null) {
        return true;
      }
      return candidateKey > existingKey;
    }
    return candidatePriority > existingPriority;
  }

  private Comparator<VariableEntity> createSortingComparator(final List<FieldSorting> sortings) {
    if (sortings == null || sortings.isEmpty()) {
      return null;
    }

    Comparator<VariableEntity> comparator = null;
    for (final var sorting : sortings) {
      final Comparator<VariableEntity> fieldComparator =
          switch (sorting.field()) {
            case "variableKey" ->
                Comparator.comparing(
                    VariableEntity::variableKey, Comparator.nullsFirst(Long::compareTo));
            case "name" ->
                Comparator.comparing(VariableEntity::name, Comparator.nullsFirst(String::compareTo));
            case "value" ->
                Comparator.comparing(VariableEntity::value, Comparator.nullsFirst(String::compareTo));
            case "scopeKey" ->
                Comparator.comparing(
                    VariableEntity::scopeKey, Comparator.nullsFirst(Long::compareTo));
            case "processInstanceKey" ->
                Comparator.comparing(
                    VariableEntity::processInstanceKey, Comparator.nullsFirst(Long::compareTo));
            case "tenantId" ->
                Comparator.comparing(
                    VariableEntity::tenantId, Comparator.nullsFirst(String::compareTo));
            default ->
                throw new IllegalArgumentException("Unknown sort field for variables: " + sorting.field());
          };
      final var orderedComparator =
          sorting.order() == SortOrder.DESC ? fieldComparator.reversed() : fieldComparator;
      comparator =
          comparator == null ? orderedComparator : comparator.thenComparing(orderedComparator);
    }
    return comparator;
  }
}
