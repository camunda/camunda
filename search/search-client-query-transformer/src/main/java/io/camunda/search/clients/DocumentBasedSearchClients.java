/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients;

import static io.camunda.zeebe.protocol.record.value.EntityType.GROUP;
import static io.camunda.zeebe.protocol.record.value.EntityType.MAPPING;
import static io.camunda.zeebe.protocol.record.value.EntityType.ROLE;
import static io.camunda.zeebe.protocol.record.value.EntityType.USER;

import io.camunda.search.aggregation.result.ProcessDefinitionFlowNodeStatisticsAggregationResult;
import io.camunda.search.aggregation.result.ProcessDefinitionLatestVersionAggregationResult;
import io.camunda.search.aggregation.result.ProcessInstanceFlowNodeStatisticsAggregationResult;
import io.camunda.search.clients.auth.AuthorizationQueryStrategy;
import io.camunda.search.clients.auth.DocumentAuthorizationQueryStrategy;
import io.camunda.search.clients.auth.DocumentTenantQueryStrategy;
import io.camunda.search.clients.auth.ResourceAccessChecks;
import io.camunda.search.clients.auth.TenantQueryStrategy;
import io.camunda.search.entities.AuthorizationEntity;
import io.camunda.search.entities.BatchOperationEntity;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemEntity;
import io.camunda.search.entities.DecisionDefinitionEntity;
import io.camunda.search.entities.DecisionInstanceEntity;
import io.camunda.search.entities.DecisionRequirementsEntity;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.FormEntity;
import io.camunda.search.entities.GroupEntity;
import io.camunda.search.entities.GroupMemberEntity;
import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.entities.IncidentEntity.IncidentState;
import io.camunda.search.entities.JobEntity;
import io.camunda.search.entities.MappingRuleEntity;
import io.camunda.search.entities.MessageSubscriptionEntity;
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.entities.ProcessFlowNodeStatisticsEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.entities.RoleEntity;
import io.camunda.search.entities.RoleMemberEntity;
import io.camunda.search.entities.SequenceFlowEntity;
import io.camunda.search.entities.TenantEntity;
import io.camunda.search.entities.TenantMemberEntity;
import io.camunda.search.entities.UsageMetricsEntity;
import io.camunda.search.entities.UserEntity;
import io.camunda.search.entities.UserTaskEntity;
import io.camunda.search.entities.VariableEntity;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.Operation;
import io.camunda.search.filter.Operator;
import io.camunda.search.filter.ProcessDefinitionStatisticsFilter;
import io.camunda.search.filter.ProcessInstanceStatisticsFilter;
import io.camunda.search.query.AuthorizationQuery;
import io.camunda.search.query.BatchOperationItemQuery;
import io.camunda.search.query.BatchOperationQuery;
import io.camunda.search.query.DecisionDefinitionQuery;
import io.camunda.search.query.DecisionInstanceQuery;
import io.camunda.search.query.DecisionRequirementsQuery;
import io.camunda.search.query.FlowNodeInstanceQuery;
import io.camunda.search.query.FormQuery;
import io.camunda.search.query.GroupQuery;
import io.camunda.search.query.IncidentQuery;
import io.camunda.search.query.JobQuery;
import io.camunda.search.query.MappingRuleQuery;
import io.camunda.search.query.MessageSubscriptionQuery;
import io.camunda.search.query.ProcessDefinitionFlowNodeStatisticsQuery;
import io.camunda.search.query.ProcessDefinitionQuery;
import io.camunda.search.query.ProcessInstanceFlowNodeStatisticsQuery;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.search.query.RoleQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.SequenceFlowQuery;
import io.camunda.search.query.TenantQuery;
import io.camunda.search.query.UsageMetricsQuery;
import io.camunda.search.query.UserQuery;
import io.camunda.search.query.UserTaskQuery;
import io.camunda.search.query.VariableQuery;
import io.camunda.security.auth.SecurityContext;
import io.camunda.security.impl.AuthorizationChecker;
import io.camunda.util.FilterUtil;
import io.camunda.webapps.schema.entities.ProcessEntity;
import io.camunda.webapps.schema.entities.event.EventEntity;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.util.CloseableSilently;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class DocumentBasedSearchClients implements SearchClientsProxy, CloseableSilently {

  private final SearchClientBasedQueryExecutor executor;
  private final AuthorizationQueryStrategy authorizationQueryStrategy;
  private final TenantQueryStrategy tenantQueryStrategy;
  private final SecurityContext securityContext;

  public DocumentBasedSearchClients(final SearchClientBasedQueryExecutor executor) {
    this.executor = executor;
    authorizationQueryStrategy =
        new DocumentAuthorizationQueryStrategy(new AuthorizationChecker(this));
    tenantQueryStrategy = new DocumentTenantQueryStrategy();
    securityContext = null;
  }

  public DocumentBasedSearchClients(
      final SearchClientBasedQueryExecutor executor,
      final AuthorizationQueryStrategy authorizationQueryStrategy,
      final TenantQueryStrategy tenantQueryStrategy,
      final SecurityContext securityContext) {
    this.executor = executor;
    this.authorizationQueryStrategy = authorizationQueryStrategy;
    this.tenantQueryStrategy = tenantQueryStrategy;
    this.securityContext = securityContext;
  }

  @Override
  public SearchQueryResult<AuthorizationEntity> searchAuthorizations(
      final AuthorizationQuery filter) {
    return executeWithResourceAccessChecks(
        access ->
            getSearchExecutor()
                .search(
                    filter,
                    io.camunda.webapps.schema.entities.usermanagement.AuthorizationEntity.class,
                    access));
  }

  @Override
  public SearchQueryResult<SequenceFlowEntity> searchSequenceFlows(final SequenceFlowQuery filter) {
    return executeWithResourceAccessChecks(
        access ->
            getSearchExecutor()
                .search(
                    filter, io.camunda.webapps.schema.entities.SequenceFlowEntity.class, access));
  }

  @Override
  public SearchQueryResult<MessageSubscriptionEntity> searchMessageSubscriptions(
      final MessageSubscriptionQuery filter) {
    return executeWithResourceAccessChecks(
        access -> getSearchExecutor().search(filter, EventEntity.class, access));
  }

  @Override
  public DocumentBasedSearchClients withSecurityContext(final SecurityContext securityContext) {
    return new DocumentBasedSearchClients(
        executor, authorizationQueryStrategy, tenantQueryStrategy, securityContext);
  }

  @Override
  public SearchQueryResult<MappingRuleEntity> searchMappingRules(
      final MappingRuleQuery mappingQuery) {
    final var query = applyFilters(mappingQuery);
    return executeWithResourceAccessChecks(
        access ->
            getSearchExecutor()
                .search(
                    query,
                    io.camunda.webapps.schema.entities.usermanagement.MappingRuleEntity.class,
                    access));
  }

  @Override
  public SearchQueryResult<DecisionDefinitionEntity> searchDecisionDefinitions(
      final DecisionDefinitionQuery filter) {
    return executeWithResourceAccessChecks(
        access ->
            getSearchExecutor()
                .search(
                    filter,
                    io.camunda.webapps.schema.entities.dmn.definition.DecisionDefinitionEntity
                        .class,
                    access));
  }

  @Override
  public SearchQueryResult<DecisionInstanceEntity> searchDecisionInstances(
      final DecisionInstanceQuery filter) {
    return executeWithResourceAccessChecks(
        access ->
            getSearchExecutor()
                .search(
                    filter,
                    io.camunda.webapps.schema.entities.dmn.DecisionInstanceEntity.class,
                    access));
  }

  @Override
  public SearchQueryResult<DecisionRequirementsEntity> searchDecisionRequirements(
      final DecisionRequirementsQuery filter) {
    return executeWithResourceAccessChecks(
        access ->
            getSearchExecutor()
                .search(
                    filter,
                    io.camunda.webapps.schema.entities.dmn.definition.DecisionRequirementsEntity
                        .class,
                    access));
  }

  @Override
  public SearchQueryResult<FlowNodeInstanceEntity> searchFlowNodeInstances(
      final FlowNodeInstanceQuery filter) {
    return executeWithResourceAccessChecks(
        access ->
            getSearchExecutor()
                .search(
                    filter,
                    io.camunda.webapps.schema.entities.flownode.FlowNodeInstanceEntity.class,
                    access));
  }

  @Override
  public SearchQueryResult<FormEntity> searchForms(final FormQuery filter) {
    return executeWithResourceAccessChecks(
        access ->
            getSearchExecutor()
                .search(filter, io.camunda.webapps.schema.entities.form.FormEntity.class, access));
  }

  @Override
  public SearchQueryResult<IncidentEntity> searchIncidents(final IncidentQuery filter) {
    return executeWithResourceAccessChecks(
        access ->
            getSearchExecutor()
                .search(
                    filter,
                    io.camunda.webapps.schema.entities.incident.IncidentEntity.class,
                    access));
  }

  @Override
  public SearchQueryResult<ProcessDefinitionEntity> searchProcessDefinitions(
      final ProcessDefinitionQuery filter) {
    if (filter.filter().isLatestVersion()) {
      final var aggResult =
          executeWithResourceAccessChecks(
              access ->
                  getSearchExecutor()
                      .aggregate(
                          filter, ProcessDefinitionLatestVersionAggregationResult.class, access));

      return new SearchQueryResult<>(
          aggResult.items().size(),
          !aggResult.items().isEmpty(),
          aggResult.items(),
          null,
          aggResult.endCursor());
    }
    return executeWithResourceAccessChecks(
        access -> getSearchExecutor().search(filter, ProcessEntity.class, access));
  }

  @Override
  public List<ProcessFlowNodeStatisticsEntity> processDefinitionFlowNodeStatistics(
      final ProcessDefinitionStatisticsFilter filter) {
    if (!filter.incidentErrorHashCodes().isEmpty()) {
      return mapIncidentErrorHashCodesToProcessInstanceKeys(
          filter.incidentErrorHashCodes(),
          filter.processInstanceKeyOperations(),
          List::of,
          processInstanceKeys -> {
            // Create a new filter that narrows the results to only process instances with
            // matching incident error hashes and existing key filters
            final var updatedFilter =
                filter.toBuilder()
                    .replaceProcessInstanceKeyOperations(
                        List.of(Operation.in(List.copyOf(processInstanceKeys))))
                    .hasIncident(true)
                    .build();
            return executeProcessDefinitionFlowNodeStatistics(updatedFilter);
          });
    }
    return executeProcessDefinitionFlowNodeStatistics(filter);
  }

  public List<ProcessFlowNodeStatisticsEntity> executeProcessDefinitionFlowNodeStatistics(
      final ProcessDefinitionStatisticsFilter filter) {
    return executeWithResourceAccessChecks(
            access ->
                getSearchExecutor()
                    .aggregate(
                        new ProcessDefinitionFlowNodeStatisticsQuery(filter),
                        ProcessDefinitionFlowNodeStatisticsAggregationResult.class,
                        access))
        .items();
  }

  @Override
  public SearchQueryResult<ProcessInstanceEntity> searchProcessInstances(
      final ProcessInstanceQuery filter) {
    if (!filter.filter().incidentErrorHashCodes().isEmpty()) {
      return mapIncidentErrorHashCodesToProcessInstanceKeys(
          filter.filter().incidentErrorHashCodes(),
          filter.filter().processInstanceKeyOperations(),
          SearchQueryResult::empty,
          processInstanceKeys -> {
            // Create a new filter that narrows the results to only process instances with
            // matching incident error hashes and existing key filters
            final var updatedFilter =
                filter.filter().toBuilder()
                    .replaceProcessInstanceKeyOperations(
                        List.of(Operation.in(List.copyOf(processInstanceKeys))))
                    .hasIncident(true)
                    .build();

            final var updatedQuery =
                ProcessInstanceQuery.of(
                    q ->
                        q.filter(updatedFilter)
                            .sort(filter.sort())
                            .page(filter.page())
                            .resultConfig(filter.resultConfig()));

            return executeSearchProcessInstances(updatedQuery);
          });
    }
    return executeSearchProcessInstances(filter);
  }

  @Override
  public List<ProcessFlowNodeStatisticsEntity> processInstanceFlowNodeStatistics(
      final long processInstanceKey) {
    return executeWithResourceAccessChecks(
            access ->
                getSearchExecutor()
                    .aggregate(
                        new ProcessInstanceFlowNodeStatisticsQuery(
                            new ProcessInstanceStatisticsFilter(processInstanceKey)),
                        ProcessInstanceFlowNodeStatisticsAggregationResult.class,
                        access))
        .items();
  }

  @Override
  public SearchQueryResult<JobEntity> searchJobs(final JobQuery query) {
    return executeWithResourceAccessChecks(
        access ->
            getSearchExecutor()
                .search(query, io.camunda.webapps.schema.entities.JobEntity.class, access));
  }

  public SearchQueryResult<ProcessInstanceEntity> executeSearchProcessInstances(
      final ProcessInstanceQuery filter) {
    return executeWithResourceAccessChecks(
        access ->
            getSearchExecutor().search(filter, ProcessInstanceForListViewEntity.class, access));
  }

  private <R> R mapIncidentErrorHashCodesToProcessInstanceKeys(
      final List<Integer> incidentErrorHashCodes,
      final List<Operation<Long>> existingProcessInstanceKeyOperations,
      final Supplier<R> fnEmptyResult,
      final Function<Set<Long>, R> fnResult) {

    // Search for active incidents that match the given error message hash codes
    final var incidentFilter =
        FilterBuilders.incident(
            f ->
                f.errorMessageHashOperations(
                        FilterUtil.mapDefaultToOperation(incidentErrorHashCodes))
                    .states(IncidentState.ACTIVE.name()));

    final var incidentResult = searchIncidents(IncidentQuery.of(f -> f.filter(incidentFilter)));

    if (incidentResult.items().isEmpty()) {
      return fnEmptyResult.get();
    }

    // Collect all relevant process instance keys (from both incidents and existing filter)
    final Set<Long> processInstanceKeys = new HashSet<>();
    incidentResult.items().forEach(i -> processInstanceKeys.add(i.processInstanceKey()));

    for (final var op : existingProcessInstanceKeyOperations) {
      if (op.operator().equals(Operator.EQUALS)) {
        processInstanceKeys.add(op.value());
      } else if (op.operator().equals(Operator.IN)) {
        processInstanceKeys.addAll(op.values());
      }
    }
    return fnResult.apply(processInstanceKeys);
  }

  @Override
  public SearchQueryResult<RoleEntity> searchRoles(final RoleQuery roleQuery) {
    var query = roleQuery;
    if (roleQuery.filter().tenantId() != null) {
      query = expandTenantFilter(roleQuery);
    }
    final var finalQuery = query;
    return executeWithResourceAccessChecks(
        access ->
            getSearchExecutor()
                .search(
                    finalQuery,
                    io.camunda.webapps.schema.entities.usermanagement.RoleEntity.class,
                    access));
  }

  @Override
  public SearchQueryResult<RoleMemberEntity> searchRoleMembers(final RoleQuery filter) {
    return executeWithResourceAccessChecks(
        access ->
            getSearchExecutor()
                .search(
                    filter,
                    io.camunda.webapps.schema.entities.usermanagement.RoleMemberEntity.class,
                    access));
  }

  @Override
  public SearchQueryResult<TenantEntity> searchTenants(final TenantQuery filter) {
    return executeWithResourceAccessChecks(
        access ->
            getSearchExecutor()
                .search(
                    filter,
                    io.camunda.webapps.schema.entities.usermanagement.TenantEntity.class,
                    access));
  }

  @Override
  public SearchQueryResult<TenantMemberEntity> searchTenantMembers(final TenantQuery query) {
    return executeWithResourceAccessChecks(
        access ->
            getSearchExecutor()
                .search(
                    query,
                    io.camunda.webapps.schema.entities.usermanagement.TenantMemberEntity.class,
                    access));
  }

  @Override
  public SearchQueryResult<GroupEntity> searchGroups(final GroupQuery groupQuery) {
    var query = groupQuery;
    if (groupQuery.filter().tenantId() != null) {
      query = expandTenantFilter(groupQuery);
    }
    if (groupQuery.filter().roleId() != null) {
      query = expandRoleFilter(groupQuery);
    }
    final var finalQuery = query;
    return executeWithResourceAccessChecks(
        access ->
            getSearchExecutor()
                .search(
                    finalQuery,
                    io.camunda.webapps.schema.entities.usermanagement.GroupEntity.class,
                    access));
  }

  @Override
  public SearchQueryResult<GroupMemberEntity> searchGroupMembers(final GroupQuery query) {
    return executeWithResourceAccessChecks(
        access ->
            getSearchExecutor()
                .search(
                    query,
                    io.camunda.webapps.schema.entities.usermanagement.GroupMemberEntity.class,
                    access));
  }

  @Override
  public SearchQueryResult<UserEntity> searchUsers(final UserQuery userQuery) {
    final var query = applyFilters(userQuery);
    return executeWithResourceAccessChecks(
        access ->
            getSearchExecutor()
                .search(
                    query,
                    io.camunda.webapps.schema.entities.usermanagement.UserEntity.class,
                    access));
  }

  @Override
  public SearchQueryResult<UserTaskEntity> searchUserTasks(final UserTaskQuery query) {
    return executeWithResourceAccessChecks(
        access -> getSearchExecutor().search(query, TaskEntity.class, access));
  }

  @Override
  public SearchQueryResult<VariableEntity> searchVariables(final VariableQuery query) {
    return executeWithResourceAccessChecks(
        access ->
            getSearchExecutor()
                .search(query, io.camunda.webapps.schema.entities.VariableEntity.class, access));
  }

  private SearchClientBasedQueryExecutor getSearchExecutor() {
    return executor;
  }

  @Override
  public void close() {
    getSearchExecutor().getSearchClient().close();
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
        UsageMetricsQuery.of(
            b ->
                b.filter(
                        f ->
                            f.startTime(query.filter().startTime())
                                .endTime(query.filter().endTime())
                                .events(event))
                    .unlimited());
    final SearchQueryResult<UsageMetricsEntity> result =
        executeWithResourceAccessChecks(
            access ->
                getSearchExecutor()
                    .search(
                        filter,
                        io.camunda.webapps.schema.entities.UsageMetricsEntity.class,
                        access));
    return result.items().stream().map(UsageMetricsEntity::value).distinct().count();
  }

  private MappingRuleQuery applyFilters(final MappingRuleQuery mappingRuleQuery) {
    if (mappingRuleQuery.filter().tenantId() != null) {
      return expandTenantFilter(mappingRuleQuery);
    }
    if (mappingRuleQuery.filter().groupId() != null) {
      return expandGroupFilter(mappingRuleQuery);
    }
    if (mappingRuleQuery.filter().roleId() != null) {
      return expandRoleFilter(mappingRuleQuery);
    }
    return mappingRuleQuery;
  }

  private MappingRuleQuery expandGroupFilter(final MappingRuleQuery mappingRuleQuery) {
    final var mappingIds = getGroupMembers(mappingRuleQuery.filter().groupId(), MAPPING);
    return mappingRuleQuery.toBuilder()
        .filter(mappingRuleQuery.filter().toBuilder().mappingIds(mappingIds).build())
        .build();
  }

  private MappingRuleQuery expandTenantFilter(final MappingRuleQuery mappingRuleQuery) {
    final var mappingIds = getTenantMembers(mappingRuleQuery.filter().tenantId(), MAPPING);
    return mappingRuleQuery.toBuilder()
        .filter(mappingRuleQuery.filter().toBuilder().mappingIds(mappingIds).build())
        .build();
  }

  private UserQuery applyFilters(final UserQuery userQuery) {
    if (userQuery.filter().tenantId() != null) {
      return expandTenantFilter(userQuery);
    }
    if (userQuery.filter().groupId() != null) {
      return expandGroupFilter(userQuery);
    }
    if (userQuery.filter().roleId() != null) {
      return expandRoleFilter(userQuery);
    }
    return userQuery;
  }

  private UserQuery expandTenantFilter(final UserQuery userQuery) {
    final var usernames = getTenantMembers(userQuery.filter().tenantId(), USER);
    return userQuery.toBuilder()
        .filter(userQuery.filter().toBuilder().usernames(usernames).build())
        .build();
  }

  private UserQuery expandGroupFilter(final UserQuery userQuery) {
    final var usernames = getGroupMembers(userQuery.filter().groupId(), USER);

    return userQuery.toBuilder()
        .filter(userQuery.filter().toBuilder().usernames(usernames).build())
        .build();
  }

  private GroupQuery expandTenantFilter(final GroupQuery groupQuery) {
    final var groupIds = getTenantMembers(groupQuery.filter().tenantId(), GROUP);

    return groupQuery.toBuilder()
        .filter(groupQuery.filter().toBuilder().groupIds(groupIds).build())
        .build();
  }

  private RoleQuery expandTenantFilter(final RoleQuery groupQuery) {
    final var roleIds = getTenantMembers(groupQuery.filter().tenantId(), ROLE);
    return groupQuery.toBuilder()
        .filter(groupQuery.filter().toBuilder().roleIds(roleIds).build())
        .build();
  }

  private Set<String> getTenantMembers(final String tenantId, final EntityType entityType) {
    final SearchQueryResult<TenantMemberEntity> tenantMembers =
        executeWithResourceAccessChecks(
            access ->
                getSearchExecutor()
                    .search(
                        TenantQuery.of(
                            b ->
                                b.filter(f -> f.joinParentId(tenantId).memberType(entityType))
                                    .unlimited()),
                        io.camunda.webapps.schema.entities.usermanagement.TenantMemberEntity.class,
                        access));
    return tenantMembers.items().stream().map(TenantMemberEntity::id).collect(Collectors.toSet());
  }

  private Set<String> getGroupMembers(final String groupId, final EntityType entityType) {
    final SearchQueryResult<GroupMemberEntity> groupMembers =
        executeWithResourceAccessChecks(
            access ->
                getSearchExecutor()
                    .search(
                        GroupQuery.of(
                            b ->
                                b.filter(f -> f.joinParentId(groupId).memberType(entityType))
                                    .unlimited()),
                        io.camunda.webapps.schema.entities.usermanagement.GroupMemberEntity.class,
                        access));
    return groupMembers.items().stream().map(GroupMemberEntity::id).collect(Collectors.toSet());
  }

  private UserQuery expandRoleFilter(final UserQuery userQuery) {
    final var usernames = getRoleMemberIds(userQuery.filter().roleId(), EntityType.USER);
    return userQuery.toBuilder()
        .filter(userQuery.filter().toBuilder().usernames(usernames).build())
        .build();
  }

  private MappingRuleQuery expandRoleFilter(final MappingRuleQuery mappingRuleQuery) {
    final var mappingIds = getRoleMemberIds(mappingRuleQuery.filter().roleId(), MAPPING);
    return mappingRuleQuery.toBuilder()
        .filter(mappingRuleQuery.filter().toBuilder().mappingIds(mappingIds).build())
        .build();
  }

  private GroupQuery expandRoleFilter(final GroupQuery groupQuery) {
    final var groupIds = getRoleMemberIds(groupQuery.filter().roleId(), GROUP);
    return groupQuery.toBuilder()
        .filter(groupQuery.filter().toBuilder().groupIds(groupIds).build())
        .build();
  }

  public Set<String> getRoleMemberIds(final String roleId, final EntityType memberType) {
    final SearchQueryResult<RoleMemberEntity> roleMembers =
        executeWithResourceAccessChecks(
            access ->
                getSearchExecutor()
                    .search(
                        RoleQuery.of(
                            b ->
                                b.filter(f -> f.joinParentId(roleId).memberType(memberType))
                                    .unlimited()),
                        io.camunda.webapps.schema.entities.usermanagement.RoleMemberEntity.class,
                        access));
    return roleMembers.items().stream().map(RoleMemberEntity::id).collect(Collectors.toSet());
  }

  @Override
  public SearchQueryResult<BatchOperationEntity> searchBatchOperations(
      final BatchOperationQuery query) {
    return executeWithResourceAccessChecks(
        access ->
            getSearchExecutor()
                .search(
                    query,
                    io.camunda.webapps.schema.entities.operation.BatchOperationEntity.class,
                    access));
  }

  @Override
  public SearchQueryResult<BatchOperationItemEntity> searchBatchOperationItems(
      final BatchOperationItemQuery query) {
    return executeWithResourceAccessChecks(
        access ->
            getSearchExecutor()
                .search(
                    query,
                    io.camunda.webapps.schema.entities.operation.OperationEntity.class,
                    access));
  }

  protected <T> T executeWithResourceAccessChecks(final Function<ResourceAccessChecks, T> applier) {
    final var authorizationCheck =
        authorizationQueryStrategy.resolveAuthorizationCheck(securityContext);
    final var tenantCheck = tenantQueryStrategy.resolveTenantCheck(securityContext);
    return applier.apply(ResourceAccessChecks.of(authorizationCheck, tenantCheck));
  }
}
