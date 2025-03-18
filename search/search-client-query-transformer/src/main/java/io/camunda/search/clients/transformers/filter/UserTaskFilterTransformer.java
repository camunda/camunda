/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.dateTimeOperations;
import static io.camunda.search.clients.query.SearchQueryBuilders.exists;
import static io.camunda.search.clients.query.SearchQueryBuilders.hasChildQuery;
import static io.camunda.search.clients.query.SearchQueryBuilders.hasParentQuery;
import static io.camunda.search.clients.query.SearchQueryBuilders.intOperations;
import static io.camunda.search.clients.query.SearchQueryBuilders.longTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringOperations;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;
import static io.camunda.webapps.schema.descriptors.tasklist.template.TaskTemplate.ASSIGNEE;
import static io.camunda.webapps.schema.descriptors.tasklist.template.TaskTemplate.BPMN_PROCESS_ID;
import static io.camunda.webapps.schema.descriptors.tasklist.template.TaskTemplate.CANDIDATE_GROUPS;
import static io.camunda.webapps.schema.descriptors.tasklist.template.TaskTemplate.CANDIDATE_USERS;
import static io.camunda.webapps.schema.descriptors.tasklist.template.TaskTemplate.COMPLETION_TIME;
import static io.camunda.webapps.schema.descriptors.tasklist.template.TaskTemplate.CREATION_TIME;
import static io.camunda.webapps.schema.descriptors.tasklist.template.TaskTemplate.DUE_DATE;
import static io.camunda.webapps.schema.descriptors.tasklist.template.TaskTemplate.FLOW_NODE_BPMN_ID;
import static io.camunda.webapps.schema.descriptors.tasklist.template.TaskTemplate.FLOW_NODE_INSTANCE_ID;
import static io.camunda.webapps.schema.descriptors.tasklist.template.TaskTemplate.FOLLOW_UP_DATE;
import static io.camunda.webapps.schema.descriptors.tasklist.template.TaskTemplate.IMPLEMENTATION;
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
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.entities.tasklist.TaskEntity.TaskImplementation;
import io.camunda.webapps.schema.entities.tasklist.TaskJoinRelationship.TaskJoinRelationshipType;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class UserTaskFilterTransformer extends IndexFilterTransformer<UserTaskFilter> {

  private final ServiceTransformers transformers;

  public UserTaskFilterTransformer(
      final ServiceTransformers transformers, final IndexDescriptor indexDescriptor) {
    super(indexDescriptor);
    this.transformers = transformers;
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
    ofNullable(getCreationTimeQuery(filter.creationDateOperations())).ifPresent(queries::addAll);
    ofNullable(getCompletionTimeQuery(filter.completionDateOperations()))
        .ifPresent(queries::addAll);
    ofNullable(getFollowUpDateQuery(filter.followUpDateOperations())).ifPresent(queries::addAll);
    ofNullable(getDueDateQuery(filter.dueDateOperations())).ifPresent(queries::addAll);

    // Process Instance Variable Query: Check if processVariable  with specified varName and
    // varValue exists
    ofNullable(getProcessInstanceVariablesQuery(filter.processInstanceVariableFilter()))
        .ifPresent(f -> queries.add(hasParentQuery(TaskJoinRelationshipType.PROCESS.getType(), f)));

    // Local Variable Query: Check if localVariable with specified varName and varValue exists
    // No need validate parent as the localVariable is the only children from Task
    ofNullable(getLocalVariablesQuery(filter.localVariableFilters())).ifPresent(queries::add);

    queries.add(exists("flowNodeInstanceId")); // Default to task
    queries.add(stringTerms(IMPLEMENTATION, List.of(TaskImplementation.ZEEBE_USER_TASK.name())));

    return and(queries);
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

  private List<SearchQuery> getCreationTimeQuery(
      final List<Operation<OffsetDateTime>> creationTime) {
    return dateTimeOperations(CREATION_TIME, creationTime);
  }

  private List<SearchQuery> getCompletionTimeQuery(
      final List<Operation<OffsetDateTime>> completionTime) {
    return dateTimeOperations(COMPLETION_TIME, completionTime);
  }

  private List<SearchQuery> getFollowUpDateQuery(
      final List<Operation<OffsetDateTime>> followUpTime) {
    return dateTimeOperations(FOLLOW_UP_DATE, followUpTime);
  }

  private List<SearchQuery> getDueDateQuery(final List<Operation<OffsetDateTime>> dueTime) {
    return dateTimeOperations(DUE_DATE, dueTime);
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

  private SearchQuery getProcessInstanceVariablesQuery(
      final List<VariableValueFilter> variableFilters) {
    if (variableFilters != null && !variableFilters.isEmpty()) {
      final var transformer = getVariableValueFilterTransformer();
      final var queries =
          variableFilters.stream()
              .map(transformer::apply)
              .map((q) -> hasChildQuery(TaskJoinRelationshipType.PROCESS_VARIABLE.getType(), q))
              .collect(Collectors.toList());
      return and(queries);
    }
    return null;
  }

  private SearchQuery getLocalVariablesQuery(final List<VariableValueFilter> variableFilters) {
    if (variableFilters != null && !variableFilters.isEmpty()) {
      final var transformer = getVariableValueFilterTransformer();

      final var queries =
          variableFilters.stream()
              .map(transformer::apply)
              .map((q) -> hasChildQuery(TaskJoinRelationshipType.LOCAL_VARIABLE.getType(), q))
              .collect(Collectors.toList());
      return and(queries);
    }
    return null;
  }

  private FilterTransformer<VariableValueFilter> getVariableValueFilterTransformer() {
    return transformers.getFilterTransformer(VariableValueFilter.class);
  }
}
