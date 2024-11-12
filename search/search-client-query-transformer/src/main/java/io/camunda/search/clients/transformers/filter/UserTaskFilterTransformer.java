/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.exists;
import static io.camunda.search.clients.query.SearchQueryBuilders.hasChildQuery;
import static io.camunda.search.clients.query.SearchQueryBuilders.hasParentQuery;
import static io.camunda.search.clients.query.SearchQueryBuilders.intOperations;
import static io.camunda.search.clients.query.SearchQueryBuilders.longTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.not;
import static io.camunda.search.clients.query.SearchQueryBuilders.or;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringOperations;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;
import static java.util.Optional.ofNullable;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.filter.Operation;
import io.camunda.search.filter.UserTaskFilter;
import io.camunda.search.filter.VariableValueFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class UserTaskFilterTransformer implements FilterTransformer<UserTaskFilter> {

  private final ServiceTransformers transformers;
  private final boolean isCamundaExporterEnabled;

  public UserTaskFilterTransformer(
      final ServiceTransformers transformers, final boolean isCamundaExporterEnabled) {
    this.transformers = transformers;
    this.isCamundaExporterEnabled = isCamundaExporterEnabled;
  }

  @Override
  public SearchQuery toSearchQuery(final UserTaskFilter filter) {
    final var queries = new ArrayList<SearchQuery>();
    ofNullable(getUserTaskKeysQuery(filter.userTaskKeys())).ifPresent(queries::add);
    ofNullable(getProcessInstanceKeysQuery(filter.processInstanceKeys())).ifPresent(queries::add);
    ofNullable(getProcessDefinitionKeyQuery(filter.processDefinitionKeys()))
        .ifPresent(queries::add);
    ofNullable(getBpmnProcessIdQuery(filter.bpmnProcessIds())).ifPresent(queries::add);
    ofNullable(getElementIdQuery(filter.elementIds())).ifPresent(queries::add);
    ofNullable(getCandidateUsersQuery(filter.candidateUserOperations())).ifPresent(queries::addAll);
    ofNullable(getCandidateGroupsQuery(filter.candidateGroupOperations()))
        .ifPresent(queries::addAll);
    ofNullable(getAssigneesQuery(filter.assigneeOperations())).ifPresent(queries::addAll);
    ofNullable(getPrioritiesQuery(filter.priorityOperations())).ifPresent(queries::addAll);
    ofNullable(getStateQuery(filter.states())).ifPresent(queries::add);
    ofNullable(getTenantQuery(filter.tenantIds())).ifPresent(queries::add);

    // Task Variable Query: Check if taskVariable with specified varName and varValue exists
    final var taskVariableQuery = getTaskVariablesQuery(filter.variableFilters());

    // Process Variable Query: Check if processVariable  with specified varName and varValue exists
    final var processVariableQuery = getProcessVariablesQuery(filter.variableFilters());

    if (filter.variableFilters() != null && !filter.variableFilters().isEmpty()) {
      // Task Variable Name Query
      final var taskVarNameQuery =
          filter.variableFilters() != null
              ? stringTerms(
                  "name",
                  filter.variableFilters().stream()
                      .map(VariableValueFilter::name)
                      .collect(Collectors.toList()))
              : null;

      // Process Condition:
      // 1. Check for process variables in the parent process.
      // 2. Check for variables in subprocesses.
      // 3. Ensure there is no overriding taskVariable.
      final var processVariableCondition =
          and(
              hasParentQuery("process", processVariableQuery),
              not(hasChildQuery("taskVariable", taskVarNameQuery)));

      // Combine taskVariable, processVariable, and subprocessVariable queries with OR logic
      queries.add(or(taskVariableQuery, processVariableCondition));
    }
    queries.add(exists("flowNodeInstanceId")); // Default to task

    return and(queries);
  }

  @Override
  public List<String> toIndices(final UserTaskFilter filter) {
    if (isCamundaExporterEnabled) {
      return List.of("tasklist-task-8.5.0_");
    }
    return List.of("tasklist-list-view-8.6.0_");
  }

  private SearchQuery getProcessInstanceKeysQuery(final List<Long> processInstanceKeys) {
    return longTerms("processInstanceId", processInstanceKeys);
  }

  private SearchQuery getProcessDefinitionKeyQuery(final List<Long> processDefinitionIds) {
    return longTerms("processDefinitionId", processDefinitionIds);
  }

  private SearchQuery getUserTaskKeysQuery(final List<Long> userTaskKeys) {
    return longTerms("key", userTaskKeys);
  }

  private List<SearchQuery> getCandidateUsersQuery(final List<Operation<String>> candidateUsers) {
    return stringOperations("candidateUsers", candidateUsers);
  }

  private List<SearchQuery> getCandidateGroupsQuery(final List<Operation<String>> candidateGroups) {
    return stringOperations("candidateGroups", candidateGroups);
  }

  private List<SearchQuery> getAssigneesQuery(final List<Operation<String>> assignees) {
    return stringOperations("assignee", assignees);
  }

  private List<SearchQuery> getPrioritiesQuery(final List<Operation<Integer>> priorities) {
    return intOperations("priority", priorities);
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

  private SearchQuery getElementIdQuery(final List<String> taskDefinitionId) {
    return stringTerms("flowNodeBpmnId", taskDefinitionId);
  }

  private SearchQuery getProcessVariablesQuery(final List<VariableValueFilter> variableFilters) {
    if (variableFilters != null && !variableFilters.isEmpty()) {
      final var transformer = getVariableValueFilterTransformer();
      final var queries =
          variableFilters.stream()
              .map(transformer::apply)
              .map((q) -> hasChildQuery("processVariable", q))
              .collect(Collectors.toList());
      return or(queries);
    }
    return null;
  }

  private SearchQuery getTaskVariablesQuery(final List<VariableValueFilter> variableFilters) {
    if (variableFilters != null && !variableFilters.isEmpty()) {
      final var transformer = getVariableValueFilterTransformer();

      final var queries =
          variableFilters.stream()
              .map(transformer::apply)
              .map((q) -> hasChildQuery("taskVariable", q))
              .collect(Collectors.toList());
      return or(queries);
    }
    return null;
  }

  private FilterTransformer<VariableValueFilter> getVariableValueFilterTransformer() {
    return transformers.getFilterTransformer(VariableValueFilter.class);
  }
}
