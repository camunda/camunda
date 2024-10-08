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
import static io.camunda.search.clients.query.SearchQueryBuilders.longTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.not;
import static io.camunda.search.clients.query.SearchQueryBuilders.or;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.filter.UserTaskFilter;
import io.camunda.search.filter.VariableValueFilter;
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
    final var userTaskKeysQuery = getUserTaskKeysQuery(filter.userTaskKeys());

    final var processInstanceKeysQuery = getProcessInstanceKeysQuery(filter.processInstanceKeys());
    final var processDefinitionKeyQuery =
        getProcessDefinitionKeyQuery(filter.processDefinitionKeys());
    final var bpmnProcessDefinitionIdQuery = getBpmnProcessIdQuery(filter.bpmnProcessIds());
    final var elementIdQuery = getElementIdQuery(filter.elementIds());

    final var candidateUsersQuery = getCandidateUsersQuery(filter.candidateUsers());
    final var candidateGroupsQuery = getCandidateGroupsQuery(filter.candidateGroups());

    final var assigneesQuery = getAssigneesQuery(filter.assignees());
    final var stateQuery = getStateQuery(filter.states());
    final var tenantQuery = getTenantQuery(filter.tenantIds());

    // Task Variable Query: Check if taskVariable with specified varName and varValue exists
    final var taskVariableQuery = getTaskVariablesQuery(filter.variableFilters());

    // Process Variable Query: Check if processVariable  with specified varName and varValue exists
    final var processVariableQuery = getProcessVariablesQuery(filter.variableFilters());

    final SearchQuery typeQuery = exists("flowNodeInstanceId"); // Default to task

    SearchQuery variablesQuery = null;
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
      variablesQuery = or(taskVariableQuery, processVariableCondition);
    }

    return and(
        userTaskKeysQuery,
        bpmnProcessDefinitionIdQuery,
        candidateUsersQuery,
        candidateGroupsQuery,
        assigneesQuery,
        stateQuery,
        processInstanceKeysQuery,
        processDefinitionKeyQuery,
        tenantQuery,
        elementIdQuery,
        variablesQuery,
        typeQuery);
  }

  @Override
  public List<String> toIndices(final UserTaskFilter filter) {
    return Arrays.asList("tasklist-list-view-8.6.0_");
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
