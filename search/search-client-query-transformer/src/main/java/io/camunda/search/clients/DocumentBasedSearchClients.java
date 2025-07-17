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
import io.camunda.search.clients.auth.DocumentAuthorizationQueryStrategy;
import io.camunda.search.clients.transformers.ServiceTransformers;
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
import io.camunda.search.entities.MappingEntity;
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
import io.camunda.search.filter.ProcessDefinitionStatisticsFilter;
import io.camunda.search.filter.ProcessInstanceFilter;
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
import io.camunda.search.query.MappingQuery;
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
import io.camunda.util.FilterUtil;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.webapps.schema.entities.ProcessEntity;
import io.camunda.webapps.schema.entities.event.EventEntity;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.util.CloseableSilently;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class DocumentBasedSearchClients implements SearchClientsProxy, CloseableSilently {

  private final DocumentBasedSearchClient searchClient;
  private final ServiceTransformers transformers;
  private final SecurityContext securityContext;

  public DocumentBasedSearchClients(
      final DocumentBasedSearchClient searchClient, final IndexDescriptors indexDescriptors) {
    this(
        searchClient,
        ServiceTransformers.newInstance(indexDescriptors),
        SecurityContext.withoutAuthentication());
  }

  private DocumentBasedSearchClients(
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
  public SearchQueryResult<SequenceFlowEntity> searchSequenceFlows(final SequenceFlowQuery filter) {
    return getSearchExecutor()
        .search(filter, io.camunda.webapps.schema.entities.SequenceFlowEntity.class);
  }

  @Override
  public SearchQueryResult<MessageSubscriptionEntity> searchMessageSubscriptions(
      final MessageSubscriptionQuery filter) {
    return getSearchExecutor().search(filter, EventEntity.class);
  }

  @Override
  public DocumentBasedSearchClients withSecurityContext(final SecurityContext securityContext) {
    return new DocumentBasedSearchClients(searchClient, transformers, securityContext);
  }

  @Override
  public SearchQueryResult<MappingEntity> searchMappings(final MappingQuery mappingQuery) {
    final var query = applyFilters(mappingQuery);
    return getSearchExecutor()
        .search(query, io.camunda.webapps.schema.entities.usermanagement.MappingEntity.class);
  }

  @Override
  public SearchQueryResult<DecisionDefinitionEntity> searchDecisionDefinitions(
      final DecisionDefinitionQuery filter) {
    return getSearchExecutor()
        .search(
            filter,
            io.camunda.webapps.schema.entities.dmn.definition.DecisionDefinitionEntity.class);
  }

  @Override
  public SearchQueryResult<DecisionInstanceEntity> searchDecisionInstances(
      final DecisionInstanceQuery filter) {
    return getSearchExecutor()
        .search(filter, io.camunda.webapps.schema.entities.dmn.DecisionInstanceEntity.class);
  }

  @Override
  public SearchQueryResult<DecisionRequirementsEntity> searchDecisionRequirements(
      final DecisionRequirementsQuery filter) {
    return getSearchExecutor()
        .search(
            filter,
            io.camunda.webapps.schema.entities.dmn.definition.DecisionRequirementsEntity.class);
  }

  @Override
  public SearchQueryResult<FlowNodeInstanceEntity> searchFlowNodeInstances(
      final FlowNodeInstanceQuery filter) {
    return getSearchExecutor()
        .search(filter, io.camunda.webapps.schema.entities.flownode.FlowNodeInstanceEntity.class);
  }

  @Override
  public SearchQueryResult<FormEntity> searchForms(final FormQuery filter) {
    return getSearchExecutor()
        .search(filter, io.camunda.webapps.schema.entities.form.FormEntity.class);
  }

  @Override
  public SearchQueryResult<IncidentEntity> searchIncidents(final IncidentQuery filter) {
    return getSearchExecutor()
        .search(filter, io.camunda.webapps.schema.entities.incident.IncidentEntity.class);
  }

  @Override
  public SearchQueryResult<ProcessDefinitionEntity> searchProcessDefinitions(
      final ProcessDefinitionQuery filter) {
    if (filter.filter().isLatestVersion()) {
      final var aggResult =
          getSearchExecutor()
              .aggregate(filter, ProcessDefinitionLatestVersionAggregationResult.class);
      return new SearchQueryResult<>(
          aggResult.items().size(),
          !aggResult.items().isEmpty(),
          aggResult.items(),
          null,
          aggResult.endCursor());
    }
    return getSearchExecutor().search(filter, ProcessEntity.class);
  }

  @Override
  public List<ProcessFlowNodeStatisticsEntity> processDefinitionFlowNodeStatistics(
      final ProcessDefinitionStatisticsFilter query) {

    var filter = query;
    if (query.incidentErrorHashCodes() != null && !query.incidentErrorHashCodes().isEmpty()) {
      filter = normalizePDTopLevelIncidentHashCodes(query);
      if (filter.incidentErrorHashCodes() == null && filter.errorMessageOperations().isEmpty()) {
        // If the incidentErrorHashCodes were resolved to null, we can return an empty result
        return List.of();
      }
    }

    if (filter.orFilters() != null && !filter.orFilters().isEmpty()) {
      final var normalizedOr = normalizePDOrFilterErrorHashCodes(filter.orFilters());
      filter = filter.toBuilder().orFilters(normalizedOr).build();
    }

    return executeProcessDefinitionFlowNodeStatistics(filter);
  }

  public List<ProcessFlowNodeStatisticsEntity> executeProcessDefinitionFlowNodeStatistics(
      final ProcessDefinitionStatisticsFilter filter) {
    return getSearchExecutor()
        .aggregate(
            new ProcessDefinitionFlowNodeStatisticsQuery(filter),
            ProcessDefinitionFlowNodeStatisticsAggregationResult.class)
        .items();
  }

  @Override
  public SearchQueryResult<ProcessInstanceEntity> searchProcessInstances(
      final ProcessInstanceQuery query) {

    var filter = query.filter();

    if (Objects.nonNull(query.filter().incidentErrorHashCode())) {
      filter = normalizePITopLevelIncidentHashCodes(filter);
      if (filter.incidentErrorHashCode() == null && filter.errorMessageOperations().isEmpty()) {
        // If the incidentErrorHashCode was resolved to null, we can return an empty result
        return SearchQueryResult.empty();
      }
    }

    if (filter.orFilters() != null && !filter.orFilters().isEmpty()) {
      final var normalizedOr = normalizePIOrFilterErrorHashCodes(filter.orFilters());
      filter = filter.toBuilder().orFilters(normalizedOr).build();
    }

    final ProcessInstanceFilter finalFilter = filter;
    final var updatedQuery =
        ProcessInstanceQuery.of(
            q ->
                q.filter(finalFilter)
                    .sort(query.sort())
                    .page(query.page())
                    .resultConfig(query.resultConfig()));

    return executeSearchProcessInstances(updatedQuery);
  }

  @Override
  public List<ProcessFlowNodeStatisticsEntity> processInstanceFlowNodeStatistics(
      final long processInstanceKey) {
    return getSearchExecutor()
        .aggregate(
            new ProcessInstanceFlowNodeStatisticsQuery(
                new ProcessInstanceStatisticsFilter(processInstanceKey)),
            ProcessInstanceFlowNodeStatisticsAggregationResult.class)
        .items();
  }

  @Override
  public SearchQueryResult<JobEntity> searchJobs(final JobQuery query) {
    return getSearchExecutor().search(query, io.camunda.webapps.schema.entities.JobEntity.class);
  }

  public SearchQueryResult<ProcessInstanceEntity> executeSearchProcessInstances(
      final ProcessInstanceQuery filter) {
    return getSearchExecutor().search(filter, ProcessInstanceForListViewEntity.class);
  }

  @Override
  public SearchQueryResult<RoleEntity> searchRoles(final RoleQuery roleQuery) {
    var query = roleQuery;
    if (roleQuery.filter().tenantId() != null) {
      query = expandTenantFilter(roleQuery);
    }
    return getSearchExecutor()
        .search(query, io.camunda.webapps.schema.entities.usermanagement.RoleEntity.class);
  }

  @Override
  public SearchQueryResult<RoleMemberEntity> searchRoleMembers(final RoleQuery filter) {
    return getSearchExecutor()
        .search(filter, io.camunda.webapps.schema.entities.usermanagement.RoleMemberEntity.class);
  }

  @Override
  public SearchQueryResult<TenantEntity> searchTenants(final TenantQuery filter) {
    return getSearchExecutor()
        .search(filter, io.camunda.webapps.schema.entities.usermanagement.TenantEntity.class);
  }

  @Override
  public SearchQueryResult<TenantMemberEntity> searchTenantMembers(final TenantQuery query) {
    return getSearchExecutor()
        .search(query, io.camunda.webapps.schema.entities.usermanagement.TenantMemberEntity.class);
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
    return getSearchExecutor()
        .search(query, io.camunda.webapps.schema.entities.usermanagement.GroupEntity.class);
  }

  @Override
  public SearchQueryResult<GroupMemberEntity> searchGroupMembers(final GroupQuery query) {
    return getSearchExecutor()
        .search(query, io.camunda.webapps.schema.entities.usermanagement.GroupMemberEntity.class);
  }

  @Override
  public SearchQueryResult<UserEntity> searchUsers(final UserQuery userQuery) {
    final var query = applyFilters(userQuery);
    return getSearchExecutor()
        .search(query, io.camunda.webapps.schema.entities.usermanagement.UserEntity.class);
  }

  @Override
  public SearchQueryResult<UserTaskEntity> searchUserTasks(final UserTaskQuery filter) {
    return getSearchExecutor().search(filter, TaskEntity.class);
  }

  @Override
  public SearchQueryResult<VariableEntity> searchVariables(final VariableQuery filter) {
    return getSearchExecutor()
        .search(filter, io.camunda.webapps.schema.entities.VariableEntity.class);
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
        UsageMetricsQuery.of(
            b ->
                b.filter(
                        f ->
                            f.startTime(query.filter().startTime())
                                .endTime(query.filter().endTime())
                                .events(event))
                    .unlimited());
    final SearchQueryResult<UsageMetricsEntity> result =
        getSearchExecutor()
            .search(filter, io.camunda.webapps.schema.entities.UsageMetricsEntity.class);
    return result.items().stream().map(UsageMetricsEntity::value).distinct().count();
  }

  private MappingQuery applyFilters(final MappingQuery mappingQuery) {
    if (mappingQuery.filter().tenantId() != null) {
      return expandTenantFilter(mappingQuery);
    }
    if (mappingQuery.filter().groupId() != null) {
      return expandGroupFilter(mappingQuery);
    }
    if (mappingQuery.filter().roleId() != null) {
      return expandRoleFilter(mappingQuery);
    }
    return mappingQuery;
  }

  private MappingQuery expandGroupFilter(final MappingQuery mappingQuery) {
    final var mappingIds = getGroupMembers(mappingQuery.filter().groupId(), MAPPING);
    return mappingQuery.toBuilder()
        .filter(mappingQuery.filter().toBuilder().mappingIds(mappingIds).build())
        .build();
  }

  private MappingQuery expandTenantFilter(final MappingQuery mappingQuery) {
    final var mappingIds = getTenantMembers(mappingQuery.filter().tenantId(), MAPPING);
    return mappingQuery.toBuilder()
        .filter(mappingQuery.filter().toBuilder().mappingIds(mappingIds).build())
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
        getSearchExecutor()
            .search(
                TenantQuery.of(
                    b ->
                        b.filter(f -> f.joinParentId(tenantId).memberType(entityType)).unlimited()),
                io.camunda.webapps.schema.entities.usermanagement.TenantMemberEntity.class);
    return tenantMembers.items().stream().map(TenantMemberEntity::id).collect(Collectors.toSet());
  }

  private Set<String> getGroupMembers(final String groupId, final EntityType entityType) {
    final SearchQueryResult<GroupMemberEntity> groupMembers =
        getSearchExecutor()
            .search(
                GroupQuery.of(
                    b -> b.filter(f -> f.joinParentId(groupId).memberType(entityType)).unlimited()),
                io.camunda.webapps.schema.entities.usermanagement.GroupMemberEntity.class);
    return groupMembers.items().stream().map(GroupMemberEntity::id).collect(Collectors.toSet());
  }

  private UserQuery expandRoleFilter(final UserQuery userQuery) {
    final var usernames = getRoleMemberIds(userQuery.filter().roleId(), EntityType.USER);
    return userQuery.toBuilder()
        .filter(userQuery.filter().toBuilder().usernames(usernames).build())
        .build();
  }

  private MappingQuery expandRoleFilter(final MappingQuery mappingQuery) {
    final var mappingIds = getRoleMemberIds(mappingQuery.filter().roleId(), MAPPING);
    return mappingQuery.toBuilder()
        .filter(mappingQuery.filter().toBuilder().mappingIds(mappingIds).build())
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
        getSearchExecutor()
            .search(
                RoleQuery.of(
                    b -> b.filter(f -> f.joinParentId(roleId).memberType(memberType)).unlimited()),
                io.camunda.webapps.schema.entities.usermanagement.RoleMemberEntity.class);
    return roleMembers.items().stream().map(RoleMemberEntity::id).collect(Collectors.toSet());
  }

  @Override
  public SearchQueryResult<BatchOperationEntity> searchBatchOperations(
      final BatchOperationQuery query) {
    return getSearchExecutor()
        .search(query, io.camunda.webapps.schema.entities.operation.BatchOperationEntity.class);
  }

  @Override
  public SearchQueryResult<BatchOperationItemEntity> searchBatchOperationItems(
      final BatchOperationItemQuery query) {
    return getSearchExecutor()
        .search(query, io.camunda.webapps.schema.entities.operation.OperationEntity.class);
  }

  /**
   * Normalizes a top-level incidentErrorHashCode by resolving it to a full errorMessage, and always
   * adds it as an additional errorMessageOperation (AND), regardless of existing ones. This
   * reflects the fact that a process instance can have multiple incidents, and all error messages
   * can be valid under AND semantics.
   */
  private ProcessInstanceFilter normalizePITopLevelIncidentHashCodes(
      final ProcessInstanceFilter filter) {
    if (filter.incidentErrorHashCode() == null) {
      return filter;
    }

    final var resolvedErrorMessage =
        findErrorMessageByErrorHashCodes(List.of(filter.incidentErrorHashCode()));

    if (resolvedErrorMessage == null || resolvedErrorMessage.isBlank()) {
      return filter.toBuilder().incidentErrorHashCode(null).build();
    }

    final var existingOps =
        filter.errorMessageOperations() != null
            ? new ArrayList<>(filter.errorMessageOperations())
            : new ArrayList<Operation<String>>();

    existingOps.add(Operation.eq(resolvedErrorMessage));

    return filter.toBuilder()
        .incidentErrorHashCode(null)
        .replaceErrorMessageOperations(existingOps)
        .build();
  }

  /**
   * Given a list of OR filters, normalize any sub-filter that uses incidentErrorHashCode by
   * resolving it to a full errorMessage equals operation, and adding it to any existing
   * errorMessage filters, using AND semantics within the subfilter. If the hash code cannot be
   * resolved, skip that clause.
   */
  private List<ProcessInstanceFilter> normalizePIOrFilterErrorHashCodes(
      final List<ProcessInstanceFilter> orFilters) {

    final List<ProcessInstanceFilter> normalized = new ArrayList<>();
    for (final var subFilter : orFilters) {
      if (subFilter.incidentErrorHashCode() == null) {
        normalized.add(subFilter);
        continue;
      }

      final var resolvedErrorMessage =
          findErrorMessageByErrorHashCodes(List.of(subFilter.incidentErrorHashCode()));

      if (resolvedErrorMessage == null || resolvedErrorMessage.isBlank()) {
        continue;
      }

      final var existingOps =
          subFilter.errorMessageOperations() != null
              ? new ArrayList<>(subFilter.errorMessageOperations())
              : new ArrayList<Operation<String>>();

      existingOps.add(Operation.eq(resolvedErrorMessage));

      final var updatedSubFilter =
          subFilter.toBuilder()
              .incidentErrorHashCode(null)
              .replaceErrorMessageOperations(existingOps)
              .build();

      normalized.add(updatedSubFilter);
    }
    return normalized;
  }

  private ProcessDefinitionStatisticsFilter normalizePDTopLevelIncidentHashCodes(
      final ProcessDefinitionStatisticsFilter filter) {
    if (filter.incidentErrorHashCodes() == null || filter.incidentErrorHashCodes().isEmpty()) {
      return filter;
    }

    final var resolvedErrorMessage =
        findErrorMessageByErrorHashCodes(filter.incidentErrorHashCodes());

    if (resolvedErrorMessage == null || resolvedErrorMessage.isEmpty()) {
      return filter.toBuilder().incidentErrorHashCodes(null).build();
    }
    final var existingOps =
        filter.errorMessageOperations() != null
            ? new ArrayList<>(filter.errorMessageOperations())
            : new ArrayList<Operation<String>>();

    existingOps.add(Operation.eq(resolvedErrorMessage));

    return filter.toBuilder()
        .incidentErrorHashCodes(null)
        .replaceErrorMessageOperations(existingOps)
        .build();
  }

  private List<ProcessDefinitionStatisticsFilter> normalizePDOrFilterErrorHashCodes(
      final List<ProcessDefinitionStatisticsFilter> orFilters) {

    final List<ProcessDefinitionStatisticsFilter> normalized = new ArrayList<>();
    for (final var subFilter : orFilters) {
      if (subFilter.incidentErrorHashCodes() == null
          || subFilter.incidentErrorHashCodes().isEmpty()) {
        normalized.add(subFilter);
        continue;
      }

      final var resolvedErrorMessage =
          findErrorMessageByErrorHashCodes(subFilter.incidentErrorHashCodes());

      if (resolvedErrorMessage == null || resolvedErrorMessage.isBlank()) {
        continue;
      }

      final var existingOps =
          subFilter.errorMessageOperations() != null
              ? new ArrayList<>(subFilter.errorMessageOperations())
              : new ArrayList<Operation<String>>();

      existingOps.add(Operation.eq(resolvedErrorMessage));

      final var updatedSubFilter =
          subFilter.toBuilder()
              .incidentErrorHashCodes(null)
              .replaceErrorMessageOperations(existingOps)
              .build();

      normalized.add(updatedSubFilter);
    }
    return normalized;
  }

  private String findErrorMessageByErrorHashCodes(final List<Integer> hashCodes) {
    if (hashCodes == null || hashCodes.isEmpty()) {
      return null;
    }

    final var incidentFilter =
        FilterBuilders.incident(
            f ->
                f.errorMessageHashOperations(FilterUtil.mapDefaultToOperation(hashCodes))
                    .states(IncidentState.ACTIVE.name()));

    final var incidentResult = searchIncidents(IncidentQuery.of(f -> f.filter(incidentFilter)));

    if (incidentResult.items().isEmpty()) {
      return null;
    }

    final var incident = incidentResult.items().getFirst();

    if (incident.errorMessage() == null || incident.errorMessage().isBlank()) {
      return null;
    }
    return incident.errorMessage();
  }
}
