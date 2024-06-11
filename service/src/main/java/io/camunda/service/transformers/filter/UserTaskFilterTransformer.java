/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.exists;
import static io.camunda.search.clients.query.SearchQueryBuilders.hasChildQuery;
import static io.camunda.search.clients.query.SearchQueryBuilders.longTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.matchAll;
import static io.camunda.search.clients.query.SearchQueryBuilders.not;
import static io.camunda.search.clients.query.SearchQueryBuilders.or;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.service.search.filter.DateValueFilter;
import io.camunda.service.search.filter.UserTaskFilter;
import io.camunda.service.search.filter.VariableValueFilter;
import io.camunda.service.transformers.ServiceTransformers;
import io.camunda.service.transformers.filter.DateValueFilterTransformer.DateFieldFilter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class UserTaskFilterTransformer implements FilterTransformer<UserTaskFilter> {
  private final ServiceTransformers transformers;

  public UserTaskFilterTransformer(final ServiceTransformers transformers) {
    this.transformers = transformers;
  }

  @Override
  public SearchQuery toSearchQuery(final UserTaskFilter filter) {
    final List<Long> userTaskKeys = filter.userTaskKeys();

    final var userTaskKeysQuery = getUserTaskKeysQuery(userTaskKeys);

    final var variablesQuery = getVariablesQuery(filter.variableFilters());

    final var creationDateQuery = getCreationDate(filter.creationDateFilter());
    final var completionTimeQuery = getCompletionTime(filter.completionDateFilter());
    final var dueDateQuery = getDueDate(filter.dueDateFilter());
    final var followUpDateQuery = getFollowUpDate(filter.followUpDateFilter());

    final var processInstanceKeysQuery = getProcessInstanceKeysQuery(filter.processInstanceKeys());
    final var processDefinitionKeyQuery =
        getProcessDefinitionKeyQuery(filter.processDefinitionKeys());
    final var bpmnProcessIdQuery = getBpmnProcessIdQuery(filter.processNames());

    final var candidateUsersQuery = getCandidateUsersQuery(filter.candidateUsers());
    final var candidateGroupsQuery = getCandidateGroupsQuery(filter.candidateGroups());

    final var assigneesQuery = getAssigneesQuery(filter.assignees());
    final var stateQuery = getStateQuery(filter.taskStates());
    final var tenantQuery = getTenantQuery(filter.tenantIds());

    return and(
        userTaskKeysQuery,
        bpmnProcessIdQuery,
        candidateUsersQuery,
        candidateGroupsQuery,
        assigneesQuery,
        stateQuery,
        creationDateQuery,
        completionTimeQuery,
        dueDateQuery,
        followUpDateQuery,
        processInstanceKeysQuery,
        processDefinitionKeyQuery,
        variablesQuery,
        tenantQuery);
  }

  @Override
  public List<String> toIndices(final UserTaskFilter filter) {
    final var completed = filter.completed();

    if (completed) {
      return Arrays.asList("tasklist-task-8.5.0_alias");
    } else {
      return Arrays.asList("tasklist-task-8.5.0_");
    }
  }

  private SearchQuery getVariablesQuery(final List<VariableValueFilter> variableFilters) {
    if (variableFilters != null && !variableFilters.isEmpty()) {
      final var transformer = getVariableValueFilterTransformer();
      final var queries =
          variableFilters.stream()
              .map(transformer::apply)
              .map((q) -> hasChildQuery("variable", q))
              .collect(Collectors.toList());
      return and(queries);
    }
    return null;
  }

  // TDB - First iteration will support CREATED and COMPLETED states
  // Next iteration will support additional states: PAUSED adn CANCELED
  private SearchQuery getUserTaskStateQuery(final UserTaskFilter filter) {
    final var running = filter.completed();
    final var completed = filter.created();

    if (running && completed) {
      return matchAll();
    }

    SearchQuery runningQuery = null;
    SearchQuery completedQuery = null;

    if (running) {
      runningQuery = not(exists("completionTime"));
    }

    if (completed) {
      completedQuery = exists("completionTime");
    }

    final var userTaskStateQuery = or(runningQuery, completedQuery);

    return userTaskStateQuery;
  }

  private SearchQuery getCreationDate(final DateValueFilter filter) {
    if (filter != null) {
      final var transformer = getDateValueFilterTransformer();
      return transformer.apply(new DateFieldFilter("creationTime", filter));
    }
    return null;
  }

  private SearchQuery getCompletionTime(final DateValueFilter filter) {
    if (filter != null) {
      final var transformer = getDateValueFilterTransformer();
      return transformer.apply(new DateFieldFilter("completionTime", filter));
    }
    return null;
  }

  private SearchQuery getDueDate(final DateValueFilter filter) {
    if (filter != null) {
      final var transformer = getDateValueFilterTransformer();
      return transformer.apply(new DateFieldFilter("dueDate", filter));
    }
    return null;
  }

  private SearchQuery getFollowUpDate(final DateValueFilter filter) {
    if (filter != null) {
      final var transformer = getDateValueFilterTransformer();
      return transformer.apply(new DateFieldFilter("followUpDate", filter));
    }
    return null;
  }

  private SearchQuery getProcessInstanceKeysQuery(final List<Long> processInstanceKeys) {
    return longTerms("processInstanceId", processInstanceKeys);
  }

  private SearchQuery getProcessDefinitionKeyQuery(final List<String> processDefinitionIds) {
    return stringTerms("processDefinitionId", processDefinitionIds);
  }

  private SearchQuery getUserTaskKeysQuery(final List<Long> userTaskKeys) {
    return longTerms("key", userTaskKeys);
  }

  private SearchQuery getCandidateUsersQuery(final List<String> candidateUsers) {
    return stringTerms("candidateUsers", candidateUsers);
  }

  private SearchQuery getCandidateGroupsQuery(final List<String> candidateGroups) {
    return stringTerms("candidateGroups", candidateGroups);
  }

  private SearchQuery getAssigneesQuery(final List<String> assignee) {
    return stringTerms("assignee", assignee);
  }

  private SearchQuery getStateQuery(final List<String> state) {
    return stringTerms("state", state);
  }

  private SearchQuery getTenantQuery(final List<String> tenant) {
    return stringTerms("tenantId", tenant);
  }

  private SearchQuery getBpmnProcessIdQuery(final List<String> bpmnProcessId) {
    return stringTerms("bpmnProcessId", bpmnProcessId);
  }

  private SearchQuery getCreatedQuery(final boolean created) {
    if (created) {
      return term("state", "CREATED");
    }

    return null;
  }

  private SearchQuery getCompletedQuery(final boolean completed) {
    if (completed) {
      return term("state", "COMPLETED");
    }

    return null;
  }

  private FilterTransformer<VariableValueFilter> getVariableValueFilterTransformer() {
    return transformers.getFilterTransformer(VariableValueFilter.class);
  }

  private FilterTransformer<DateFieldFilter> getDateValueFilterTransformer() {
    return transformers.getFilterTransformer(DateValueFilter.class);
  }
}
