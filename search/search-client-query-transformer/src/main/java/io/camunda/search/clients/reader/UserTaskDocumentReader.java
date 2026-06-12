/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import io.camunda.search.clients.SearchClientBasedQueryExecutor;
import io.camunda.search.entities.UserTaskEntity;
import io.camunda.search.entities.VariableEntity;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.Operation;
import io.camunda.search.filter.UserTaskFilter;
import io.camunda.search.filter.VariableFilter;
import io.camunda.search.filter.VariableValueFilter;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.UserTaskQuery;
import io.camunda.search.query.VariableQuery;
import io.camunda.security.core.authz.ResourceAccessChecks;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class UserTaskDocumentReader extends DocumentBasedReader implements UserTaskReader {

  private static final int VARIABLE_PAGE_SIZE = 10_000;

  public UserTaskDocumentReader(
      final SearchClientBasedQueryExecutor executor, final IndexDescriptor indexDescriptor) {
    super(executor, indexDescriptor);
  }

  @Override
  public UserTaskEntity getByKey(final long key, final ResourceAccessChecks resourceAccessChecks) {
    return getSearchExecutor()
        .getByQuery(
            UserTaskQuery.of(b -> b.filter(f -> f.userTaskKeys(key)).singleResult()),
            TaskEntity.class);
  }

  @Override
  public SearchQueryResult<UserTaskEntity> search(
      final UserTaskQuery query, final ResourceAccessChecks resourceAccessChecks) {
    if (!hasProcessInstanceVariableFilter(query)) {
      return getSearchExecutor().search(query, TaskEntity.class, resourceAccessChecks);
    }

    final var matchedKeys = findProcessInstanceKeysMatchingAllVariableFilters(query.filter());
    if (matchedKeys.isEmpty()) {
      return SearchQueryResult.empty();
    }
    return getSearchExecutor()
        .search(
            resolveProcessInstanceVariableFilter(query, matchedKeys),
            TaskEntity.class,
            resourceAccessChecks);
  }

  private static boolean hasProcessInstanceVariableFilter(final UserTaskQuery query) {
    final var variableFilters = query.filter().processInstanceVariableFilter();
    return variableFilters != null && !variableFilters.isEmpty();
  }

  private Set<Long> findProcessInstanceKeysMatchingAllVariableFilters(final UserTaskFilter filter) {
    Set<Long> matchedKeys = null;
    for (final var variableFilter : filter.processInstanceVariableFilter()) {
      final var keys = findProcessInstanceKeysWithMatchingVariable(variableFilter, filter);
      if (matchedKeys == null) {
        matchedKeys = keys;
      } else {
        matchedKeys.retainAll(keys);
      }
      if (matchedKeys.isEmpty()) {
        return Set.of();
      }
    }
    return matchedKeys;
  }

  private Set<Long> findProcessInstanceKeysWithMatchingVariable(
      final VariableValueFilter variableFilter, final UserTaskFilter userTaskFilter) {
    final var variableQueryFilter =
        FilterBuilders.variable()
            .names(variableFilter.name())
            .valueUntypedOperations(variableFilter.valueOperations())
            .processInstanceKeyOperations(userTaskFilter.processInstanceKeyOperations())
            .processDefinitionKeyOperations(userTaskFilter.processDefinitionKeyOperations())
            .build();

    final var keys = new HashSet<Long>();
    String cursor = null;
    do {
      final var page = fetchVariablesPage(variableQueryFilter, cursor);
      page.items().forEach(variable -> keys.add(variable.processInstanceKey()));
      cursor = isLastPage(page) ? null : page.endCursor();
    } while (cursor != null);
    return keys;
  }

  private SearchQueryResult<VariableEntity> fetchVariablesPage(
      final VariableFilter filter, final String cursor) {
    final var query =
        VariableQuery.of(
            q ->
                q.filter(filter)
                    .sort(s -> s.variableKey().asc())
                    .page(p -> p.size(VARIABLE_PAGE_SIZE).after(cursor)));
    // the schema document class, not the domain entity of the same name: hits are deserialized
    // into the document class and only then transformed to the domain entity
    return getSearchExecutor()
        .search(
            query,
            io.camunda.webapps.schema.entities.VariableEntity.class,
            ResourceAccessChecks.disabled());
  }

  private static boolean isLastPage(final SearchQueryResult<VariableEntity> page) {
    return page.endCursor() == null || page.items().size() < VARIABLE_PAGE_SIZE;
  }

  private static UserTaskQuery resolveProcessInstanceVariableFilter(
      final UserTaskQuery query, final Set<Long> matchedProcessInstanceKeys) {
    final var rewrittenFilter =
        query.filter().toBuilder()
            .clearProcessInstanceVariables()
            .processInstanceKeyOperations(Operation.in(List.copyOf(matchedProcessInstanceKeys)))
            .build();
    return new UserTaskQuery(rewrittenFilter, query.sort(), query.page());
  }
}
