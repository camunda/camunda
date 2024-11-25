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
import static io.camunda.search.clients.query.SearchQueryBuilders.or;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringOperations;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;
import static io.camunda.webapps.schema.descriptors.tasklist.template.TaskTemplate.ASSIGNEE;
import static io.camunda.webapps.schema.descriptors.tasklist.template.TaskTemplate.BPMN_PROCESS_ID;
import static io.camunda.webapps.schema.descriptors.tasklist.template.TaskTemplate.CANDIDATE_GROUPS;
import static io.camunda.webapps.schema.descriptors.tasklist.template.TaskTemplate.CANDIDATE_USERS;
import static io.camunda.webapps.schema.descriptors.tasklist.template.TaskTemplate.FLOW_NODE_BPMN_ID;
import static io.camunda.webapps.schema.descriptors.tasklist.template.TaskTemplate.FLOW_NODE_INSTANCE_ID;
import static io.camunda.webapps.schema.descriptors.tasklist.template.TaskTemplate.KEY;
import static io.camunda.webapps.schema.descriptors.tasklist.template.TaskTemplate.PRIORITY;
import static io.camunda.webapps.schema.descriptors.tasklist.template.TaskTemplate.PROCESS_DEFINITION_ID;
import static io.camunda.webapps.schema.descriptors.tasklist.template.TaskTemplate.PROCESS_INSTANCE_ID;
import static io.camunda.webapps.schema.descriptors.tasklist.template.TaskTemplate.STATE;
import static io.camunda.webapps.schema.descriptors.tasklist.template.TaskTemplate.TENANT_ID;
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
    ofNullable(getElementInstanceKeyQuery(filter.elementInstanceKeys())).ifPresent(queries::add);

    // Task Variable Query: Check if taskVariable with specified varName and varValue exists
    ofNullable(getTaskVariablesQuery(filter.variableFilters())).ifPresent(queries::add);

    // Process Variable Query: Check if processVariable  with specified varName and varValue exists
    final var processVariableQuery =
        hasParentQuery("process", getProcessVariablesQuery(filter.variableFilters()));
    queries.add(processVariableQuery);

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
    return longTerms(PROCESS_INSTANCE_ID, processInstanceKeys);
  }

  private SearchQuery getProcessDefinitionKeyQuery(final List<Long> processDefinitionIds) {
    return longTerms(PROCESS_DEFINITION_ID, processDefinitionIds);
  }

  private SearchQuery getUserTaskKeysQuery(final List<Long> userTaskKeys) {
    return longTerms(KEY, userTaskKeys);
  }

  private List<SearchQuery> getCandidateUsersQuery(final List<Operation<String>> candidateUsers) {
    return stringOperations(CANDIDATE_USERS, candidateUsers);
  }

  private List<SearchQuery> getCandidateGroupsQuery(final List<Operation<String>> candidateGroups) {
    return stringOperations(CANDIDATE_GROUPS, candidateGroups);
  }

  private List<SearchQuery> getAssigneesQuery(final List<Operation<String>> assignees) {
    return stringOperations(ASSIGNEE, assignees);
  }

  private List<SearchQuery> getPrioritiesQuery(final List<Operation<Integer>> priorities) {
    return intOperations(PRIORITY, priorities);
  }

  private SearchQuery getStateQuery(final List<String> state) {
    return stringTerms(STATE, state);
  }

  private SearchQuery getTenantQuery(final List<String> tenant) {
    return stringTerms(TENANT_ID, tenant);
  }

  private SearchQuery getBpmnProcessIdQuery(final List<String> bpmnProcessId) {
    return stringTerms(BPMN_PROCESS_ID, bpmnProcessId);
  }

  private SearchQuery getElementInstanceKeyQuery(final List<Long> elementInstanceKeys) {
    return longTerms(FLOW_NODE_INSTANCE_ID, elementInstanceKeys);
  }

  private SearchQuery getElementIdQuery(final List<String> taskDefinitionId) {
    return stringTerms(FLOW_NODE_BPMN_ID, taskDefinitionId);
  }

  private SearchQuery getProcessVariablesQuery(final List<VariableValueFilter> variableFilters) {
    if (variableFilters != null && !variableFilters.isEmpty()) {
      final var transformer = getVariableValueFilterTransformer();
      final var queries =
          variableFilters.stream()
              .map(transformer::apply)
              .map((q) -> hasChildQuery("variable", q))
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
              .map((q) -> hasChildQuery("localVariables", q))
              .collect(Collectors.toList());
      return or(queries);
    }
    return null;
  }

  private FilterTransformer<VariableValueFilter> getVariableValueFilterTransformer() {
    return transformers.getFilterTransformer(VariableValueFilter.class);
  }
}
