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
import io.camunda.gateway.mapping.http.search.contract.generated.CamundaUserContract;
import io.camunda.gateway.mapping.http.search.contract.generated.DecisionInstanceContract;
import io.camunda.gateway.mapping.http.search.contract.generated.DecisionInstanceGetQueryContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GlobalJobStatisticsQueryContract;
import io.camunda.gateway.mapping.http.search.contract.generated.IncidentProcessInstanceStatisticsByDefinitionContract;
import io.camunda.gateway.mapping.http.search.contract.generated.IncidentProcessInstanceStatisticsByDefinitionQueryResultContract;
import io.camunda.gateway.mapping.http.search.contract.generated.IncidentProcessInstanceStatisticsByErrorContract;
import io.camunda.gateway.mapping.http.search.contract.generated.IncidentProcessInstanceStatisticsByErrorQueryResultContract;
import io.camunda.gateway.mapping.http.search.contract.generated.JobErrorStatisticsItemContract;
import io.camunda.gateway.mapping.http.search.contract.generated.JobErrorStatisticsQueryResultContract;
import io.camunda.gateway.mapping.http.search.contract.generated.JobTimeSeriesStatisticsItemContract;
import io.camunda.gateway.mapping.http.search.contract.generated.JobTimeSeriesStatisticsQueryResultContract;
import io.camunda.gateway.mapping.http.search.contract.generated.JobTypeStatisticsItemContract;
import io.camunda.gateway.mapping.http.search.contract.generated.JobTypeStatisticsQueryResultContract;
import io.camunda.gateway.mapping.http.search.contract.generated.JobWorkerStatisticsItemContract;
import io.camunda.gateway.mapping.http.search.contract.generated.JobWorkerStatisticsQueryResultContract;
import io.camunda.gateway.mapping.http.search.contract.generated.ProcessDefinitionElementStatisticsQueryResultContract;
import io.camunda.gateway.mapping.http.search.contract.generated.ProcessDefinitionInstanceStatisticsContract;
import io.camunda.gateway.mapping.http.search.contract.generated.ProcessDefinitionInstanceStatisticsQueryResultContract;
import io.camunda.gateway.mapping.http.search.contract.generated.ProcessDefinitionInstanceVersionStatisticsContract;
import io.camunda.gateway.mapping.http.search.contract.generated.ProcessDefinitionInstanceVersionStatisticsQueryResultContract;
import io.camunda.gateway.mapping.http.search.contract.generated.ProcessDefinitionMessageSubscriptionStatisticsContract;
import io.camunda.gateway.mapping.http.search.contract.generated.ProcessDefinitionMessageSubscriptionStatisticsQueryResultContract;
import io.camunda.gateway.mapping.http.search.contract.generated.ProcessElementStatisticsContract;
import io.camunda.gateway.mapping.http.search.contract.generated.ProcessInstanceElementStatisticsQueryContract;
import io.camunda.gateway.mapping.http.search.contract.generated.ProcessInstanceSequenceFlowContract;
import io.camunda.gateway.mapping.http.search.contract.generated.ProcessInstanceSequenceFlowsQueryContract;
import io.camunda.gateway.mapping.http.search.contract.generated.SearchQueryPageResponseContract;
import io.camunda.gateway.mapping.http.search.contract.generated.StatusMetricContract;
import io.camunda.gateway.mapping.http.search.contract.generated.TenantContract;
import io.camunda.gateway.mapping.http.search.contract.generated.UsageMetricsResponseContract;
import io.camunda.gateway.mapping.http.search.contract.generated.UsageMetricsResponseItemContract;
import io.camunda.gateway.mapping.http.util.KeyUtil;
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

  public static UsageMetricsResponseContract toUsageMetricsResponse(
      final SearchQueryResult<Tuple<UsageMetricStatisticsEntity, UsageMetricTUStatisticsEntity>>
          result,
      final boolean withTenants) {
    final var tuple = result.items().getFirst();
    final var statistics = tuple.getLeft();
    final var tuStatistics = tuple.getRight();

    Map<String, UsageMetricsResponseItemContract> tenants = Map.of();

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
                        return new UsageMetricsResponseItemContract(
                            stats != null ? stats.rpi() : 0L,
                            stats != null ? stats.edi() : 0L,
                            tuStats != null ? tuStats.tu() : 0L);
                      }));
      if (!mergedTenants.isEmpty()) {
        tenants = mergedTenants;
      }
    }

    return new UsageMetricsResponseContract(
        statistics.totalRpi(),
        statistics.totalEdi(),
        tuStatistics.totalTu(),
        statistics.at(),
        tenants);
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

  public static ProcessDefinitionElementStatisticsQueryResultContract
      toProcessDefinitionElementStatisticsResult(
          final List<ProcessFlowNodeStatisticsEntity> result) {
    return new ProcessDefinitionElementStatisticsQueryResultContract(
        result.stream().map(SearchQueryResponseMapper::toProcessElementStatisticsResult).toList());
  }

  public static ProcessInstanceElementStatisticsQueryContract
      toProcessInstanceElementStatisticsResult(final List<ProcessFlowNodeStatisticsEntity> result) {
    return new ProcessInstanceElementStatisticsQueryContract(
        result.stream().map(SearchQueryResponseMapper::toProcessElementStatisticsResult).toList());
  }

  private static ProcessElementStatisticsContract toProcessElementStatisticsResult(
      final ProcessFlowNodeStatisticsEntity result) {
    return new ProcessElementStatisticsContract(
        result.flowNodeId(),
        result.active(),
        result.canceled(),
        result.incidents(),
        result.completed());
  }

  public static ProcessDefinitionInstanceStatisticsQueryResultContract
      toProcessInstanceStatisticsQueryResult(
          final SearchQueryResult<ProcessDefinitionInstanceStatisticsEntity> result) {
    return new ProcessDefinitionInstanceStatisticsQueryResultContract(
        toPage(result),
        result.items().stream()
            .map(SearchQueryResponseMapper::toProcessInstanceStatisticsResult)
            .toList());
  }

  public static ProcessDefinitionInstanceVersionStatisticsQueryResultContract
      toProcessInstanceVersionStatisticsQueryResult(
          final SearchQueryResult<ProcessDefinitionInstanceVersionStatisticsEntity> result) {
    return new ProcessDefinitionInstanceVersionStatisticsQueryResultContract(
        toPage(result),
        result.items().stream()
            .map(SearchQueryResponseMapper::toProcessInstanceVersionStatisticsResult)
            .toList());
  }

  private static ProcessDefinitionInstanceStatisticsContract toProcessInstanceStatisticsResult(
      final ProcessDefinitionInstanceStatisticsEntity result) {
    return new ProcessDefinitionInstanceStatisticsContract(
        result.processDefinitionId(),
        result.tenantId(),
        result.latestProcessDefinitionName(),
        result.hasMultipleVersions(),
        result.activeInstancesWithoutIncidentCount(),
        result.activeInstancesWithIncidentCount());
  }

  private static ProcessDefinitionInstanceVersionStatisticsContract
      toProcessInstanceVersionStatisticsResult(
          final ProcessDefinitionInstanceVersionStatisticsEntity result) {
    return new ProcessDefinitionInstanceVersionStatisticsContract(
        result.processDefinitionId(),
        KeyUtil.keyToString(result.processDefinitionKey()),
        result.processDefinitionName(),
        result.tenantId(),
        result.processDefinitionVersion(),
        result.activeInstancesWithIncidentCount(),
        result.activeInstancesWithoutIncidentCount());
  }

  public static IncidentProcessInstanceStatisticsByErrorQueryResultContract
      toIncidentProcessInstanceStatisticsByErrorResult(
          final SearchQueryResult<IncidentProcessInstanceStatisticsByErrorEntity> result) {
    return new IncidentProcessInstanceStatisticsByErrorQueryResultContract(
        toPage(result),
        result.items().stream()
            .map(SearchQueryResponseMapper::toIncidentProcessInstanceStatisticsByErrorResult)
            .toList());
  }

  private static IncidentProcessInstanceStatisticsByErrorContract
      toIncidentProcessInstanceStatisticsByErrorResult(
          final IncidentProcessInstanceStatisticsByErrorEntity result) {
    return new IncidentProcessInstanceStatisticsByErrorContract(
        result.errorHashCode(), result.errorMessage(), result.activeInstancesWithErrorCount());
  }

  public static IncidentProcessInstanceStatisticsByDefinitionQueryResultContract
      toIncidentProcessInstanceStatisticsByDefinitionQueryResult(
          final SearchQueryResult<IncidentProcessInstanceStatisticsByDefinitionEntity> result) {
    return new IncidentProcessInstanceStatisticsByDefinitionQueryResultContract(
        toPage(result),
        result.items().stream()
            .map(SearchQueryResponseMapper::toIncidentProcessInstanceStatisticsByDefinitionResult)
            .toList());
  }

  private static IncidentProcessInstanceStatisticsByDefinitionContract
      toIncidentProcessInstanceStatisticsByDefinitionResult(
          final IncidentProcessInstanceStatisticsByDefinitionEntity result) {
    return new IncidentProcessInstanceStatisticsByDefinitionContract(
        result.processDefinitionId(),
        KeyUtil.keyToString(result.processDefinitionKey()),
        result.processDefinitionName(),
        result.processDefinitionVersion(),
        result.tenantId(),
        result.activeInstancesWithErrorCount());
  }

  public static ProcessInstanceSequenceFlowsQueryContract toSequenceFlowsResult(
      final List<SequenceFlowEntity> result) {
    return new ProcessInstanceSequenceFlowsQueryContract(
        result.stream()
            .map(SearchQueryResponseMapper::toProcessInstanceSequenceFlowResult)
            .toList());
  }

  private static ProcessInstanceSequenceFlowContract toProcessInstanceSequenceFlowResult(
      final SequenceFlowEntity result) {
    return new ProcessInstanceSequenceFlowContract(
        result.sequenceFlowId(),
        KeyUtil.keyToString(result.processInstanceKey()),
        KeyUtil.keyToString(result.rootProcessInstanceKey()),
        KeyUtil.keyToString(result.processDefinitionKey()),
        result.processDefinitionId(),
        result.flowNodeId(),
        result.tenantId());
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

  private static SearchQueryPageResponseContract toPage(final SearchQueryResult<?> result) {
    return new SearchQueryPageResponseContract(
        result.total(), result.hasMoreTotalItems(), result.startCursor(), result.endCursor());
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

  public static CamundaUserContract toCamundaUser(final CamundaUserDTO camundaUser) {
    return new CamundaUserContract(
        camundaUser.username(),
        camundaUser.displayName(),
        camundaUser.email(),
        camundaUser.authorizedComponents(),
        camundaUser.tenants().stream()
            .map(t -> new TenantContract(t.name(), t.tenantId(), t.description()))
            .toList(),
        camundaUser.groups(),
        camundaUser.roles(),
        camundaUser.salesPlanType(),
        toCamundaUserResultC8Links(camundaUser.c8Links()),
        camundaUser.canLogout());
  }

  private static Map<String, String> toCamundaUserResultC8Links(
      final Map<AppName, String> c8Links) {
    return c8Links.entrySet().stream()
        .collect(toMap(e -> e.getKey().getValue(), Map.Entry::getValue, (v1, v2) -> v1));
  }

  private static List<DecisionInstanceContract> toDecisionInstances(
      final List<DecisionInstanceEntity> instances) {
    return DecisionInstanceContractAdapter.toSearchProjections(instances);
  }

  public static <T> T toDecisionInstance(final DecisionInstanceEntity entity) {
    return adaptType(DecisionInstanceContractAdapter.toSearchProjection(entity));
  }

  public static DecisionInstanceGetQueryContract toDecisionInstanceGetQueryResponse(
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

  private static List<ProcessDefinitionMessageSubscriptionStatisticsContract>
      toProcessDefinitionMessageSubscriptionStatisticsItems(
          final List<ProcessDefinitionMessageSubscriptionStatisticsEntity> entities) {
    return entities.stream()
        .map(
            e ->
                new ProcessDefinitionMessageSubscriptionStatisticsContract(
                    e.processDefinitionId(),
                    e.tenantId(),
                    KeyUtil.keyToString(e.processDefinitionKey()),
                    e.processInstancesWithActiveSubscriptions(),
                    e.activeSubscriptions()))
        .toList();
  }

  public static ProcessDefinitionMessageSubscriptionStatisticsQueryResultContract
      toProcessDefinitionMessageSubscriptionStatisticsQueryResponse(
          final SearchQueryResult<ProcessDefinitionMessageSubscriptionStatisticsEntity> result) {
    return new ProcessDefinitionMessageSubscriptionStatisticsQueryResultContract(
        toPage(result),
        ofNullable(result.items())
            .map(SearchQueryResponseMapper::toProcessDefinitionMessageSubscriptionStatisticsItems)
            .orElseGet(Collections::emptyList));
  }

  public static GlobalJobStatisticsQueryContract toGlobalJobStatisticsQueryResult(
      final GlobalJobStatisticsEntity entity) {
    if (entity == null) {
      return new GlobalJobStatisticsQueryContract(
          new StatusMetricContract(0L, null),
          new StatusMetricContract(0L, null),
          new StatusMetricContract(0L, null),
          false);
    }

    return new GlobalJobStatisticsQueryContract(
        toStatusMetric(entity.created()),
        toStatusMetric(entity.completed()),
        toStatusMetric(entity.failed()),
        entity.isIncomplete());
  }

  public static JobTypeStatisticsQueryResultContract toJobTypeStatisticsQueryResult(
      final SearchQueryResult<JobTypeStatisticsEntity> result) {
    return new JobTypeStatisticsQueryResultContract(
        toPage(result),
        result.items().stream().map(SearchQueryResponseMapper::toJobTypeStatisticsItem).toList());
  }

  private static JobTypeStatisticsItemContract toJobTypeStatisticsItem(
      final JobTypeStatisticsEntity entity) {
    if (entity == null) {
      return new JobTypeStatisticsItemContract(
          "",
          new StatusMetricContract(0L, null),
          new StatusMetricContract(0L, null),
          new StatusMetricContract(0L, null),
          0);
    }

    return new JobTypeStatisticsItemContract(
        entity.jobType(),
        toStatusMetric(entity.created()),
        toStatusMetric(entity.completed()),
        toStatusMetric(entity.failed()),
        entity.workers());
  }

  public static JobWorkerStatisticsQueryResultContract toJobWorkerStatisticsQueryResult(
      final SearchQueryResult<JobWorkerStatisticsEntity> result) {
    return new JobWorkerStatisticsQueryResultContract(
        toPage(result),
        result.items().stream().map(SearchQueryResponseMapper::toJobWorkerStatisticsItem).toList());
  }

  private static JobWorkerStatisticsItemContract toJobWorkerStatisticsItem(
      final JobWorkerStatisticsEntity entity) {
    if (entity == null) {
      return new JobWorkerStatisticsItemContract(
          "",
          new StatusMetricContract(0L, null),
          new StatusMetricContract(0L, null),
          new StatusMetricContract(0L, null));
    }

    return new JobWorkerStatisticsItemContract(
        entity.worker(),
        toStatusMetric(entity.created()),
        toStatusMetric(entity.completed()),
        toStatusMetric(entity.failed()));
  }

  public static JobTimeSeriesStatisticsQueryResultContract toJobTimeSeriesStatisticsQueryResult(
      final SearchQueryResult<JobTimeSeriesStatisticsEntity> result) {
    return new JobTimeSeriesStatisticsQueryResultContract(
        toPage(result),
        result.items().stream()
            .map(SearchQueryResponseMapper::toJobTimeSeriesStatisticsItem)
            .toList());
  }

  private static JobTimeSeriesStatisticsItemContract toJobTimeSeriesStatisticsItem(
      final JobTimeSeriesStatisticsEntity entity) {
    if (entity == null) {
      return new JobTimeSeriesStatisticsItemContract(
          "",
          new StatusMetricContract(0L, null),
          new StatusMetricContract(0L, null),
          new StatusMetricContract(0L, null));
    }

    return new JobTimeSeriesStatisticsItemContract(
        formatDate(entity.time()),
        toStatusMetric(entity.created()),
        toStatusMetric(entity.completed()),
        toStatusMetric(entity.failed()));
  }

  public static JobErrorStatisticsQueryResultContract toJobErrorStatisticsQueryResult(
      final SearchQueryResult<JobErrorStatisticsEntity> result) {
    return new JobErrorStatisticsQueryResultContract(
        toPage(result),
        result.items().stream().map(SearchQueryResponseMapper::toJobErrorStatisticsItem).toList());
  }

  private static JobErrorStatisticsItemContract toJobErrorStatisticsItem(
      final JobErrorStatisticsEntity entity) {
    if (entity == null) {
      return new JobErrorStatisticsItemContract("", "", 0);
    }

    return new JobErrorStatisticsItemContract(
        ofNullable(entity.errorCode()).orElse(""), entity.errorMessage(), entity.workers());
  }

  private static StatusMetricContract toStatusMetric(
      final GlobalJobStatisticsEntity.StatusMetric metric) {
    if (metric == null) {
      return new StatusMetricContract(0L, null);
    }
    return new StatusMetricContract(metric.count(), formatDate(metric.lastUpdatedAt()));
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
