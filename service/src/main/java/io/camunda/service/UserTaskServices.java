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
import io.camunda.service.search.filter.VariableFilter;
import io.camunda.service.search.filter.VariableValueFilter;
import io.camunda.service.search.page.SearchQueryPage;
import io.camunda.service.search.query.SearchQueryBuilders;
import io.camunda.service.search.query.SearchQueryResult;
import io.camunda.service.search.query.UserTaskQuery;
import io.camunda.service.search.query.UserTaskQuery.Builder;
import io.camunda.service.search.query.VariableQuery;
import io.camunda.service.search.sort.SortOption;
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
      final VariableServices variableServices = new VariableServices(searchClient, transformers, authentication);
      boolean firstIteration = true;

      for (final VariableValueFilter variableValueFilter : query.filter().variableFilters()) {
        final VariableFilter.Builder variableFilterBuilder = new VariableFilter.Builder();
        variableFilterBuilder.variable(variableValueFilter);

        final SearchQueryPage searchQueryPage = new SearchQueryPage.Builder()
            .size(MAX_ELASTICSEARCH_PAGE_SIZE)
            .build();

        final VariableQuery variableQueryBuilder;
        if(firstIteration){
          variableQueryBuilder = VariableQuery.of(b -> b
              .filter(variableFilterBuilder.build())
              .page(searchQueryPage)
        );}
        else{
          variableFilterBuilder.processInstanceKeys(scopeKeyMapByProcessInstanceKey.keySet().stream().toList());
          variableFilterBuilder.variable(variableValueFilter);
          variableQueryBuilder = VariableQuery.of(b -> b
            .filter(variableFilterBuilder.build())
            .page(searchQueryPage)
        );
        }

        final var searchResults = variableServices
            .withAuthentication(authentication)
            .search(variableQueryBuilder);

        final Map<Long, Set<Long>> currentScopeKeyMapByProcessInstanceKey = searchResults.items().stream()
            .collect(Collectors.groupingBy(
                VariableEntity::processInstanceKey,
                Collectors.mapping(VariableEntity::scopeKey, Collectors.toSet())
            ));

        if (firstIteration) {
          // Initialize the map on the first iteration
          for (final Map.Entry<Long, Set<Long>> entry : currentScopeKeyMapByProcessInstanceKey.entrySet()) {
            final Long processInstanceId = entry.getKey();
            final Set<Long> validScopeKeys = entry.getValue().stream()
                .filter(scopeKey -> !scopeKey.equals(processInstanceId))
                .collect(Collectors.toSet());
            if (!validScopeKeys.isEmpty()) {
              scopeKeyMapByProcessInstanceKey.put(processInstanceId, validScopeKeys);
            } else {
              scopeKeyMapByProcessInstanceKey.put(processInstanceId, null);
            }
          }
          firstIteration = false;
        } else {
          scopeKeyMapByProcessInstanceKey.entrySet().removeIf(entry -> {
            final Long processInstanceId = entry.getKey();
            final Set<Long> validScopeKeys = entry.getValue() != null ? entry.getValue().stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()) : new HashSet<>();
            final Set<Long> currentScopeKeys = currentScopeKeyMapByProcessInstanceKey.get(processInstanceId);

            if (currentScopeKeys != null) {
              validScopeKeys.retainAll(currentScopeKeys);
            }

            // Ensure processInstanceId equals scopeId or scopeId equals flowNodeID
            if (entry.getValue() == null || validScopeKeys.isEmpty()) {
              return currentScopeKeys == null || currentScopeKeys.isEmpty();
            }

            return validScopeKeys.isEmpty() || !currentScopeKeyMapByProcessInstanceKey.containsKey(processInstanceId);
          });

          // Merge the valid entries from the current map
          for (final Map.Entry<Long, Set<Long>> entry : currentScopeKeyMapByProcessInstanceKey.entrySet()) {
            scopeKeyMapByProcessInstanceKey.merge(entry.getKey(), entry.getValue(), (oldSet, newSet) -> {
              oldSet.addAll(newSet);
              return oldSet;
            });
          }
        }
      }

      System.out.println("Collected ScopeKeys by ProcessInstanceId: " + scopeKeyMapByProcessInstanceKey);

      // Collect all unique processInstanceIds and scopeKeys
      final List<Long> allProcessInstanceIds = new ArrayList<>(scopeKeyMapByProcessInstanceKey.keySet());
      final List<Long> allScopeKeys = scopeKeyMapByProcessInstanceKey.values().stream()
          .flatMap(set -> Optional.ofNullable(set).orElse(Collections.emptySet()).stream())
          .filter(Objects::nonNull)
          .collect(Collectors.toList());

      if(allProcessInstanceIds.isEmpty() && allScopeKeys.isEmpty()){
        return new SearchQueryResult<>(0, Collections.emptyList(), null);
      }
      // Modify the UserTaskQuery to include the new filter conditions
      final UserTaskQuery modifiedQuery = UserTaskQuery.of(b -> b
          .filter(f -> f
              .scopeKeys(allScopeKeys)
              .processInstanceKeys(allProcessInstanceIds))
          .sort(query.sort())
          .page(query.page())
      );

      return executor.search(modifiedQuery, UserTaskEntity.class);
    }
    return executor.search(query, UserTaskEntity.class);
  }



  public SearchQueryResult<UserTaskEntity> search(
      final Function<Builder, ObjectBuilder<UserTaskQuery>> fn) {
    return search(SearchQueryBuilders.userTaskSearchQuery(fn));
  }
}
