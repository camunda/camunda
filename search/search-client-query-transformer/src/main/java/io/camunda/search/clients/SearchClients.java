/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients;

import static io.camunda.zeebe.protocol.record.value.EntityType.USER;

import io.camunda.search.clients.auth.DocumentAuthorizationQueryStrategy;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.entities.AuthorizationEntity;
import io.camunda.search.entities.DecisionDefinitionEntity;
import io.camunda.search.entities.DecisionInstanceEntity;
import io.camunda.search.entities.DecisionRequirementsEntity;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.FormEntity;
import io.camunda.search.entities.GroupEntity;
import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.entities.IncidentEntity.IncidentState;
import io.camunda.search.entities.MappingEntity;
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.entities.RoleEntity;
import io.camunda.search.entities.TenantEntity;
import io.camunda.search.entities.TenantMemberEntity;
import io.camunda.search.entities.UsageMetricsEntity;
import io.camunda.search.entities.UserEntity;
import io.camunda.search.entities.UserTaskEntity;
import io.camunda.search.entities.VariableEntity;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.Operation;
import io.camunda.search.filter.ProcessInstanceFilter.Builder;
import io.camunda.search.filter.UsageMetricsFilter;
import io.camunda.search.query.AuthorizationQuery;
import io.camunda.search.query.DecisionDefinitionQuery;
import io.camunda.search.query.DecisionInstanceQuery;
import io.camunda.search.query.DecisionRequirementsQuery;
import io.camunda.search.query.FlowNodeInstanceQuery;
import io.camunda.search.query.FormQuery;
import io.camunda.search.query.GroupQuery;
import io.camunda.search.query.IncidentQuery;
import io.camunda.search.query.MappingQuery;
import io.camunda.search.query.ProcessDefinitionQuery;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.search.query.RoleQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.TenantQuery;
import io.camunda.search.query.UsageMetricsQuery;
import io.camunda.search.query.UserQuery;
import io.camunda.search.query.UserTaskQuery;
import io.camunda.search.query.VariableQuery;
import io.camunda.security.auth.SecurityContext;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.zeebe.util.CloseableSilently;
import java.util.List;
import java.util.stream.Collectors;

public class SearchClients
    implements AuthorizationSearchClient,
        DecisionDefinitionSearchClient,
        DecisionInstanceSearchClient,
        DecisionRequirementSearchClient,
        FlowNodeInstanceSearchClient,
        FormSearchClient,
        IncidentSearchClient,
        ProcessDefinitionSearchClient,
        ProcessInstanceSearchClient,
        RoleSearchClient,
        TenantSearchClient,
        UserTaskSearchClient,
        UserSearchClient,
        VariableSearchClient,
        MappingSearchClient,
        GroupSearchClient,
        UsageMetricsSearchClient,
        CloseableSilently {

  private final DocumentBasedSearchClient searchClient;
  private final ServiceTransformers transformers;
  private final SecurityContext securityContext;

  public SearchClients(
      final DocumentBasedSearchClient searchClient, final IndexDescriptors indexDescriptors) {
    this(
        searchClient,
        ServiceTransformers.newInstance(indexDescriptors),
        SecurityContext.withoutAuthentication());
  }

  private SearchClients(
      final DocumentBasedSearchClient searchClient,
      final ServiceTransformers transformers,
      final SecurityContext securityContext) {
    this.searchClient = searchClient;
    this.transformers = transformers;
    this.securityContext = securityContext;
  }

  @Override
  public SearchQueryResult<AuthorizationEntity> searchAuthorizations(
      final AuthorizationQuery filter) {
    return getSearchExecutor()
        .search(
            filter, io.camunda.webapps.schema.entities.usermanagement.AuthorizationEntity.class);
  }

  @Override
  public List<AuthorizationEntity> findAllAuthorizations(final AuthorizationQuery filter) {
    return getSearchExecutor()
        .findAll(
            filter, io.camunda.webapps.schema.entities.usermanagement.AuthorizationEntity.class);
  }

  @Override
  public SearchClients withSecurityContext(final SecurityContext securityContext) {
    return new SearchClients(searchClient, transformers, securityContext);
  }

  @Override
  public SearchQueryResult<MappingEntity> searchMappings(final MappingQuery filter) {
    return getSearchExecutor()
        .search(filter, io.camunda.webapps.schema.entities.usermanagement.MappingEntity.class);
  }

  @Override
  public List<MappingEntity> findAllMappings(final MappingQuery query) {
    return getSearchExecutor()
        .findAll(query, io.camunda.webapps.schema.entities.usermanagement.MappingEntity.class);
  }

  @Override
  public SearchQueryResult<DecisionDefinitionEntity> searchDecisionDefinitions(
      final DecisionDefinitionQuery filter) {
    return getSearchExecutor()
        .search(
            filter,
            io.camunda.webapps.schema.entities.operate.dmn.definition.DecisionDefinitionEntity
                .class);
  }

  @Override
  public SearchQueryResult<DecisionInstanceEntity> searchDecisionInstances(
      final DecisionInstanceQuery filter) {
    return getSearchExecutor()
        .search(
            filter, io.camunda.webapps.schema.entities.operate.dmn.DecisionInstanceEntity.class);
  }

  @Override
  public SearchQueryResult<DecisionRequirementsEntity> searchDecisionRequirements(
      final DecisionRequirementsQuery filter) {
    return getSearchExecutor()
        .search(
            filter,
            io.camunda.webapps.schema.entities.operate.dmn.definition.DecisionRequirementsEntity
                .class);
  }

  @Override
  public SearchQueryResult<FlowNodeInstanceEntity> searchFlowNodeInstances(
      final FlowNodeInstanceQuery filter) {
    return getSearchExecutor()
        .search(filter, io.camunda.webapps.schema.entities.operate.FlowNodeInstanceEntity.class);
  }

  @Override
  public SearchQueryResult<FormEntity> searchForms(final FormQuery filter) {
    return getSearchExecutor()
        .search(filter, io.camunda.webapps.schema.entities.tasklist.FormEntity.class);
  }

  @Override
  public SearchQueryResult<IncidentEntity> searchIncidents(final IncidentQuery filter) {
    return getSearchExecutor()
        .search(filter, io.camunda.webapps.schema.entities.operate.IncidentEntity.class);
  }

  @Override
  public SearchQueryResult<ProcessDefinitionEntity> searchProcessDefinitions(
      final ProcessDefinitionQuery filter) {
    return getSearchExecutor()
        .search(filter, io.camunda.webapps.schema.entities.operate.ProcessEntity.class);
  }

  @Override
  public SearchQueryResult<ProcessInstanceEntity> searchProcessInstances(
      final ProcessInstanceQuery filter) {
    if (!filter.filter().incidentErrorHashCodes().isEmpty()) {
      return searchProcessInstancesByIncidentErrorHash(filter);
    }

    return getSearchExecutor()
        .search(
            filter,
            io.camunda.webapps.schema.entities.operate.listview.ProcessInstanceForListViewEntity
                .class);
  }

  private SearchQueryResult<ProcessInstanceEntity> searchProcessInstancesByIncidentErrorHash(
      final ProcessInstanceQuery filter) {

    final var originalFilter = filter.filter();

    final var incidentFilter =
        FilterBuilders.incident(
            f ->
                f.errorMessageHashes(originalFilter.incidentErrorHashCodes())
                    .states(IncidentState.ACTIVE));

    final var incidentResult = searchIncidents(IncidentQuery.of(f -> f.filter(incidentFilter)));

    if (incidentResult.items().isEmpty()) {
      return new SearchQueryResult.Builder<ProcessInstanceEntity>().build();
    }

    final var processInstanceKeys =
        incidentResult.items().stream().map(IncidentEntity::processInstanceKey).toList();

    final var updatedFilter =
        FilterBuilders.processInstance(
            f ->
                Builder.from(originalFilter)
                    .processInstanceKeyOperations(Operation.in(processInstanceKeys))
                    .endDateOperations(Operation.exists(false))
                    .hasIncident(true));

    final var updatedQuery =
        ProcessInstanceQuery.of(
            q ->
                q.filter(updatedFilter)
                    .sort(filter.sort())
                    .page(filter.page())
                    .resultConfig(filter.resultConfig()));

    return getSearchExecutor()
        .search(
            updatedQuery,
            io.camunda.webapps.schema.entities.operate.listview.ProcessInstanceForListViewEntity
                .class);
  }

  @Override
  public SearchQueryResult<RoleEntity> searchRoles(final RoleQuery filter) {
    return getSearchExecutor()
        .search(filter, io.camunda.webapps.schema.entities.usermanagement.RoleEntity.class);
  }

  @Override
  public List<RoleEntity> findAllRoles(final RoleQuery filter) {
    return getSearchExecutor()
        .findAll(filter, io.camunda.webapps.schema.entities.usermanagement.RoleEntity.class);
  }

  @Override
  public SearchQueryResult<TenantEntity> searchTenants(final TenantQuery filter) {
    return getSearchExecutor()
        .search(filter, io.camunda.webapps.schema.entities.usermanagement.TenantEntity.class);
  }

  @Override
  public List<TenantEntity> findAllTenants(final TenantQuery query) {
    return getSearchExecutor()
        .findAll(query, io.camunda.webapps.schema.entities.usermanagement.TenantEntity.class);
  }

  @Override
  public SearchQueryResult<GroupEntity> searchGroups(final GroupQuery query) {
    return getSearchExecutor()
        .search(query, io.camunda.webapps.schema.entities.usermanagement.GroupEntity.class);
  }

  @Override
  public List<GroupEntity> findAllGroups(final GroupQuery query) {
    return getSearchExecutor()
        .findAll(query, io.camunda.webapps.schema.entities.usermanagement.GroupEntity.class);
  }

  @Override
  public SearchQueryResult<UserEntity> searchUsers(final UserQuery userQuery) {
    var query = userQuery;
    if (query.filter().tenantId() != null) {
      query = expandTenantFilter(query);
    }
    return getSearchExecutor()
        .search(query, io.camunda.webapps.schema.entities.usermanagement.UserEntity.class);
  }

  @Override
  public SearchQueryResult<UserTaskEntity> searchUserTasks(final UserTaskQuery filter) {
    return getSearchExecutor()
        .search(filter, io.camunda.webapps.schema.entities.tasklist.TaskEntity.class);
  }

  @Override
  public SearchQueryResult<VariableEntity> searchVariables(final VariableQuery filter) {
    return getSearchExecutor()
        .search(filter, io.camunda.webapps.schema.entities.operate.VariableEntity.class);
  }

  private SearchClientBasedQueryExecutor getSearchExecutor() {
    return new SearchClientBasedQueryExecutor(
        searchClient, transformers, new DocumentAuthorizationQueryStrategy(this), securityContext);
  }

  @Override
  public void close() {
    searchClient.close();
  }

  @Override
  public Long countAssignees(final UsageMetricsQuery query) {
    return distinctCountUsageMetricsFor("task_completed_by_assignee", query);
  }

  @Override
  public Long countProcessInstances(final UsageMetricsQuery query) {
    return distinctCountUsageMetricsFor("EVENT_PROCESS_INSTANCE_STARTED", query);
  }

  @Override
  public Long countDecisionInstances(final UsageMetricsQuery query) {
    return distinctCountUsageMetricsFor("EVENT_DECISION_INSTANCE_EVALUATED", query);
  }

  /*
   * The distinct count is implemented here by using Java Stream API until aggregations are in place.
   */
  private Long distinctCountUsageMetricsFor(final String event, final UsageMetricsQuery query) {
    final var filter =
        new UsageMetricsQuery.Builder()
            .filter(
                new UsageMetricsFilter.Builder()
                    .startTime(query.filter().startTime())
                    .endTime(query.filter().endTime())
                    .events(event)
                    .build())
            .build();
    final List<UsageMetricsEntity> metrics =
        new SearchClientBasedQueryExecutor(
                searchClient,
                transformers,
                new DocumentAuthorizationQueryStrategy(this),
                securityContext)
            .findAll(filter, io.camunda.webapps.schema.entities.operate.UsageMetricsEntity.class);
    return metrics.stream().map(UsageMetricsEntity::value).distinct().count();
  }

  private UserQuery expandTenantFilter(final UserQuery userQuery) {
    final List<TenantMemberEntity> tenantMembers =
        getSearchExecutor()
            .findAll(
                new TenantQuery.Builder()
                    .filter(f -> f.joinParentId(userQuery.filter().tenantId()).memberType(USER))
                    .build(),
                io.camunda.webapps.schema.entities.usermanagement.TenantMemberEntity.class);
    final var usernames =
        tenantMembers.stream().map(TenantMemberEntity::id).collect(Collectors.toSet());

    return userQuery.toBuilder()
        .filter(userQuery.filter().toBuilder().usernames(usernames).build())
        .build();
  }
}
