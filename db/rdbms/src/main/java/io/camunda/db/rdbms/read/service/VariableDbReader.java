/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import static io.camunda.search.query.SearchQueryBuilders.variableSearchQuery;

import io.camunda.db.rdbms.read.RdbmsReaderConfig;
import io.camunda.db.rdbms.read.domain.VariableDbQuery;
import io.camunda.db.rdbms.sql.VariableMapper;
import io.camunda.db.rdbms.sql.columns.VariableSearchColumn;
import io.camunda.search.clients.reader.VariableReader;
import io.camunda.search.entities.VariableEntity;
import io.camunda.search.filter.Operation;
import io.camunda.search.filter.VariableFilter.Builder;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.VariableQuery;
import io.camunda.search.sort.SortOrder;
import io.camunda.search.sort.SortOption.FieldSorting;
import io.camunda.search.sort.VariableSort;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VariableDbReader extends AbstractEntityReader<VariableEntity>
    implements VariableReader {

  private static final Logger LOG = LoggerFactory.getLogger(VariableDbReader.class);

  private final VariableMapper variableMapper;

  public VariableDbReader(
      final VariableMapper variableMapper, final RdbmsReaderConfig readerConfig) {
    super(VariableSearchColumn.values(), readerConfig);
    this.variableMapper = variableMapper;
  }

  @Override
  public VariableEntity getByKey(final long key, final ResourceAccessChecks resourceAccessChecks) {
    return findOne(key);
  }

  @Override
  public SearchQueryResult<VariableEntity> search(
      final VariableQuery query, final ResourceAccessChecks resourceAccessChecks) {
    if (query.filter().scopeKeyOperations() == null || query.filter().scopeKeyOperations().isEmpty()) {
      return searchWithoutDeduplication(query, resourceAccessChecks);
    }

    if (shouldReturnEmptyResult(resourceAccessChecks)) {
      return buildSearchQueryResult(0, List.of(), convertSort(query.sort(), VariableSearchColumn.VAR_KEY));
    }

    final var authorizedResourceIds =
        resourceAccessChecks
            .getAuthorizedResourceIdsByType()
            .getOrDefault(AuthorizationResourceType.PROCESS_DEFINITION.name(), List.of());
    final var queryWithAllItems = variableSearchQuery(q -> q.filter(query.filter()).unlimited());
    final var dbSortForAll = convertSort(VariableSort.of(b -> b), VariableSearchColumn.VAR_KEY);
    final var dbPageForAll = convertPaging(dbSortForAll, queryWithAllItems.page());
    final var dbQuery =
        VariableDbQuery.of(
            b ->
                b.filter(queryWithAllItems.filter())
                    .authorizedResourceIds(authorizedResourceIds)
                    .authorizedTenantIds(resourceAccessChecks.getAuthorizedTenantIds())
                    .sort(dbSortForAll)
                    .page(dbPageForAll));
    LOG.trace("[RDBMS DB] Search for variables with filter {}", query);
    final var hits = variableMapper.search(dbQuery);

    final var scopePriorities = createScopePriorities(query.filter().scopeKeyOperations());
    final var variablesByName = new HashMap<String, VariableEntity>();
    hits.forEach(
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

    final int from = query.page().from();
    final int to = Math.min(from + query.page().size(), deduplicatedVariables.size());
    final var items =
        from >= deduplicatedVariables.size()
            ? List.<VariableEntity>of()
            : deduplicatedVariables.subList(from, to);
    return new SearchQueryResult<>(deduplicatedVariables.size(), false, items, null, null);
  }

  private SearchQueryResult<VariableEntity> searchWithoutDeduplication(
      final VariableQuery query, final ResourceAccessChecks resourceAccessChecks) {
    final var dbSort = convertSort(query.sort(), VariableSearchColumn.VAR_KEY);

    if (shouldReturnEmptyResult(resourceAccessChecks)) {
      return buildSearchQueryResult(0, List.of(), dbSort);
    }

    final var authorizedResourceIds =
        resourceAccessChecks
            .getAuthorizedResourceIdsByType()
            .getOrDefault(AuthorizationResourceType.PROCESS_DEFINITION.name(), List.of());
    final var dbPage = convertPaging(dbSort, query.page());
    final var dbQuery =
        VariableDbQuery.of(
            b ->
                b.filter(query.filter())
                    .authorizedResourceIds(authorizedResourceIds)
                    .authorizedTenantIds(resourceAccessChecks.getAuthorizedTenantIds())
                    .sort(dbSort)
                    .page(dbPage));
    LOG.trace("[RDBMS DB] Search for variables with filter {}", query);
    final var totalHits = variableMapper.count(dbQuery);

    if (shouldReturnEmptyPage(dbPage, totalHits)) {
      return buildSearchQueryResult(totalHits, List.of(), dbSort);
    }

    final var hits = variableMapper.search(dbQuery);
    return buildSearchQueryResult(totalHits, hits, dbSort);
  }

  public VariableEntity findOne(final Long key) {
    return search(
            new VariableQuery(
                new Builder().variableKeys(key).build(),
                VariableSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(1))))
        .items()
        .stream()
        .findFirst()
        .orElse(null);
  }

  public SearchQueryResult<VariableEntity> search(final VariableQuery query) {
    return search(query, ResourceAccessChecks.disabled());
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
