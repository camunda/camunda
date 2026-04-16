/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search;

import static io.camunda.gateway.mapping.http.ResponseMapper.formatDate;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toMap;

import io.camunda.authentication.entity.CamundaUserDTO;
import io.camunda.gateway.mapping.http.search.contract.AuditLogContractAdapter;
import io.camunda.gateway.mapping.http.search.contract.AuthorizationContractAdapter;
import io.camunda.gateway.mapping.http.search.contract.BatchOperationItemResponseContractAdapter;
import io.camunda.gateway.mapping.http.search.contract.BatchOperationResponseContractAdapter;
import io.camunda.gateway.mapping.http.search.contract.ClusterVariableContractAdapter;
import io.camunda.gateway.mapping.http.search.contract.CorrelatedMessageSubscriptionContractAdapter;
import io.camunda.gateway.mapping.http.search.contract.DecisionDefinitionContractAdapter;
import io.camunda.gateway.mapping.http.search.contract.DecisionInstanceContractAdapter;
import io.camunda.gateway.mapping.http.search.contract.DecisionRequirementsContractAdapter;
import io.camunda.gateway.mapping.http.search.contract.ElementInstanceContractAdapter;
import io.camunda.gateway.mapping.http.search.contract.FormContractAdapter;
import io.camunda.gateway.mapping.http.search.contract.GlobalTaskListenerContractAdapter;
import io.camunda.gateway.mapping.http.search.contract.GroupContractAdapter;
import io.camunda.gateway.mapping.http.search.contract.IncidentContractAdapter;
import io.camunda.gateway.mapping.http.search.contract.JobContractAdapter;
import io.camunda.gateway.mapping.http.search.contract.MappingRuleContractAdapter;
import io.camunda.gateway.mapping.http.search.contract.MemberContractAdapter;
import io.camunda.gateway.mapping.http.search.contract.MessageSubscriptionContractAdapter;
import io.camunda.gateway.mapping.http.search.contract.ProcessDefinitionContractAdapter;
import io.camunda.gateway.mapping.http.search.contract.ProcessInstanceCallHierarchyEntryContractAdapter;
import io.camunda.gateway.mapping.http.search.contract.ProcessInstanceContractAdapter;
import io.camunda.gateway.mapping.http.search.contract.RoleContractAdapter;
import io.camunda.gateway.mapping.http.search.contract.StrictSearchQueryPage;
import io.camunda.gateway.mapping.http.search.contract.StrictSearchQueryResult;
import io.camunda.gateway.mapping.http.search.contract.TenantContractAdapter;
import io.camunda.gateway.mapping.http.search.contract.UserContractAdapter;
import io.camunda.gateway.mapping.http.search.contract.UserTaskContractAdapter;
import io.camunda.gateway.mapping.http.search.contract.VariableContractAdapter;
import io.camunda.gateway.mapping.http.util.KeyUtil;
import io.camunda.gateway.protocol.model.CamundaUser;
import io.camunda.gateway.protocol.model.DecisionInstance;
import io.camunda.gateway.protocol.model.DecisionInstanceGetQuery;
import io.camunda.gateway.protocol.model.GlobalJobStatisticsQuery;
import io.camunda.gateway.protocol.model.IncidentProcessInstanceStatisticsByDefinition;
import io.camunda.gateway.protocol.model.IncidentProcessInstanceStatisticsByDefinitionQueryResult;
import io.camunda.gateway.protocol.model.IncidentProcessInstanceStatisticsByError;
import io.camunda.gateway.protocol.model.IncidentProcessInstanceStatisticsByErrorQueryResult;
import io.camunda.gateway.protocol.model.JobErrorStatisticsItem;
import io.camunda.gateway.protocol.model.JobErrorStatisticsQueryResult;
import io.camunda.gateway.protocol.model.JobTimeSeriesStatisticsItem;
import io.camunda.gateway.protocol.model.JobTimeSeriesStatisticsQueryResult;
import io.camunda.gateway.protocol.model.JobTypeStatisticsItem;
import io.camunda.gateway.protocol.model.JobTypeStatisticsQueryResult;
import io.camunda.gateway.protocol.model.JobWorkerStatisticsItem;
import io.camunda.gateway.protocol.model.JobWorkerStatisticsQueryResult;
import io.camunda.gateway.protocol.model.ProcessDefinitionElementStatisticsQueryResult;
import io.camunda.gateway.protocol.model.ProcessDefinitionInstanceStatistics;
import io.camunda.gateway.protocol.model.ProcessDefinitionInstanceStatisticsQueryResult;
import io.camunda.gateway.protocol.model.ProcessDefinitionInstanceVersionStatistics;
import io.camunda.gateway.protocol.model.ProcessDefinitionInstanceVersionStatisticsQueryResult;
import io.camunda.gateway.protocol.model.ProcessDefinitionMessageSubscriptionStatistics;
import io.camunda.gateway.protocol.model.ProcessDefinitionMessageSubscriptionStatisticsQueryResult;
import io.camunda.gateway.protocol.model.ProcessElementStatistics;
import io.camunda.gateway.protocol.model.ProcessInstanceElementStatisticsQuery;
import io.camunda.gateway.protocol.model.ProcessInstanceSequenceFlow;
import io.camunda.gateway.protocol.model.ProcessInstanceSequenceFlowsQuery;
import io.camunda.gateway.protocol.model.SearchQueryPageResponse;
import io.camunda.gateway.protocol.model.StatusMetric;
import io.camunda.gateway.protocol.model.Tenant;
import io.camunda.gateway.protocol.model.UsageMetricsResponse;
import io.camunda.gateway.protocol.model.UsageMetricsResponseItem;
import io.camunda.search.entities.AuditLogEntity;
import io.camunda.search.entities.AuthorizationEntity;
import io.camunda.search.entities.BatchOperationEntity;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemEntity;
import io.camunda.search.entities.ClusterVariableEntity;
import io.camunda.search.entities.CorrelatedMessageSubscriptionEntity;
import io.camunda.search.entities.DecisionDefinitionEntity;
import io.camunda.search.entities.DecisionInstanceEntity;
import io.camunda.search.entities.DecisionRequirementsEntity;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.FormEntity;
import io.camunda.search.entities.GlobalJobStatisticsEntity;
import io.camunda.search.entities.GlobalListenerEntity;
import io.camunda.search.entities.GroupEntity;
import io.camunda.search.entities.GroupMemberEntity;
import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.entities.IncidentProcessInstanceStatisticsByDefinitionEntity;
import io.camunda.search.entities.IncidentProcessInstanceStatisticsByErrorEntity;
import io.camunda.search.entities.JobEntity;
import io.camunda.search.entities.JobErrorStatisticsEntity;
import io.camunda.search.entities.JobTimeSeriesStatisticsEntity;
import io.camunda.search.entities.JobTypeStatisticsEntity;
import io.camunda.search.entities.JobWorkerStatisticsEntity;
import io.camunda.search.entities.MappingRuleEntity;
import io.camunda.search.entities.MessageSubscriptionEntity;
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.entities.ProcessDefinitionInstanceStatisticsEntity;
import io.camunda.search.entities.ProcessDefinitionInstanceVersionStatisticsEntity;
import io.camunda.search.entities.ProcessDefinitionMessageSubscriptionStatisticsEntity;
import io.camunda.search.entities.ProcessFlowNodeStatisticsEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.entities.RoleEntity;
import io.camunda.search.entities.RoleMemberEntity;
import io.camunda.search.entities.SequenceFlowEntity;
import io.camunda.search.entities.TenantEntity;
import io.camunda.search.entities.TenantMemberEntity;
import io.camunda.search.entities.UsageMetricStatisticsEntity;
import io.camunda.search.entities.UsageMetricStatisticsEntity.UsageMetricStatisticsEntityTenant;
import io.camunda.search.entities.UsageMetricTUStatisticsEntity;
import io.camunda.search.entities.UsageMetricTUStatisticsEntity.UsageMetricTUStatisticsEntityTenant;
import io.camunda.search.entities.UserEntity;
import io.camunda.search.entities.UserTaskEntity;
import io.camunda.search.entities.VariableEntity;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.entity.ClusterMetadata.AppName;
import io.camunda.zeebe.util.collection.Tuple;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SearchQueryResponseMapper {
  private static final Logger LOGGER = LoggerFactory.getLogger(SearchQueryResponseMapper.class);

  private SearchQueryResponseMapper() {}

  public static UsageMetricsResponse toUsageMetricsResponse(
      final SearchQueryResult<Tuple<UsageMetricStatisticsEntity, UsageMetricTUStatisticsEntity>>
          result,
      final boolean withTenants) {
    final var tuple = result.items().getFirst();
    final var statistics = tuple.getLeft();
    final var tuStatistics = tuple.getRight();

    Map<String, UsageMetricsResponseItem> tenants = Map.of();

    if (withTenants) {
      final Map<String, UsageMetricStatisticsEntityTenant> tenants1 = statistics.tenants();
      final Map<String, UsageMetricTUStatisticsEntityTenant> tenants2 = tuStatistics.tenants();
      final var allTenantKeys = new HashSet<>(tenants1.keySet());
      allTenantKeys.addAll(tenants2.keySet());

      final var mergedTenants =
          allTenantKeys.stream()
              .collect(
                  Collectors.toMap(
                      key -> key,
                      key -> {
                        final UsageMetricStatisticsEntityTenant stats = tenants1.get(key);
                        final UsageMetricTUStatisticsEntityTenant tuStats = tenants2.get(key);
                        return new UsageMetricsResponseItem()
                            .processInstances(stats != null ? stats.rpi() : 0L)
                            .decisionInstances(stats != null ? stats.edi() : 0L)
                            .assignees(tuStats != null ? tuStats.tu() : 0L);
                      }));
      if (!mergedTenants.isEmpty()) {
        tenants = mergedTenants;
      }
    }

    return new UsageMetricsResponse()
        .processInstances(statistics.totalRpi())
        .decisionInstances(statistics.totalEdi())
        .assignees(tuStatistics.totalTu())
        .activeTenants(statistics.at())
        .tenants(tenants);
  }

  public static <T> T toProcessDefinitionSearchQueryResponse(
      final SearchQueryResult<ProcessDefinitionEntity> result) {
    return adaptType(
        new StrictSearchQueryResult<>(
            ofNullable(result.items())
                .map(items -> items.stream().map(ProcessDefinitionContractAdapter::adapt).toList())
                .orElseGet(Collections::emptyList),
            toStrictSearchQueryPage(result)));
  }

  public static ProcessDefinitionElementStatisticsQueryResult
      toProcessDefinitionElementStatisticsResult(
          final List<ProcessFlowNodeStatisticsEntity> result) {
    return new ProcessDefinitionElementStatisticsQueryResult()
        .items(
            result.stream()
                .map(SearchQueryResponseMapper::toProcessElementStatisticsResult)
                .toList());
  }

  public static ProcessInstanceElementStatisticsQuery toProcessInstanceElementStatisticsResult(
      final List<ProcessFlowNodeStatisticsEntity> result) {
    return new ProcessInstanceElementStatisticsQuery()
        .items(
            result.stream()
                .map(SearchQueryResponseMapper::toProcessElementStatisticsResult)
                .toList());
  }

  private static ProcessElementStatistics toProcessElementStatisticsResult(
      final ProcessFlowNodeStatisticsEntity result) {
    return new ProcessElementStatistics()
        .elementId(result.flowNodeId())
        .active(result.active())
        .canceled(result.canceled())
        .incidents(result.incidents())
        .completed(result.completed());
  }

  public static ProcessDefinitionInstanceStatisticsQueryResult
      toProcessInstanceStatisticsQueryResult(
          final SearchQueryResult<ProcessDefinitionInstanceStatisticsEntity> result) {
    return new ProcessDefinitionInstanceStatisticsQueryResult()
        .page(toPage(result))
        .items(
            result.items().stream()
                .map(SearchQueryResponseMapper::toProcessInstanceStatisticsResult)
                .toList());
  }

  public static ProcessDefinitionInstanceVersionStatisticsQueryResult
      toProcessInstanceVersionStatisticsQueryResult(
          final SearchQueryResult<ProcessDefinitionInstanceVersionStatisticsEntity> result) {
    return new ProcessDefinitionInstanceVersionStatisticsQueryResult()
        .page(toPage(result))
        .items(
            result.items().stream()
                .map(SearchQueryResponseMapper::toProcessInstanceVersionStatisticsResult)
                .toList());
  }

  private static ProcessDefinitionInstanceStatistics toProcessInstanceStatisticsResult(
      final ProcessDefinitionInstanceStatisticsEntity result) {
    return new ProcessDefinitionInstanceStatistics()
        .processDefinitionId(result.processDefinitionId())
        .tenantId(result.tenantId())
        .latestProcessDefinitionName(result.latestProcessDefinitionName())
        .hasMultipleVersions(result.hasMultipleVersions())
        .activeInstancesWithoutIncidentCount(result.activeInstancesWithoutIncidentCount())
        .activeInstancesWithIncidentCount(result.activeInstancesWithIncidentCount());
  }

  private static ProcessDefinitionInstanceVersionStatistics
      toProcessInstanceVersionStatisticsResult(
          final ProcessDefinitionInstanceVersionStatisticsEntity result) {
    return new ProcessDefinitionInstanceVersionStatistics()
        .processDefinitionId(result.processDefinitionId())
        .processDefinitionKey(KeyUtil.keyToString(result.processDefinitionKey()))
        .processDefinitionName(result.processDefinitionName())
        .tenantId(result.tenantId())
        .processDefinitionVersion(result.processDefinitionVersion())
        .activeInstancesWithIncidentCount(result.activeInstancesWithIncidentCount())
        .activeInstancesWithoutIncidentCount(result.activeInstancesWithoutIncidentCount());
  }

  public static IncidentProcessInstanceStatisticsByErrorQueryResult
      toIncidentProcessInstanceStatisticsByErrorResult(
          final SearchQueryResult<IncidentProcessInstanceStatisticsByErrorEntity> result) {
    return new IncidentProcessInstanceStatisticsByErrorQueryResult()
        .page(toPage(result))
        .items(
            result.items().stream()
                .map(SearchQueryResponseMapper::toIncidentProcessInstanceStatisticsByErrorResult)
                .toList());
  }

  private static IncidentProcessInstanceStatisticsByError
      toIncidentProcessInstanceStatisticsByErrorResult(
          final IncidentProcessInstanceStatisticsByErrorEntity result) {
    return new IncidentProcessInstanceStatisticsByError()
        .errorHashCode(result.errorHashCode())
        .errorMessage(result.errorMessage())
        .activeInstancesWithErrorCount(result.activeInstancesWithErrorCount());
  }

  public static IncidentProcessInstanceStatisticsByDefinitionQueryResult
      toIncidentProcessInstanceStatisticsByDefinitionQueryResult(
          final SearchQueryResult<IncidentProcessInstanceStatisticsByDefinitionEntity> result) {
    return new IncidentProcessInstanceStatisticsByDefinitionQueryResult()
        .page(toPage(result))
        .items(
            result.items().stream()
                .map(
                    SearchQueryResponseMapper
                        ::toIncidentProcessInstanceStatisticsByDefinitionResult)
                .toList());
  }

  private static IncidentProcessInstanceStatisticsByDefinition
      toIncidentProcessInstanceStatisticsByDefinitionResult(
          final IncidentProcessInstanceStatisticsByDefinitionEntity result) {
    return new IncidentProcessInstanceStatisticsByDefinition()
        .processDefinitionId(result.processDefinitionId())
        .processDefinitionKey(KeyUtil.keyToString(result.processDefinitionKey()))
        .processDefinitionName(result.processDefinitionName())
        .processDefinitionVersion(result.processDefinitionVersion())
        .tenantId(result.tenantId())
        .activeInstancesWithErrorCount(result.activeInstancesWithErrorCount());
  }

  public static ProcessInstanceSequenceFlowsQuery toSequenceFlowsResult(
      final List<SequenceFlowEntity> result) {
    return new ProcessInstanceSequenceFlowsQuery()
        .items(
            result.stream()
                .map(SearchQueryResponseMapper::toProcessInstanceSequenceFlowResult)
                .toList());
  }

  private static ProcessInstanceSequenceFlow toProcessInstanceSequenceFlowResult(
      final SequenceFlowEntity result) {
    return new ProcessInstanceSequenceFlow()
        .sequenceFlowId(result.sequenceFlowId())
        .processInstanceKey(KeyUtil.keyToString(result.processInstanceKey()))
        .rootProcessInstanceKey(KeyUtil.keyToString(result.rootProcessInstanceKey()))
        .processDefinitionKey(KeyUtil.keyToString(result.processDefinitionKey()))
        .processDefinitionId(result.processDefinitionId())
        .elementId(result.flowNodeId())
        .tenantId(result.tenantId());
  }

  public static <T> T toProcessInstanceSearchQueryResponse(
      final SearchQueryResult<ProcessInstanceEntity> result) {
    return adaptType(
        new StrictSearchQueryResult<>(
            ofNullable(result.items())
                .map(items -> items.stream().map(ProcessInstanceContractAdapter::adapt).toList())
                .orElseGet(Collections::emptyList),
            toStrictSearchQueryPage(result)));
  }

  public static <T> T toJobSearchQueryResponse(final SearchQueryResult<JobEntity> result) {
    return adaptType(
        new StrictSearchQueryResult<>(
            ofNullable(result.items())
                .map(items -> items.stream().map(JobContractAdapter::adapt).toList())
                .orElseGet(Collections::emptyList),
            toStrictSearchQueryPage(result)));
  }

  public static <T> T toRoleSearchQueryResponse(final SearchQueryResult<RoleEntity> result) {
    return adaptType(
        new StrictSearchQueryResult<>(
            ofNullable(result.items())
                .map(RoleContractAdapter::adapt)
                .orElseGet(Collections::emptyList),
            toStrictSearchQueryPage(result)));
  }

  public static <T> T toRoleGroupSearchQueryResponse(
      final SearchQueryResult<RoleMemberEntity> result) {
    return adaptType(
        new StrictSearchQueryResult<>(
            ofNullable(result.items())
                .map(MemberContractAdapter::toRoleGroups)
                .orElseGet(Collections::emptyList),
            toStrictSearchQueryPage(result)));
  }

  public static <T> T toRoleUserSearchQueryResponse(
      final SearchQueryResult<RoleMemberEntity> result) {
    return adaptType(
        new StrictSearchQueryResult<>(
            ofNullable(result.items())
                .map(MemberContractAdapter::toRoleUsers)
                .orElseGet(Collections::emptyList),
            toStrictSearchQueryPage(result)));
  }

  public static <T> T toRoleClientSearchQueryResponse(
      final SearchQueryResult<RoleMemberEntity> result) {
    return adaptType(
        new StrictSearchQueryResult<>(
            ofNullable(result.items())
                .map(MemberContractAdapter::toRoleClients)
                .orElseGet(Collections::emptyList),
            toStrictSearchQueryPage(result)));
  }

  public static <T> T toGroupSearchQueryResponse(final SearchQueryResult<GroupEntity> result) {
    return adaptType(
        new StrictSearchQueryResult<>(
            ofNullable(result.items())
                .map(GroupContractAdapter::adapt)
                .orElseGet(Collections::emptyList),
            toStrictSearchQueryPage(result)));
  }

  public static <T> T toGroupUserSearchQueryResponse(
      final SearchQueryResult<GroupMemberEntity> result) {
    return adaptType(
        new StrictSearchQueryResult<>(
            ofNullable(result.items())
                .map(MemberContractAdapter::toGroupUsers)
                .orElseGet(Collections::emptyList),
            toStrictSearchQueryPage(result)));
  }

  public static <T> T toGroupClientSearchQueryResponse(
      final SearchQueryResult<GroupMemberEntity> result) {
    return adaptType(
        new StrictSearchQueryResult<>(
            ofNullable(result.items())
                .map(MemberContractAdapter::toGroupClients)
                .orElseGet(Collections::emptyList),
            toStrictSearchQueryPage(result)));
  }

  public static <T> T toTenantSearchQueryResponse(final SearchQueryResult<TenantEntity> result) {
    return adaptType(
        new StrictSearchQueryResult<>(
            ofNullable(result.items())
                .map(TenantContractAdapter::adapt)
                .orElseGet(Collections::emptyList),
            toStrictSearchQueryPage(result)));
  }

  public static <T> T toTenantGroupSearchQueryResponse(
      final SearchQueryResult<TenantMemberEntity> result) {
    return adaptType(
        new StrictSearchQueryResult<>(
            ofNullable(result.items())
                .map(MemberContractAdapter::toTenantGroups)
                .orElseGet(Collections::emptyList),
            toStrictSearchQueryPage(result)));
  }

  public static <T> T toTenantUserSearchQueryResponse(
      final SearchQueryResult<TenantMemberEntity> result) {
    return adaptType(
        new StrictSearchQueryResult<>(
            ofNullable(result.items())
                .map(MemberContractAdapter::toTenantUsers)
                .orElseGet(Collections::emptyList),
            toStrictSearchQueryPage(result)));
  }

  public static <T> T toTenantClientSearchQueryResponse(
      final SearchQueryResult<TenantMemberEntity> result) {
    return adaptType(
        new StrictSearchQueryResult<>(
            ofNullable(result.items())
                .map(MemberContractAdapter::toTenantClients)
                .orElseGet(Collections::emptyList),
            toStrictSearchQueryPage(result)));
  }

  public static <T> T toMappingRuleSearchQueryResponse(
      final SearchQueryResult<MappingRuleEntity> result) {
    return adaptType(
        new StrictSearchQueryResult<>(
            ofNullable(result.items())
                .map(MappingRuleContractAdapter::adapt)
                .orElseGet(Collections::emptyList),
            toStrictSearchQueryPage(result)));
  }

  public static <T> T toDecisionDefinitionSearchQueryResponse(
      final SearchQueryResult<DecisionDefinitionEntity> result) {
    return adaptType(
        new StrictSearchQueryResult<>(
            ofNullable(result.items())
                .map(items -> items.stream().map(DecisionDefinitionContractAdapter::adapt).toList())
                .orElseGet(Collections::emptyList),
            toStrictSearchQueryPage(result)));
  }

  public static <T> T toDecisionRequirementsSearchQueryResponse(
      final SearchQueryResult<DecisionRequirementsEntity> result) {
    return adaptType(
        new StrictSearchQueryResult<>(
            ofNullable(result.items())
                .map(
                    items ->
                        items.stream().map(DecisionRequirementsContractAdapter::adapt).toList())
                .orElseGet(Collections::emptyList),
            toStrictSearchQueryPage(result)));
  }

  public static <T> T toElementInstanceSearchQueryResponse(
      final SearchQueryResult<FlowNodeInstanceEntity> result) {
    return adaptType(
        new StrictSearchQueryResult<>(
            ofNullable(result.items())
                .map(items -> items.stream().map(ElementInstanceContractAdapter::adapt).toList())
                .orElseGet(Collections::emptyList),
            toStrictSearchQueryPage(result)));
  }

  public static <T> T toDecisionInstanceSearchQueryResponse(
      final SearchQueryResult<DecisionInstanceEntity> result) {
    return adaptType(
        new StrictSearchQueryResult<>(
            ofNullable(result.items())
                .map(DecisionInstanceContractAdapter::toSearchProjections)
                .orElseGet(Collections::emptyList),
            toStrictSearchQueryPage(result)));
  }

  public static <T> T toUserTaskSearchQueryResponse(
      final SearchQueryResult<UserTaskEntity> result) {
    return adaptType(
        new StrictSearchQueryResult<>(
            ofNullable(result.items())
                .map(items -> items.stream().map(UserTaskContractAdapter::adapt).toList())
                .orElseGet(Collections::emptyList),
            toStrictSearchQueryPage(result)));
  }

  public static <T> T toUserSearchQueryResponse(final SearchQueryResult<UserEntity> result) {
    return adaptType(
        new StrictSearchQueryResult<>(
            ofNullable(result.items())
                .map(items -> items.stream().map(UserContractAdapter::adapt).toList())
                .orElseGet(Collections::emptyList),
            toStrictSearchQueryPage(result)));
  }

  public static <T> T toBatchOperationSearchQueryResult(
      final SearchQueryResult<BatchOperationEntity> result) {
    return adaptType(
        new StrictSearchQueryResult<>(
            ofNullable(result.items())
                .map(
                    items ->
                        items.stream().map(BatchOperationResponseContractAdapter::adapt).toList())
                .orElseGet(Collections::emptyList),
            toStrictSearchQueryPage(result)));
  }

  public static <T> T toBatchOperationItemSearchQueryResult(
      final SearchQueryResult<BatchOperationItemEntity> result) {
    return adaptType(
        new StrictSearchQueryResult<>(
            ofNullable(result.items())
                .map(
                    items ->
                        items.stream()
                            .map(BatchOperationItemResponseContractAdapter::adapt)
                            .toList())
                .orElseGet(Collections::emptyList),
            toStrictSearchQueryPage(result)));
  }

  public static <T> T toIncidentSearchQueryResponse(
      final SearchQueryResult<IncidentEntity> result) {
    return adaptType(
        new StrictSearchQueryResult<>(
            ofNullable(result.items())
                .map(items -> items.stream().map(IncidentContractAdapter::adapt).toList())
                .orElseGet(Collections::emptyList),
            toStrictSearchQueryPage(result)));
  }

  public static <T> T toMessageSubscriptionSearchQueryResponse(
      final SearchQueryResult<MessageSubscriptionEntity> result) {
    return adaptType(
        new StrictSearchQueryResult<>(
            ofNullable(result.items())
                .map(
                    items -> items.stream().map(MessageSubscriptionContractAdapter::adapt).toList())
                .orElseGet(Collections::emptyList),
            toStrictSearchQueryPage(result)));
  }

  public static <T> T toCorrelatedMessageSubscriptionSearchQueryResponse(
      final SearchQueryResult<CorrelatedMessageSubscriptionEntity> result) {
    return adaptType(
        new StrictSearchQueryResult<>(
            ofNullable(result.items())
                .map(
                    items ->
                        items.stream()
                            .map(CorrelatedMessageSubscriptionContractAdapter::adapt)
                            .toList())
                .orElseGet(Collections::emptyList),
            toStrictSearchQueryPage(result)));
  }

  private static SearchQueryPageResponse toPage(final SearchQueryResult<?> result) {
    return new SearchQueryPageResponse()
        .totalItems(result.total())
        .hasMoreTotalItems(result.hasMoreTotalItems())
        .startCursor(result.startCursor())
        .endCursor(result.endCursor());
  }

  private static StrictSearchQueryPage toStrictSearchQueryPage(final SearchQueryResult<?> result) {
    return new StrictSearchQueryPage(
        result.total(), result.hasMoreTotalItems(), result.startCursor(), result.endCursor());
  }

  public static <T> T toProcessDefinition(final ProcessDefinitionEntity entity) {
    return adaptType(ProcessDefinitionContractAdapter.adapt(entity));
  }

  public static <T> T toProcessInstance(final ProcessInstanceEntity entity) {
    return adaptType(ProcessInstanceContractAdapter.adapt(entity));
  }

  public static <T> T toBatchOperations(final List<BatchOperationEntity> batchOperations) {
    return adaptType(
        batchOperations.stream().map(SearchQueryResponseMapper::toBatchOperation).toList());
  }

  public static <T> T toBatchOperation(final BatchOperationEntity entity) {
    return adaptType(BatchOperationResponseContractAdapter.adapt(entity));
  }

  public static <T> T toBatchOperationItemSearchQueryResult(
      final List<BatchOperationItemEntity> batchOperations) {
    return adaptType(
        new StrictSearchQueryResult<>(
            batchOperations.stream().map(SearchQueryResponseMapper::toBatchOperationItem).toList(),
            null));
  }

  public static <T> T toBatchOperationItems(
      final List<BatchOperationItemEntity> batchOperationItems) {
    return adaptType(
        batchOperationItems.stream().map(SearchQueryResponseMapper::toBatchOperationItem).toList());
  }

  public static <T> T toBatchOperationItem(final BatchOperationItemEntity entity) {
    return adaptType(BatchOperationItemResponseContractAdapter.adapt(entity));
  }

  public static <T> T toRole(final RoleEntity entity) {
    return adaptType(RoleContractAdapter.adapt(entity));
  }

  public static <T> T toGroup(final GroupEntity entity) {
    return adaptType(GroupContractAdapter.adapt(entity));
  }

  public static <T> T toTenant(final TenantEntity entity) {
    return adaptType(TenantContractAdapter.adapt(entity));
  }

  public static <T> T toMappingRule(final MappingRuleEntity entity) {
    return adaptType(MappingRuleContractAdapter.adapt(entity));
  }

  public static <T> T toElementInstance(final FlowNodeInstanceEntity entity) {
    return adaptType(ElementInstanceContractAdapter.adapt(entity));
  }

  public static <T> T toDecisionDefinition(final DecisionDefinitionEntity entity) {
    return adaptType(DecisionDefinitionContractAdapter.adapt(entity));
  }

  public static <T> T toDecisionRequirements(final DecisionRequirementsEntity entity) {
    return adaptType(DecisionRequirementsContractAdapter.adapt(entity));
  }

  public static <T> T toIncident(final IncidentEntity entity) {
    return adaptType(IncidentContractAdapter.adapt(entity));
  }

  public static <T> T toUserTask(final UserTaskEntity entity) {
    return adaptType(UserTaskContractAdapter.adapt(entity));
  }

  public static <T> T toFormItem(final FormEntity entity) {
    return adaptType(FormContractAdapter.adapt(entity));
  }

  public static <T> T toUser(final UserEntity entity) {
    return adaptType(UserContractAdapter.adapt(entity));
  }

  public static CamundaUser toCamundaUser(final CamundaUserDTO camundaUser) {
    return new CamundaUser()
        .username(camundaUser.username())
        .displayName(camundaUser.displayName())
        .email(camundaUser.email())
        .authorizedComponents(camundaUser.authorizedComponents())
        .tenants(
            camundaUser.tenants().stream()
                .map(
                    t ->
                        new Tenant()
                            .name(t.name())
                            .tenantId(t.tenantId())
                            .description(t.description()))
                .toList())
        .groups(camundaUser.groups())
        .roles(camundaUser.roles())
        .salesPlanType(camundaUser.salesPlanType())
        .c8Links(toCamundaUserResultC8Links(camundaUser.c8Links()))
        .canLogout(camundaUser.canLogout());
  }

  private static Map<String, String> toCamundaUserResultC8Links(
      final Map<AppName, String> c8Links) {
    return c8Links.entrySet().stream()
        .collect(toMap(e -> e.getKey().getValue(), Map.Entry::getValue, (v1, v2) -> v1));
  }

  private static List<DecisionInstance> toDecisionInstances(
      final List<DecisionInstanceEntity> instances) {
    return DecisionInstanceContractAdapter.toSearchProjections(instances);
  }

  public static <T> T toDecisionInstance(final DecisionInstanceEntity entity) {
    return adaptType(DecisionInstanceContractAdapter.toSearchProjection(entity));
  }

  public static DecisionInstanceGetQuery toDecisionInstanceGetQueryResponse(
      final DecisionInstanceEntity entity) {
    return DecisionInstanceContractAdapter.toGetProjection(entity);
  }

  public static <T> T toVariableSearchQueryResponse(
      final SearchQueryResult<VariableEntity> result, final boolean truncateValues) {
    return adaptType(
        new StrictSearchQueryResult<>(
            ofNullable(result.items())
                .map(items -> VariableContractAdapter.toSearchProjections(items, truncateValues))
                .orElseGet(Collections::emptyList),
            toStrictSearchQueryPage(result)));
  }

  public static <T> T toVariableItem(final VariableEntity variableEntity) {
    return adaptType(VariableContractAdapter.toItemProjection(variableEntity));
  }

  public static <T> T toClusterVariableSearchQueryResponse(
      final SearchQueryResult<ClusterVariableEntity> result, final boolean truncateValues) {
    return adaptType(
        new StrictSearchQueryResult<>(
            ofNullable(result.items())
                .map(
                    items ->
                        ClusterVariableContractAdapter.toSearchProjections(items, truncateValues))
                .orElseGet(Collections::emptyList),
            toStrictSearchQueryPage(result)));
  }

  public static <T> T toClusterVariableSearchResult(
      final ClusterVariableEntity clusterVariableEntity, final boolean truncateValues) {
    return adaptType(
        ClusterVariableContractAdapter.toSearchProjection(clusterVariableEntity, truncateValues));
  }

  public static <T> T toClusterVariableResult(final ClusterVariableEntity clusterVariableEntity) {
    return adaptType(ClusterVariableContractAdapter.toItemProjection(clusterVariableEntity));
  }

  public static <T> T toAuthorizationSearchQueryResponse(
      final SearchQueryResult<AuthorizationEntity> result) {
    return adaptType(
        new StrictSearchQueryResult<>(
            ofNullable(result.items())
                .map(items -> items.stream().map(AuthorizationContractAdapter::adapt).toList())
                .orElseGet(Collections::emptyList),
            toStrictSearchQueryPage(result)));
  }

  public static <T> T toAuthorization(final AuthorizationEntity entity) {
    return adaptType(AuthorizationContractAdapter.adapt(entity));
  }

  public static <T> T toAuditLogSearchQueryResponse(
      final SearchQueryResult<AuditLogEntity> result) {
    return adaptType(
        new StrictSearchQueryResult<>(
            ofNullable(result.items())
                .map(items -> items.stream().map(AuditLogContractAdapter::adapt).toList())
                .orElseGet(Collections::emptyList),
            toStrictSearchQueryPage(result)));
  }

  public static <T> T toAuditLog(final AuditLogEntity entity) {
    return adaptType(AuditLogContractAdapter.adapt(entity));
  }

  public static <T> T toProcessInstanceCallHierarchyEntries(
      final List<ProcessInstanceEntity> processInstanceEntities) {
    return adaptType(
        processInstanceEntities.stream()
            .map(SearchQueryResponseMapper::toProcessInstanceCallHierarchyEntry)
            .toList());
  }

  public static <T> T toProcessInstanceCallHierarchyEntry(
      final ProcessInstanceEntity processInstanceEntity) {
    return adaptType(ProcessInstanceCallHierarchyEntryContractAdapter.adapt(processInstanceEntity));
  }

  private static List<ProcessDefinitionMessageSubscriptionStatistics>
      toProcessDefinitionMessageSubscriptionStatisticsItems(
          final List<ProcessDefinitionMessageSubscriptionStatisticsEntity> entities) {
    return entities.stream()
        .map(
            e ->
                new ProcessDefinitionMessageSubscriptionStatistics()
                    .processDefinitionId(e.processDefinitionId())
                    .tenantId(e.tenantId())
                    .processDefinitionKey(KeyUtil.keyToString(e.processDefinitionKey()))
                    .processInstancesWithActiveSubscriptions(
                        e.processInstancesWithActiveSubscriptions())
                    .activeSubscriptions(e.activeSubscriptions()))
        .toList();
  }

  public static ProcessDefinitionMessageSubscriptionStatisticsQueryResult
      toProcessDefinitionMessageSubscriptionStatisticsQueryResponse(
          final SearchQueryResult<ProcessDefinitionMessageSubscriptionStatisticsEntity> result) {
    return new ProcessDefinitionMessageSubscriptionStatisticsQueryResult()
        .page(toPage(result))
        .items(
            ofNullable(result.items())
                .map(
                    SearchQueryResponseMapper
                        ::toProcessDefinitionMessageSubscriptionStatisticsItems)
                .orElseGet(Collections::emptyList));
  }

  public static GlobalJobStatisticsQuery toGlobalJobStatisticsQueryResult(
      final GlobalJobStatisticsEntity entity) {
    if (entity == null) {
      return new GlobalJobStatisticsQuery()
          .created(new StatusMetric().count(0L).lastUpdatedAt(null))
          .completed(new StatusMetric().count(0L).lastUpdatedAt(null))
          .failed(new StatusMetric().count(0L).lastUpdatedAt(null))
          .isIncomplete(false);
    }

    return new GlobalJobStatisticsQuery()
        .created(toStatusMetric(entity.created()))
        .completed(toStatusMetric(entity.completed()))
        .failed(toStatusMetric(entity.failed()))
        .isIncomplete(entity.isIncomplete());
  }

  public static JobTypeStatisticsQueryResult toJobTypeStatisticsQueryResult(
      final SearchQueryResult<JobTypeStatisticsEntity> result) {
    return new JobTypeStatisticsQueryResult()
        .page(toPage(result))
        .items(
            result.items().stream()
                .map(SearchQueryResponseMapper::toJobTypeStatisticsItem)
                .toList());
  }

  private static JobTypeStatisticsItem toJobTypeStatisticsItem(
      final JobTypeStatisticsEntity entity) {
    if (entity == null) {
      return new JobTypeStatisticsItem()
          .jobType("")
          .created(new StatusMetric().count(0L).lastUpdatedAt(null))
          .completed(new StatusMetric().count(0L).lastUpdatedAt(null))
          .failed(new StatusMetric().count(0L).lastUpdatedAt(null))
          .workers(0);
    }

    return new JobTypeStatisticsItem()
        .jobType(entity.jobType())
        .created(toStatusMetric(entity.created()))
        .completed(toStatusMetric(entity.completed()))
        .failed(toStatusMetric(entity.failed()))
        .workers(entity.workers());
  }

  public static JobWorkerStatisticsQueryResult toJobWorkerStatisticsQueryResult(
      final SearchQueryResult<JobWorkerStatisticsEntity> result) {
    return new JobWorkerStatisticsQueryResult()
        .page(toPage(result))
        .items(
            result.items().stream()
                .map(SearchQueryResponseMapper::toJobWorkerStatisticsItem)
                .toList());
  }

  private static JobWorkerStatisticsItem toJobWorkerStatisticsItem(
      final JobWorkerStatisticsEntity entity) {
    if (entity == null) {
      return new JobWorkerStatisticsItem()
          .worker("")
          .created(new StatusMetric().count(0L).lastUpdatedAt(null))
          .completed(new StatusMetric().count(0L).lastUpdatedAt(null))
          .failed(new StatusMetric().count(0L).lastUpdatedAt(null));
    }

    return new JobWorkerStatisticsItem()
        .worker(entity.worker())
        .created(toStatusMetric(entity.created()))
        .completed(toStatusMetric(entity.completed()))
        .failed(toStatusMetric(entity.failed()));
  }

  public static JobTimeSeriesStatisticsQueryResult toJobTimeSeriesStatisticsQueryResult(
      final SearchQueryResult<JobTimeSeriesStatisticsEntity> result) {
    return new JobTimeSeriesStatisticsQueryResult()
        .page(toPage(result))
        .items(
            result.items().stream()
                .map(SearchQueryResponseMapper::toJobTimeSeriesStatisticsItem)
                .toList());
  }

  private static JobTimeSeriesStatisticsItem toJobTimeSeriesStatisticsItem(
      final JobTimeSeriesStatisticsEntity entity) {
    if (entity == null) {
      return new JobTimeSeriesStatisticsItem()
          .time("")
          .created(new StatusMetric().count(0L).lastUpdatedAt(null))
          .completed(new StatusMetric().count(0L).lastUpdatedAt(null))
          .failed(new StatusMetric().count(0L).lastUpdatedAt(null));
    }

    return new JobTimeSeriesStatisticsItem()
        .time(formatDate(entity.time()))
        .created(toStatusMetric(entity.created()))
        .completed(toStatusMetric(entity.completed()))
        .failed(toStatusMetric(entity.failed()));
  }

  public static JobErrorStatisticsQueryResult toJobErrorStatisticsQueryResult(
      final SearchQueryResult<JobErrorStatisticsEntity> result) {
    return new JobErrorStatisticsQueryResult()
        .page(toPage(result))
        .items(
            result.items().stream()
                .map(SearchQueryResponseMapper::toJobErrorStatisticsItem)
                .toList());
  }

  private static JobErrorStatisticsItem toJobErrorStatisticsItem(
      final JobErrorStatisticsEntity entity) {
    if (entity == null) {
      return new JobErrorStatisticsItem().errorCode("").errorMessage("").workers(0);
    }

    return new JobErrorStatisticsItem()
        .errorCode(ofNullable(entity.errorCode()).orElse(""))
        .errorMessage(entity.errorMessage())
        .workers(entity.workers());
  }

  private static StatusMetric toStatusMetric(final GlobalJobStatisticsEntity.StatusMetric metric) {
    if (metric == null) {
      return new StatusMetric().count(0L).lastUpdatedAt(null);
    }
    return new StatusMetric()
        .count(metric.count())
        .lastUpdatedAt(formatDate(metric.lastUpdatedAt()));
  }

  public static <T> T toGlobalTaskListenerSearchQueryResponse(
      final SearchQueryResult<GlobalListenerEntity> result) {
    return adaptType(
        new StrictSearchQueryResult<>(
            ofNullable(result.items())
                .map(items -> items.stream().map(GlobalTaskListenerContractAdapter::adapt).toList())
                .orElseGet(Collections::emptyList),
            toStrictSearchQueryPage(result)));
  }

  public static <T> T toGlobalTaskListenerResult(final GlobalListenerEntity entity) {
    return adaptType(GlobalTaskListenerContractAdapter.adapt(entity));
  }

  /**
   * Erased generic cast: bridges strict contract types to protocol model types without runtime
   * overhead. Safe because (1) {@code ResponseEntity} stores the body as {@code Object}, (2) Spring
   * MVC and Jackson serialize based on actual runtime types, and (3) generic casts are erased by
   * the compiler, preventing {@code ClassCastException}. Temporary until all controllers are
   * rewired to the strict-contract pipeline.
   */
  @SuppressWarnings("unchecked")
  private static <T> T adaptType(final Object source) {
    return (T) source;
  }
}
