/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.search.clients.CamundaSearchClient;
import io.camunda.service.entities.UserTaskEntity;
import io.camunda.service.entities.VariableEntity;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.search.filter.UserTaskFilter;
import io.camunda.service.search.filter.VariableFilter;
import io.camunda.service.search.filter.VariableValueFilter;
import io.camunda.service.search.page.SearchQueryPage;
import io.camunda.service.search.query.SearchQueryBuilders;
import io.camunda.service.search.query.SearchQueryResult;
import io.camunda.service.search.query.UserTaskQuery;
import io.camunda.service.search.query.UserTaskQuery.Builder;
import io.camunda.service.search.query.VariableQuery;
import io.camunda.service.security.auth.Authentication;
import io.camunda.service.transformers.ServiceTransformers;
import io.camunda.util.ObjectBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class UserTaskServices
    extends SearchQueryService<UserTaskServices, UserTaskQuery, UserTaskEntity> {

  private static final Integer MAX_ELASTICSEARCH_PAGE_SIZE = 10000;

  public UserTaskServices(final CamundaSearchClient dataStoreClient) {
    this(dataStoreClient, null, null);
  }

  public UserTaskServices(
      final CamundaSearchClient searchClient,
      final ServiceTransformers transformers,
      final Authentication authentication) {
    super(searchClient, transformers, authentication);
  }

  @Override
  public UserTaskServices withAuthentication(final Authentication authentication) {
    return new UserTaskServices(searchClient, transformers, authentication);
  }

  @Override
  public SearchQueryResult<UserTaskEntity> search(final UserTaskQuery query) {
    if (query.filter().variableFilters() != null && !query.filter().variableFilters().isEmpty()) {
      final Map<Long, Set<Long>> scopeKeyMapByProcessInstanceKey = new HashMap<>();
      final VariableServices variableServices =
          new VariableServices(searchClient, transformers, authentication);
      boolean firstIteration = true;

      for (final VariableValueFilter variableValueFilter : query.filter().variableFilters()) {
        processVariableFilter(
            variableValueFilter, variableServices, scopeKeyMapByProcessInstanceKey, firstIteration);
        firstIteration = false;
      }

      // Collect all unique processInstanceIds and scopeKeys
      final List<Long> allProcessInstanceIds =
          new ArrayList<>(scopeKeyMapByProcessInstanceKey.keySet());
      final List<Long> allScopeKeys =
          scopeKeyMapByProcessInstanceKey.values().stream()
              .flatMap(set -> Optional.ofNullable(set).orElse(Collections.emptySet()).stream())
              .filter(Objects::nonNull)
              .collect(Collectors.toList());

      if (allProcessInstanceIds.isEmpty() && allScopeKeys.isEmpty()) {
        return new SearchQueryResult<>(0, Collections.emptyList(), null);
      }

      final UserTaskFilter mergedFilter =
          getUserTaskFilter(query, allProcessInstanceIds, allScopeKeys);

      // Create a new UserTaskQuery with the merged filter
      final UserTaskQuery modifiedQuery =
          UserTaskQuery.of(b -> b.filter(mergedFilter).sort(query.sort()).page(query.page()));

      return executor.search(modifiedQuery, UserTaskEntity.class);
    }
    return executor.search(query, UserTaskEntity.class);
  }

  private static UserTaskFilter getUserTaskFilter(
      final UserTaskQuery query,
      final List<Long> allProcessInstanceIds,
      final List<Long> allScopeKeys) {
    final UserTaskFilter originalFilter = query.filter();

    // Create a new UserTaskFilter with the merged keys and existing values
    return new UserTaskFilter(
        originalFilter.userTaskKeys(),
        originalFilter.userTaskDefinitionIds(),
        originalFilter.processNames(),
        originalFilter.assignees(),
        originalFilter.userTaskState(),
        originalFilter.processInstanceKeys() != null
                && originalFilter.processInstanceKeys().isEmpty()
            ? allProcessInstanceIds
            : originalFilter.processInstanceKeys(),
        originalFilter.processDefinitionKeys(),
        allScopeKeys,
        originalFilter.candidateUsers(),
        originalFilter.candidateGroups(),
        originalFilter.created(),
        originalFilter.completed(),
        originalFilter.canceled(),
        originalFilter.creationDateFilter(),
        originalFilter.completionDateFilter(),
        originalFilter.dueDateFilter(),
        originalFilter.followUpDateFilter(),
        originalFilter.variableFilters(),
        originalFilter.tenantIds());
  }

  public SearchQueryResult<UserTaskEntity> search(
      final Function<Builder, ObjectBuilder<UserTaskQuery>> fn) {
    return search(SearchQueryBuilders.userTaskSearchQuery(fn));
  }

  private void processVariableFilter(
      final VariableValueFilter variableValueFilter,
      final VariableServices variableServices,
      final Map<Long, Set<Long>> scopeKeyMapByProcessInstanceKey,
      final boolean firstIteration) {
    final VariableFilter.Builder variableFilterBuilder = new VariableFilter.Builder();
    variableFilterBuilder.variable(variableValueFilter);

    final SearchQueryPage searchQueryPage =
        new SearchQueryPage.Builder().size(MAX_ELASTICSEARCH_PAGE_SIZE).build();

    final VariableQuery variableQueryBuilder;
    if (firstIteration) {
      variableQueryBuilder =
          VariableQuery.of(b -> b.filter(variableFilterBuilder.build()).page(searchQueryPage));
    } else {
      variableFilterBuilder.processInstanceKeys(
          new ArrayList<>(scopeKeyMapByProcessInstanceKey.keySet()));
      variableFilterBuilder.variable(variableValueFilter);
      variableQueryBuilder =
          VariableQuery.of(b -> b.filter(variableFilterBuilder.build()).page(searchQueryPage));
    }

    final var searchResults =
        variableServices.withAuthentication(authentication).search(variableQueryBuilder);

    final Map<Long, Set<Long>> currentScopeKeyMapByProcessInstanceKey =
        searchResults.items().stream()
            .collect(
                Collectors.groupingBy(
                    VariableEntity::processInstanceKey,
                    Collectors.mapping(VariableEntity::scopeKey, Collectors.toSet())));

    if (firstIteration) {
      initializeScopeKeyMap(
          scopeKeyMapByProcessInstanceKey, currentScopeKeyMapByProcessInstanceKey);
    } else {
      updateScopeKeyMap(scopeKeyMapByProcessInstanceKey, currentScopeKeyMapByProcessInstanceKey);
    }
  }

  private void initializeScopeKeyMap(
      final Map<Long, Set<Long>> scopeKeyMapByProcessInstanceKey,
      final Map<Long, Set<Long>> currentScopeKeyMapByProcessInstanceKey) {
    for (final Map.Entry<Long, Set<Long>> entry :
        currentScopeKeyMapByProcessInstanceKey.entrySet()) {
      final Long processInstanceId = entry.getKey();
      final Set<Long> validScopeKeys =
          entry.getValue().stream()
              .filter(scopeKey -> !scopeKey.equals(processInstanceId))
              .collect(Collectors.toSet());
      if (!validScopeKeys.isEmpty()) {
        scopeKeyMapByProcessInstanceKey.put(processInstanceId, validScopeKeys);
      } else {
        scopeKeyMapByProcessInstanceKey.put(processInstanceId, null);
      }
    }
  }

  private void updateScopeKeyMap(
      final Map<Long, Set<Long>> scopeKeyMapByProcessInstanceKey,
      final Map<Long, Set<Long>> currentScopeKeyMapByProcessInstanceKey) {
    scopeKeyMapByProcessInstanceKey
        .entrySet()
        .removeIf(
            entry -> {
              final Long processInstanceId = entry.getKey();
              final Set<Long> validScopeKeys =
                  entry.getValue() != null
                      ? entry.getValue().stream()
                          .filter(Objects::nonNull)
                          .collect(Collectors.toSet())
                      : new HashSet<>();
              final Set<Long> currentScopeKeys =
                  currentScopeKeyMapByProcessInstanceKey.get(processInstanceId);

              if (currentScopeKeys != null) {
                validScopeKeys.retainAll(currentScopeKeys);
              }

              if (entry.getValue() == null || validScopeKeys.isEmpty()) {
                return currentScopeKeys == null || currentScopeKeys.isEmpty();
              }

              return validScopeKeys.isEmpty()
                  || !currentScopeKeyMapByProcessInstanceKey.containsKey(processInstanceId);
            });

    currentScopeKeyMapByProcessInstanceKey.forEach(
        (processInstanceId, currentScopeKeys) ->
            scopeKeyMapByProcessInstanceKey.merge(
                processInstanceId,
                currentScopeKeys,
                (oldSet, newSet) -> {
                  oldSet.addAll(newSet);
                  return oldSet;
                }));
  }
}
