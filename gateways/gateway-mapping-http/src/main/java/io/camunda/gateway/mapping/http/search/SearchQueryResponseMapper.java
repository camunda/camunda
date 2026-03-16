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
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedAuditLogStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedAuthorizationStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedBatchOperationItemResponseStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedBatchOperationResponseStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedClusterVariableSearchStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedClusterVariableStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedCorrelatedMessageSubscriptionStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedDecisionDefinitionStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedDecisionInstanceStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedDecisionRequirementsStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedElementInstanceStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedFormStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedGlobalTaskListenerStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedGroupClientStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedGroupStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedGroupUserStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedIncidentStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedJobSearchStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedMappingRuleStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedMessageSubscriptionStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedProcessDefinitionStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedProcessInstanceCallHierarchyEntryStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedProcessInstanceStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedRoleClientStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedRoleGroupStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedRoleStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedRoleUserStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedTenantClientStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedTenantGroupStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedTenantStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedTenantUserStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedUserStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedUserTaskStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedVariableSearchStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedVariableStrictContract;
import io.camunda.gateway.mapping.http.util.KeyUtil;
import io.camunda.gateway.protocol.model.CamundaUserResult;
import io.camunda.gateway.protocol.model.DecisionInstanceGetQueryResult;
import io.camunda.gateway.protocol.model.GlobalJobStatisticsQueryResult;
import io.camunda.gateway.protocol.model.IncidentProcessInstanceStatisticsByDefinitionQueryResult;
import io.camunda.gateway.protocol.model.IncidentProcessInstanceStatisticsByDefinitionResult;
import io.camunda.gateway.protocol.model.IncidentProcessInstanceStatisticsByErrorQueryResult;
import io.camunda.gateway.protocol.model.IncidentProcessInstanceStatisticsByErrorResult;
import io.camunda.gateway.protocol.model.JobErrorStatisticsItem;
import io.camunda.gateway.protocol.model.JobErrorStatisticsQueryResult;
import io.camunda.gateway.protocol.model.JobTimeSeriesStatisticsItem;
import io.camunda.gateway.protocol.model.JobTimeSeriesStatisticsQueryResult;
import io.camunda.gateway.protocol.model.JobTypeStatisticsItem;
import io.camunda.gateway.protocol.model.JobTypeStatisticsQueryResult;
import io.camunda.gateway.protocol.model.JobWorkerStatisticsItem;
import io.camunda.gateway.protocol.model.JobWorkerStatisticsQueryResult;
import io.camunda.gateway.protocol.model.ProcessDefinitionElementStatisticsQueryResult;
import io.camunda.gateway.protocol.model.ProcessDefinitionInstanceStatisticsQueryResult;
import io.camunda.gateway.protocol.model.ProcessDefinitionInstanceStatisticsResult;
import io.camunda.gateway.protocol.model.ProcessDefinitionInstanceVersionStatisticsQueryResult;
import io.camunda.gateway.protocol.model.ProcessDefinitionInstanceVersionStatisticsResult;
import io.camunda.gateway.protocol.model.ProcessDefinitionMessageSubscriptionStatisticsQueryResult;
import io.camunda.gateway.protocol.model.ProcessDefinitionMessageSubscriptionStatisticsResult;
import io.camunda.gateway.protocol.model.ProcessElementStatisticsResult;
import io.camunda.gateway.protocol.model.ProcessInstanceElementStatisticsQueryResult;
import io.camunda.gateway.protocol.model.ProcessInstanceSequenceFlowResult;
import io.camunda.gateway.protocol.model.ProcessInstanceSequenceFlowsQueryResult;
import io.camunda.gateway.protocol.model.SearchQueryPageResponse;
import io.camunda.gateway.protocol.model.StatusMetric;
import io.camunda.gateway.protocol.model.TenantResult;
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

    final var response =
        new UsageMetricsResponse()
            .assignees(tuStatistics.totalTu())
            .processInstances(statistics.totalRpi())
            .decisionInstances(statistics.totalEdi())
            .activeTenants(statistics.at());

    if (withTenants) {
      final Map<String, UsageMetricStatisticsEntityTenant> tenants1 = statistics.tenants();
      final Map<String, UsageMetricTUStatisticsEntityTenant> tenants2 = tuStatistics.tenants();
      final var allTenantKeys = new HashSet<>(tenants1.keySet());
      allTenantKeys.addAll(tenants2.keySet());

      final Map<String, UsageMetricsResponseItem> mergedTenants =
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
        response.tenants(mergedTenants);
      }
    }

    return response;
  }

  public static StrictSearchQueryResult<GeneratedProcessDefinitionStrictContract>
      toProcessDefinitionSearchQueryResponse(
          final SearchQueryResult<ProcessDefinitionEntity> result) {
    return new StrictSearchQueryResult<>(
        ofNullable(result.items())
            .map(
                entities -> entities.stream().map(ProcessDefinitionContractAdapter::adapt).toList())
            .orElseGet(Collections::emptyList),
        toStrictSearchQueryPage(result));
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

  public static ProcessInstanceElementStatisticsQueryResult
      toProcessInstanceElementStatisticsResult(final List<ProcessFlowNodeStatisticsEntity> result) {
    return new ProcessInstanceElementStatisticsQueryResult()
        .items(
            result.stream()
                .map(SearchQueryResponseMapper::toProcessElementStatisticsResult)
                .toList());
  }

  private static ProcessElementStatisticsResult toProcessElementStatisticsResult(
      final ProcessFlowNodeStatisticsEntity result) {
    return new ProcessElementStatisticsResult()
        .elementId(result.flowNodeId())
        .active(result.active())
        .canceled(result.canceled())
        .incidents(result.incidents())
        .completed(result.completed());
  }

  public static ProcessDefinitionInstanceStatisticsQueryResult
      toProcessInstanceStatisticsQueryResult(
          final SearchQueryResult<ProcessDefinitionInstanceStatisticsEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return new ProcessDefinitionInstanceStatisticsQueryResult()
        .page(page)
        .items(
            result.items().stream()
                .map(SearchQueryResponseMapper::toProcessInstanceStatisticsResult)
                .toList());
  }

  public static ProcessDefinitionInstanceVersionStatisticsQueryResult
      toProcessInstanceVersionStatisticsQueryResult(
          final SearchQueryResult<ProcessDefinitionInstanceVersionStatisticsEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return new ProcessDefinitionInstanceVersionStatisticsQueryResult()
        .page(page)
        .items(
            result.items().stream()
                .map(SearchQueryResponseMapper::toProcessInstanceVersionStatisticsResult)
                .toList());
  }

  private static ProcessDefinitionInstanceStatisticsResult toProcessInstanceStatisticsResult(
      final ProcessDefinitionInstanceStatisticsEntity result) {
    return new ProcessDefinitionInstanceStatisticsResult()
        .processDefinitionId(result.processDefinitionId())
        .tenantId(result.tenantId())
        .latestProcessDefinitionName(result.latestProcessDefinitionName())
        .hasMultipleVersions(result.hasMultipleVersions())
        .activeInstancesWithIncidentCount(result.activeInstancesWithIncidentCount())
        .activeInstancesWithoutIncidentCount(result.activeInstancesWithoutIncidentCount());
  }

  private static ProcessDefinitionInstanceVersionStatisticsResult
      toProcessInstanceVersionStatisticsResult(
          final ProcessDefinitionInstanceVersionStatisticsEntity result) {
    return new ProcessDefinitionInstanceVersionStatisticsResult()
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
    final var page = toSearchQueryPageResponse(result);
    return new IncidentProcessInstanceStatisticsByErrorQueryResult()
        .page(page)
        .items(
            result.items().stream()
                .map(SearchQueryResponseMapper::toIncidentProcessInstanceStatisticsByErrorResult)
                .toList());
  }

  private static IncidentProcessInstanceStatisticsByErrorResult
      toIncidentProcessInstanceStatisticsByErrorResult(
          final IncidentProcessInstanceStatisticsByErrorEntity result) {
    return new IncidentProcessInstanceStatisticsByErrorResult()
        .errorHashCode(result.errorHashCode())
        .errorMessage(result.errorMessage())
        .activeInstancesWithErrorCount(result.activeInstancesWithErrorCount());
  }

  public static IncidentProcessInstanceStatisticsByDefinitionQueryResult
      toIncidentProcessInstanceStatisticsByDefinitionQueryResult(
          final SearchQueryResult<IncidentProcessInstanceStatisticsByDefinitionEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return new IncidentProcessInstanceStatisticsByDefinitionQueryResult()
        .page(page)
        .items(
            result.items().stream()
                .map(
                    SearchQueryResponseMapper
                        ::toIncidentProcessInstanceStatisticsByDefinitionResult)
                .toList());
  }

  private static IncidentProcessInstanceStatisticsByDefinitionResult
      toIncidentProcessInstanceStatisticsByDefinitionResult(
          final IncidentProcessInstanceStatisticsByDefinitionEntity result) {
    return new IncidentProcessInstanceStatisticsByDefinitionResult()
        .processDefinitionId(result.processDefinitionId())
        .processDefinitionKey(KeyUtil.keyToString(result.processDefinitionKey()))
        .processDefinitionName(result.processDefinitionName())
        .processDefinitionVersion(result.processDefinitionVersion())
        .tenantId(result.tenantId())
        .activeInstancesWithErrorCount(result.activeInstancesWithErrorCount());
  }

  public static ProcessInstanceSequenceFlowsQueryResult toSequenceFlowsResult(
      final List<SequenceFlowEntity> result) {
    return new ProcessInstanceSequenceFlowsQueryResult()
        .items(
            result.stream()
                .map(SearchQueryResponseMapper::toProcessInstanceSequenceFlowResult)
                .toList());
  }

  private static ProcessInstanceSequenceFlowResult toProcessInstanceSequenceFlowResult(
      final SequenceFlowEntity result) {
    return new ProcessInstanceSequenceFlowResult()
        .sequenceFlowId(result.sequenceFlowId())
        .processInstanceKey(KeyUtil.keyToString(result.processInstanceKey()))
        .rootProcessInstanceKey(KeyUtil.keyToString(result.rootProcessInstanceKey()))
        .processDefinitionKey(KeyUtil.keyToString(result.processDefinitionKey()))
        .processDefinitionId(result.processDefinitionId())
        .elementId(result.flowNodeId())
        .tenantId(result.tenantId());
  }

  public static StrictSearchQueryResult<GeneratedProcessInstanceStrictContract>
      toProcessInstanceSearchQueryResponse(final SearchQueryResult<ProcessInstanceEntity> result) {
    return new StrictSearchQueryResult<>(
        ofNullable(result.items())
            .map(ProcessInstanceContractAdapter::adapt)
            .orElseGet(Collections::emptyList),
        toStrictSearchQueryPage(result));
  }

  public static StrictSearchQueryResult<GeneratedJobSearchStrictContract> toJobSearchQueryResponse(
      final SearchQueryResult<JobEntity> result) {
    return new StrictSearchQueryResult<>(
        ofNullable(result.items()).map(JobContractAdapter::adapt).orElseGet(Collections::emptyList),
        toStrictSearchQueryPage(result));
  }

  public static StrictSearchQueryResult<GeneratedRoleStrictContract> toRoleSearchQueryResponse(
      final SearchQueryResult<RoleEntity> result) {
    return new StrictSearchQueryResult<>(
        ofNullable(result.items()).map(RoleContractAdapter::adapt).orElseGet(List::of),
        toStrictSearchQueryPage(result));
  }

  public static StrictSearchQueryResult<GeneratedRoleGroupStrictContract>
      toRoleGroupSearchQueryResponse(final SearchQueryResult<RoleMemberEntity> result) {
    return new StrictSearchQueryResult<>(
        ofNullable(result.items()).map(MemberContractAdapter::toRoleGroups).orElseGet(List::of),
        toStrictSearchQueryPage(result));
  }

  public static StrictSearchQueryResult<GeneratedRoleUserStrictContract>
      toRoleUserSearchQueryResponse(final SearchQueryResult<RoleMemberEntity> result) {
    return new StrictSearchQueryResult<>(
        ofNullable(result.items()).map(MemberContractAdapter::toRoleUsers).orElseGet(List::of),
        toStrictSearchQueryPage(result));
  }

  public static StrictSearchQueryResult<GeneratedRoleClientStrictContract>
      toRoleClientSearchQueryResponse(final SearchQueryResult<RoleMemberEntity> result) {
    return new StrictSearchQueryResult<>(
        ofNullable(result.items()).map(MemberContractAdapter::toRoleClients).orElseGet(List::of),
        toStrictSearchQueryPage(result));
  }

  public static StrictSearchQueryResult<GeneratedGroupStrictContract> toGroupSearchQueryResponse(
      final SearchQueryResult<GroupEntity> result) {
    return new StrictSearchQueryResult<>(
        ofNullable(result.items()).map(GroupContractAdapter::adapt).orElseGet(List::of),
        toStrictSearchQueryPage(result));
  }

  public static StrictSearchQueryResult<GeneratedGroupUserStrictContract>
      toGroupUserSearchQueryResponse(final SearchQueryResult<GroupMemberEntity> result) {
    return new StrictSearchQueryResult<>(
        ofNullable(result.items()).map(MemberContractAdapter::toGroupUsers).orElseGet(List::of),
        toStrictSearchQueryPage(result));
  }

  public static StrictSearchQueryResult<GeneratedGroupClientStrictContract>
      toGroupClientSearchQueryResponse(final SearchQueryResult<GroupMemberEntity> result) {
    return new StrictSearchQueryResult<>(
        ofNullable(result.items()).map(MemberContractAdapter::toGroupClients).orElseGet(List::of),
        toStrictSearchQueryPage(result));
  }

  public static StrictSearchQueryResult<GeneratedTenantStrictContract> toTenantSearchQueryResponse(
      final SearchQueryResult<TenantEntity> result) {
    return new StrictSearchQueryResult<>(
        ofNullable(result.items()).map(TenantContractAdapter::adapt).orElseGet(List::of),
        toStrictSearchQueryPage(result));
  }

  public static StrictSearchQueryResult<GeneratedTenantGroupStrictContract>
      toTenantGroupSearchQueryResponse(final SearchQueryResult<TenantMemberEntity> result) {
    return new StrictSearchQueryResult<>(
        ofNullable(result.items()).map(MemberContractAdapter::toTenantGroups).orElseGet(List::of),
        toStrictSearchQueryPage(result));
  }

  public static StrictSearchQueryResult<GeneratedTenantUserStrictContract>
      toTenantUserSearchQueryResponse(final SearchQueryResult<TenantMemberEntity> result) {
    return new StrictSearchQueryResult<>(
        ofNullable(result.items()).map(MemberContractAdapter::toTenantUsers).orElseGet(List::of),
        toStrictSearchQueryPage(result));
  }

  public static StrictSearchQueryResult<GeneratedTenantClientStrictContract>
      toTenantClientSearchQueryResponse(final SearchQueryResult<TenantMemberEntity> result) {
    return new StrictSearchQueryResult<>(
        ofNullable(result.items()).map(MemberContractAdapter::toTenantClients).orElseGet(List::of),
        toStrictSearchQueryPage(result));
  }

  public static StrictSearchQueryResult<GeneratedMappingRuleStrictContract>
      toMappingRuleSearchQueryResponse(final SearchQueryResult<MappingRuleEntity> result) {
    return new StrictSearchQueryResult<>(
        ofNullable(result.items()).map(MappingRuleContractAdapter::adapt).orElseGet(List::of),
        toStrictSearchQueryPage(result));
  }

  public static StrictSearchQueryResult<GeneratedDecisionDefinitionStrictContract>
      toDecisionDefinitionSearchQueryResponse(
          final SearchQueryResult<DecisionDefinitionEntity> result) {
    return new StrictSearchQueryResult<>(
        ofNullable(result.items())
            .map(DecisionDefinitionContractAdapter::adapt)
            .orElseGet(Collections::emptyList),
        toStrictSearchQueryPage(result));
  }

  public static StrictSearchQueryResult<GeneratedDecisionRequirementsStrictContract>
      toDecisionRequirementsSearchQueryResponse(
          final SearchQueryResult<DecisionRequirementsEntity> result) {
    return new StrictSearchQueryResult<>(
        ofNullable(result.items())
            .map(DecisionRequirementsContractAdapter::adapt)
            .orElseGet(Collections::emptyList),
        toStrictSearchQueryPage(result));
  }

  public static StrictSearchQueryResult<GeneratedElementInstanceStrictContract>
      toElementInstanceSearchQueryResponse(final SearchQueryResult<FlowNodeInstanceEntity> result) {
    return new StrictSearchQueryResult<>(
        ofNullable(result.items())
            .map(ElementInstanceContractAdapter::adapt)
            .orElseGet(Collections::emptyList),
        toStrictSearchQueryPage(result));
  }

  public static StrictSearchQueryResult<GeneratedDecisionInstanceStrictContract>
      toDecisionInstanceSearchQueryResponse(
          final SearchQueryResult<DecisionInstanceEntity> result) {
    final var page = toStrictSearchQueryPage(result);
    final var items =
        ofNullable(result.items()).map(SearchQueryResponseMapper::toDecisionInstances).orElse(null);
    return new StrictSearchQueryResult<>(items, page);
  }

  public static StrictSearchQueryResult<GeneratedUserTaskStrictContract>
      toUserTaskSearchQueryResponse(final SearchQueryResult<UserTaskEntity> result) {
    return new StrictSearchQueryResult<>(
        ofNullable(result.items())
            .map(UserTaskContractAdapter::adapt)
            .orElseGet(Collections::emptyList),
        toStrictSearchQueryPage(result));
  }

  public static StrictSearchQueryResult<GeneratedUserStrictContract> toUserSearchQueryResponse(
      final SearchQueryResult<UserEntity> result) {
    return new StrictSearchQueryResult<>(
        ofNullable(result.items())
            .map(UserContractAdapter::adapt)
            .orElseGet(Collections::emptyList),
        toStrictSearchQueryPage(result));
  }

  public static StrictSearchQueryResult<GeneratedBatchOperationResponseStrictContract>
      toBatchOperationSearchQueryResult(final SearchQueryResult<BatchOperationEntity> result) {
    final var page = toStrictSearchQueryPage(result);
    return new StrictSearchQueryResult<>(
        ofNullable(result.items())
            .map(SearchQueryResponseMapper::toBatchOperations)
            .orElseGet(Collections::emptyList),
        page);
  }

  public static StrictSearchQueryResult<GeneratedBatchOperationItemResponseStrictContract>
      toBatchOperationItemSearchQueryResult(
          final SearchQueryResult<BatchOperationItemEntity> result) {
    final var page = toStrictSearchQueryPage(result);
    return new StrictSearchQueryResult<>(
        ofNullable(result.items())
            .map(SearchQueryResponseMapper::toBatchOperationItems)
            .orElseGet(Collections::emptyList),
        page);
  }

  public static StrictSearchQueryResult<GeneratedIncidentStrictContract>
      toIncidentSearchQueryResponse(final SearchQueryResult<IncidentEntity> result) {
    return new StrictSearchQueryResult<>(
        ofNullable(result.items())
            .map(IncidentContractAdapter::adapt)
            .orElseGet(Collections::emptyList),
        toStrictSearchQueryPage(result));
  }

  public static StrictSearchQueryResult<GeneratedMessageSubscriptionStrictContract>
      toMessageSubscriptionSearchQueryResponse(
          final SearchQueryResult<MessageSubscriptionEntity> result) {
    return new StrictSearchQueryResult<>(
        ofNullable(result.items())
            .map(MessageSubscriptionContractAdapter::adapt)
            .orElseGet(Collections::emptyList),
        toStrictSearchQueryPage(result));
  }

  public static StrictSearchQueryResult<GeneratedCorrelatedMessageSubscriptionStrictContract>
      toCorrelatedMessageSubscriptionSearchQueryResponse(
          final SearchQueryResult<CorrelatedMessageSubscriptionEntity> result) {
    return new StrictSearchQueryResult<>(
        ofNullable(result.items())
            .map(CorrelatedMessageSubscriptionContractAdapter::adapt)
            .orElseGet(Collections::emptyList),
        toStrictSearchQueryPage(result));
  }

  private static SearchQueryPageResponse toSearchQueryPageResponse(
      final SearchQueryResult<?> result) {

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

  public static GeneratedProcessDefinitionStrictContract toProcessDefinition(
      final ProcessDefinitionEntity entity) {
    return ProcessDefinitionContractAdapter.adapt(entity);
  }

  public static GeneratedProcessInstanceStrictContract toProcessInstance(
      final ProcessInstanceEntity entity) {
    return ProcessInstanceContractAdapter.adapt(entity);
  }

  public static List<GeneratedBatchOperationResponseStrictContract> toBatchOperations(
      final List<BatchOperationEntity> batchOperations) {
    return batchOperations.stream().map(SearchQueryResponseMapper::toBatchOperation).toList();
  }

  public static GeneratedBatchOperationResponseStrictContract toBatchOperation(
      final BatchOperationEntity entity) {
    return BatchOperationResponseContractAdapter.adapt(entity);
  }

  public static StrictSearchQueryResult<GeneratedBatchOperationItemResponseStrictContract>
      toBatchOperationItemSearchQueryResult(final List<BatchOperationItemEntity> batchOperations) {
    return new StrictSearchQueryResult<>(
        batchOperations.stream().map(SearchQueryResponseMapper::toBatchOperationItem).toList(),
        null);
  }

  public static List<GeneratedBatchOperationItemResponseStrictContract> toBatchOperationItems(
      final List<BatchOperationItemEntity> batchOperationItems) {
    return batchOperationItems.stream()
        .map(SearchQueryResponseMapper::toBatchOperationItem)
        .toList();
  }

  public static GeneratedBatchOperationItemResponseStrictContract toBatchOperationItem(
      final BatchOperationItemEntity entity) {
    return BatchOperationItemResponseContractAdapter.adapt(entity);
  }

  public static GeneratedRoleStrictContract toRole(final RoleEntity entity) {
    return RoleContractAdapter.adapt(entity);
  }

  public static GeneratedGroupStrictContract toGroup(final GroupEntity entity) {
    return GroupContractAdapter.adapt(entity);
  }

  public static GeneratedTenantStrictContract toTenant(final TenantEntity entity) {
    return TenantContractAdapter.adapt(entity);
  }

  public static GeneratedMappingRuleStrictContract toMappingRule(final MappingRuleEntity entity) {
    return MappingRuleContractAdapter.adapt(entity);
  }

  public static GeneratedElementInstanceStrictContract toElementInstance(
      final FlowNodeInstanceEntity entity) {
    return ElementInstanceContractAdapter.adapt(entity);
  }

  public static GeneratedDecisionDefinitionStrictContract toDecisionDefinition(
      final DecisionDefinitionEntity entity) {
    return DecisionDefinitionContractAdapter.adapt(entity);
  }

  public static GeneratedDecisionRequirementsStrictContract toDecisionRequirements(
      final DecisionRequirementsEntity entity) {
    return DecisionRequirementsContractAdapter.adapt(entity);
  }

  public static GeneratedIncidentStrictContract toIncident(final IncidentEntity entity) {
    return IncidentContractAdapter.adapt(entity);
  }

  public static GeneratedUserTaskStrictContract toUserTask(final UserTaskEntity entity) {
    return UserTaskContractAdapter.adapt(entity);
  }

  public static GeneratedFormStrictContract toFormItem(final FormEntity entity) {
    return FormContractAdapter.adapt(entity);
  }

  public static GeneratedUserStrictContract toUser(final UserEntity entity) {
    return UserContractAdapter.adapt(entity);
  }

  public static CamundaUserResult toCamundaUser(final CamundaUserDTO camundaUser) {
    return new CamundaUserResult()
        .displayName(camundaUser.displayName())
        .username(camundaUser.username())
        .email(camundaUser.email())
        .authorizedComponents(camundaUser.authorizedComponents())
        .tenants(
            camundaUser.tenants().stream()
                .map(
                    t ->
                        new TenantResult()
                            .name(t.name())
                            .description(t.description())
                            .tenantId(t.tenantId()))
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

  private static List<GeneratedDecisionInstanceStrictContract> toDecisionInstances(
      final List<DecisionInstanceEntity> instances) {
    return DecisionInstanceContractAdapter.toSearchProjections(instances);
  }

  public static GeneratedDecisionInstanceStrictContract toDecisionInstance(
      final DecisionInstanceEntity entity) {
    return DecisionInstanceContractAdapter.toSearchProjection(entity);
  }

  public static DecisionInstanceGetQueryResult toDecisionInstanceGetQueryResponse(
      final DecisionInstanceEntity entity) {
    return DecisionInstanceContractAdapter.toGetProjection(entity);
  }

  public static StrictSearchQueryResult<GeneratedVariableSearchStrictContract>
      toVariableSearchQueryResponse(
          final SearchQueryResult<VariableEntity> result, final boolean truncateValues) {
    final var page = toStrictSearchQueryPage(result);
    return new StrictSearchQueryResult<>(
        ofNullable(result.items())
            .map(entities -> VariableContractAdapter.toSearchProjections(entities, truncateValues))
            .orElseGet(Collections::emptyList),
        page);
  }

  public static GeneratedVariableStrictContract toVariableItem(
      final VariableEntity variableEntity) {
    return VariableContractAdapter.toItemProjection(variableEntity);
  }

  public static StrictSearchQueryResult<GeneratedClusterVariableSearchStrictContract>
      toClusterVariableSearchQueryResponse(
          final SearchQueryResult<ClusterVariableEntity> result, final boolean truncateValues) {
    final var page = toStrictSearchQueryPage(result);
    return new StrictSearchQueryResult<>(
        ofNullable(result.items())
            .map(
                entities ->
                    ClusterVariableContractAdapter.toSearchProjections(entities, truncateValues))
            .orElseGet(Collections::emptyList),
        page);
  }

  public static GeneratedClusterVariableSearchStrictContract toClusterVariableSearchResult(
      final ClusterVariableEntity clusterVariableEntity, final boolean truncateValues) {
    return ClusterVariableContractAdapter.toSearchProjection(clusterVariableEntity, truncateValues);
  }

  public static GeneratedClusterVariableStrictContract toClusterVariableResult(
      final ClusterVariableEntity clusterVariableEntity) {
    return ClusterVariableContractAdapter.toItemProjection(clusterVariableEntity);
  }

  public static StrictSearchQueryResult<GeneratedAuthorizationStrictContract>
      toAuthorizationSearchQueryResponse(final SearchQueryResult<AuthorizationEntity> result) {
    return new StrictSearchQueryResult<>(
        ofNullable(result.items())
            .map(AuthorizationContractAdapter::adapt)
            .orElseGet(Collections::emptyList),
        toStrictSearchQueryPage(result));
  }

  public static GeneratedAuthorizationStrictContract toAuthorization(
      final AuthorizationEntity entity) {
    return AuthorizationContractAdapter.adapt(entity);
  }

  public static StrictSearchQueryResult<GeneratedAuditLogStrictContract>
      toAuditLogSearchQueryResponse(final SearchQueryResult<AuditLogEntity> result) {
    return new StrictSearchQueryResult<>(
        ofNullable(result.items())
            .map(AuditLogContractAdapter::adapt)
            .orElseGet(Collections::emptyList),
        toStrictSearchQueryPage(result));
  }

  public static GeneratedAuditLogStrictContract toAuditLog(final AuditLogEntity entity) {
    return AuditLogContractAdapter.adapt(entity);
  }

  public static List<GeneratedProcessInstanceCallHierarchyEntryStrictContract>
      toProcessInstanceCallHierarchyEntries(
          final List<ProcessInstanceEntity> processInstanceEntities) {
    return processInstanceEntities.stream()
        .map(SearchQueryResponseMapper::toProcessInstanceCallHierarchyEntry)
        .toList();
  }

  public static GeneratedProcessInstanceCallHierarchyEntryStrictContract
      toProcessInstanceCallHierarchyEntry(final ProcessInstanceEntity processInstanceEntity) {
    return ProcessInstanceCallHierarchyEntryContractAdapter.adapt(processInstanceEntity);
  }

  private static List<ProcessDefinitionMessageSubscriptionStatisticsResult>
      toProcessDefinitionMessageSubscriptionStatisticsQueryResponse(
          final List<ProcessDefinitionMessageSubscriptionStatisticsEntity> entities) {
    return entities.stream()
        .map(
            e ->
                new ProcessDefinitionMessageSubscriptionStatisticsResult()
                    .processDefinitionId(e.processDefinitionId())
                    .tenantId(e.tenantId())
                    .processDefinitionKey(KeyUtil.keyToString(e.processDefinitionKey()))
                    .activeSubscriptions(e.activeSubscriptions())
                    .processInstancesWithActiveSubscriptions(
                        e.processInstancesWithActiveSubscriptions()))
        .toList();
  }

  public static ProcessDefinitionMessageSubscriptionStatisticsQueryResult
      toProcessDefinitionMessageSubscriptionStatisticsQueryResponse(
          final SearchQueryResult<ProcessDefinitionMessageSubscriptionStatisticsEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return new ProcessDefinitionMessageSubscriptionStatisticsQueryResult()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(
                    SearchQueryResponseMapper
                        ::toProcessDefinitionMessageSubscriptionStatisticsQueryResponse)
                .orElseGet(Collections::emptyList));
  }

  public static GlobalJobStatisticsQueryResult toGlobalJobStatisticsQueryResult(
      final GlobalJobStatisticsEntity entity) {
    if (entity == null) {
      return new GlobalJobStatisticsQueryResult()
          .created(new StatusMetric().count(0L))
          .completed(new StatusMetric().count(0L))
          .failed(new StatusMetric().count(0L))
          .isIncomplete(false);
    }

    return new GlobalJobStatisticsQueryResult()
        .created(toStatusMetric(entity.created()))
        .completed(toStatusMetric(entity.completed()))
        .failed(toStatusMetric(entity.failed()))
        .isIncomplete(entity.isIncomplete());
  }

  public static JobTypeStatisticsQueryResult toJobTypeStatisticsQueryResult(
      final SearchQueryResult<JobTypeStatisticsEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return new JobTypeStatisticsQueryResult()
        .page(page)
        .items(
            result.items().stream()
                .map(SearchQueryResponseMapper::toJobTypeStatisticsItem)
                .toList());
  }

  private static JobTypeStatisticsItem toJobTypeStatisticsItem(
      final JobTypeStatisticsEntity entity) {
    if (entity == null) {
      return new JobTypeStatisticsItem();
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
    final var page = toSearchQueryPageResponse(result);
    return new JobWorkerStatisticsQueryResult()
        .page(page)
        .items(
            result.items().stream()
                .map(SearchQueryResponseMapper::toJobWorkerStatisticsItem)
                .toList());
  }

  private static JobWorkerStatisticsItem toJobWorkerStatisticsItem(
      final JobWorkerStatisticsEntity entity) {
    if (entity == null) {
      return new JobWorkerStatisticsItem();
    }

    return new JobWorkerStatisticsItem()
        .worker(entity.worker())
        .created(toStatusMetric(entity.created()))
        .completed(toStatusMetric(entity.completed()))
        .failed(toStatusMetric(entity.failed()));
  }

  public static JobTimeSeriesStatisticsQueryResult toJobTimeSeriesStatisticsQueryResult(
      final SearchQueryResult<JobTimeSeriesStatisticsEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return new JobTimeSeriesStatisticsQueryResult()
        .page(page)
        .items(
            result.items().stream()
                .map(SearchQueryResponseMapper::toJobTimeSeriesStatisticsItem)
                .toList());
  }

  private static JobTimeSeriesStatisticsItem toJobTimeSeriesStatisticsItem(
      final JobTimeSeriesStatisticsEntity entity) {
    if (entity == null) {
      return new JobTimeSeriesStatisticsItem();
    }

    return new JobTimeSeriesStatisticsItem()
        .time(formatDate(entity.time()))
        .created(toStatusMetric(entity.created()))
        .completed(toStatusMetric(entity.completed()))
        .failed(toStatusMetric(entity.failed()));
  }

  public static JobErrorStatisticsQueryResult toJobErrorStatisticsQueryResult(
      final SearchQueryResult<JobErrorStatisticsEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return new JobErrorStatisticsQueryResult()
        .page(page)
        .items(
            result.items().stream()
                .map(SearchQueryResponseMapper::toJobErrorStatisticsItem)
                .toList());
  }

  private static JobErrorStatisticsItem toJobErrorStatisticsItem(
      final JobErrorStatisticsEntity entity) {
    if (entity == null) {
      return new JobErrorStatisticsItem();
    }

    return new JobErrorStatisticsItem()
        .errorCode(ofNullable(entity.errorCode()).orElse(""))
        .errorMessage(entity.errorMessage())
        .workers(entity.workers());
  }

  private static StatusMetric toStatusMetric(final GlobalJobStatisticsEntity.StatusMetric metric) {
    if (metric == null) {
      return new StatusMetric().count(0L);
    }
    return new StatusMetric()
        .count(metric.count())
        .lastUpdatedAt(formatDate(metric.lastUpdatedAt()));
  }

  public static StrictSearchQueryResult<GeneratedGlobalTaskListenerStrictContract>
      toGlobalTaskListenerSearchQueryResponse(
          final SearchQueryResult<GlobalListenerEntity> result) {
    return new StrictSearchQueryResult<>(
        ofNullable(result.items())
            .map(GlobalTaskListenerContractAdapter::adapt)
            .orElseGet(Collections::emptyList),
        toStrictSearchQueryPage(result));
  }

  public static GeneratedGlobalTaskListenerStrictContract toGlobalTaskListenerResult(
      final GlobalListenerEntity entity) {
    return GlobalTaskListenerContractAdapter.adapt(entity);
  }
}
