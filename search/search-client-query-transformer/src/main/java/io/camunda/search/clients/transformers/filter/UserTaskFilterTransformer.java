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
import static io.camunda.search.clients.query.SearchQueryBuilders.matchNone;
import static io.camunda.search.clients.query.SearchQueryBuilders.or;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringOperations;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;
import static io.camunda.webapps.schema.descriptors.template.TaskTemplate.*;
import static java.util.Optional.ofNullable;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.filter.Operation;
import io.camunda.search.filter.UserTaskFilter;
import io.camunda.search.filter.VariableValueFilter;
import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.util.NumberParsingUtil;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.entities.usertask.TaskEntity.TaskImplementation;
import io.camunda.webapps.schema.entities.usertask.TaskJoinRelationship.TaskJoinRelationshipType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserTaskFilterTransformer extends IndexFilterTransformer<UserTaskFilter> {

  private static final Logger LOG = LoggerFactory.getLogger(UserTaskFilterTransformer.class);

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
    ofNullable(getNameQuery(filter.names())).ifPresent(queries::add);
    queries.addAll(getCandidateUsersQuery(filter.candidateUserOperations()));
    queries.addAll(getCandidateGroupsQuery(filter.candidateGroupOperations()));
    queries.addAll(getAssigneesQuery(filter.assigneeOperations()));
    queries.addAll(getPrioritiesQuery(filter.priorityOperations()));
    queries.addAll(getStatesQuery(filter.stateOperations()));
    queries.addAll(getTenantQuery(filter.tenantIdOperations()));
    ofNullable(getElementInstanceKeyQuery(filter.elementInstanceKeys())).ifPresent(queries::add);
    queries.addAll(getCreationTimeQuery(filter.creationDateOperations()));
    queries.addAll(getCompletionTimeQuery(filter.completionDateOperations()));
    queries.addAll(getFollowUpDateQuery(filter.followUpDateOperations()));
    queries.addAll(getDueDateQuery(filter.dueDateOperations()));

    // Handle tags (AND logic like process instances)
    // tags are stored as a keyword list, so we need to match all provided tags
    // expression: tags: [A, B] -> tags:A AND tags:B means
    // the tags list must contain a tag that is equal to A and a tag that is equal to B
    if (filter.tags() != null && !filter.tags().isEmpty()) {
      final List<SearchQuery> tagQueries =
          filter.tags().stream()
              .map(tag -> stringTerms(TAGS, java.util.List.of(tag)))
              .collect(Collectors.toList());
      queries.add(and(tagQueries));
    }

    // Process Instance Variable Query: Check if processVariable with specified
    // varName and
    // varValue exists
    ofNullable(getProcessInstanceVariablesQuery(filter.processInstanceVariableFilter()))
        .ifPresent(f -> queries.add(hasParentQuery(TaskJoinRelationshipType.PROCESS.getType(), f)));

    // Local Variable Query: Check if localVariable with specified varName and
    // varValue exists
    // No need validate parent as the localVariable is the only children from Task
    ofNullable(getLocalVariablesQuery(filter.localVariableFilters())).ifPresent(queries::add);

    queries.add(exists("flowNodeInstanceId")); // Default to task
    queries.add(stringTerms(IMPLEMENTATION, List.of(TaskImplementation.ZEEBE_USER_TASK.name())));

    return and(queries);
  }

  @Override
  protected SearchQuery toAuthorizationCheckSearchQuery(final Authorization<?> authorization) {
    return switch (authorization.resourceType()) {
      case PROCESS_DEFINITION -> stringTerms(BPMN_PROCESS_ID, authorization.resourceIds());
      case USER_TASK -> longTerms(KEY, NumberParsingUtil.parseLongs(authorization.resourceIds()));
      default -> {
        LOG.warn(
            "Unsupported authorization resource type {} for user task search query authorization check; "
                + "returning a match-none query. Supported types: PROCESS_DEFINITION, USER_TASK.",
            authorization.resourceType());
        yield matchNone();
      }
    };
  }

  @Override
  protected SearchQuery toAuthorizationCheckSearchQueryByProperties(
      final Authorization<?> authorization, final CamundaAuthentication authentication) {

    if (authorization.resourceType() != AuthorizationResourceType.USER_TASK) {
      LOG.warn(
          "Property-based authorization is only supported for USER_TASK resource type, "
              + "but received '{}'; returning a match-none query.",
          authorization.resourceType());
      return matchNone();
    }

    final var username = authentication.authenticatedUsername();
    final var groups = authentication.authenticatedGroupIds();

    final var queries = new ArrayList<SearchQuery>();

    for (final var propertyName : authorization.resourcePropertyNames()) {
      switch (propertyName) {
        case Authorization.PROP_ASSIGNEE -> {
          if (StringUtils.isNotEmpty(username)) {
            queries.add(term(ASSIGNEE, username));
          }
        }
        case Authorization.PROP_CANDIDATE_USERS -> {
          if (StringUtils.isNotEmpty(username)) {
            queries.add(stringTerms(CANDIDATE_USERS, List.of(username)));
          }
        }
        case Authorization.PROP_CANDIDATE_GROUPS -> {
          if (groups != null && !groups.isEmpty()) {
            queries.add(stringTerms(CANDIDATE_GROUPS, groups));
          }
        }
        default ->
            LOG.warn(
                "Unknown property name '{}' for USER_TASK property-based authorization; ignoring.",
                propertyName);
      }
    }

    if (queries.isEmpty()) {
      return matchNone();
    }

    return or(queries);
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

  private List<SearchQuery> getStatesQuery(final List<Operation<String>> states) {
    return stringOperations(STATE, states);
  }

  private List<SearchQuery> getTenantQuery(final List<Operation<String>> tenant) {
    return stringOperations(TENANT_ID, tenant);
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

  private SearchQuery getNameQuery(final List<String> name) {
    return stringTerms(NAME, name);
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
