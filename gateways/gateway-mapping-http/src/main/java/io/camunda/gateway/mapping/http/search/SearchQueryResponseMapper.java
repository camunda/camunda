/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search;

import static io.camunda.gateway.mapping.http.ResponseMapper.formatDate;
import static io.camunda.gateway.mapping.http.ResponseMapper.formatDateOrNull;
import static io.camunda.gateway.mapping.http.util.KeyUtil.keyToString;
import static io.camunda.gateway.mapping.http.util.KeyUtil.keyToStringOrNull;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;

import io.camunda.authentication.entity.CamundaUserDTO;
import io.camunda.gateway.mapping.http.util.KeyUtil;
import io.camunda.gateway.protocol.model.AuditLogActorTypeEnum;
import io.camunda.gateway.protocol.model.AuditLogCategoryEnum;
import io.camunda.gateway.protocol.model.AuditLogEntityTypeEnum;
import io.camunda.gateway.protocol.model.AuditLogOperationTypeEnum;
import io.camunda.gateway.protocol.model.AuditLogResult;
import io.camunda.gateway.protocol.model.AuditLogResultEnum;
import io.camunda.gateway.protocol.model.AuditLogSearchQueryResult;
import io.camunda.gateway.protocol.model.AuthorizationResult;
import io.camunda.gateway.protocol.model.AuthorizationSearchResult;
import io.camunda.gateway.protocol.model.BatchOperationError;
import io.camunda.gateway.protocol.model.BatchOperationError.TypeEnum;
import io.camunda.gateway.protocol.model.BatchOperationItemResponse;
import io.camunda.gateway.protocol.model.BatchOperationItemSearchQueryResult;
import io.camunda.gateway.protocol.model.BatchOperationResponse;
import io.camunda.gateway.protocol.model.BatchOperationSearchQueryResult;
import io.camunda.gateway.protocol.model.BatchOperationStateEnum;
import io.camunda.gateway.protocol.model.BatchOperationTypeEnum;
import io.camunda.gateway.protocol.model.CamundaUserResult;
import io.camunda.gateway.protocol.model.ClusterVariableResult;
import io.camunda.gateway.protocol.model.ClusterVariableScopeEnum;
import io.camunda.gateway.protocol.model.ClusterVariableSearchQueryResult;
import io.camunda.gateway.protocol.model.ClusterVariableSearchResult;
import io.camunda.gateway.protocol.model.CorrelatedMessageSubscriptionResult;
import io.camunda.gateway.protocol.model.CorrelatedMessageSubscriptionSearchQueryResult;
import io.camunda.gateway.protocol.model.DecisionDefinitionResult;
import io.camunda.gateway.protocol.model.DecisionDefinitionSearchQueryResult;
import io.camunda.gateway.protocol.model.DecisionDefinitionTypeEnum;
import io.camunda.gateway.protocol.model.DecisionInstanceGetQueryResult;
import io.camunda.gateway.protocol.model.DecisionInstanceResult;
import io.camunda.gateway.protocol.model.DecisionInstanceSearchQueryResult;
import io.camunda.gateway.protocol.model.DecisionInstanceStateEnum;
import io.camunda.gateway.protocol.model.DecisionRequirementsResult;
import io.camunda.gateway.protocol.model.DecisionRequirementsSearchQueryResult;
import io.camunda.gateway.protocol.model.ElementInstanceResult;
import io.camunda.gateway.protocol.model.ElementInstanceSearchQueryResult;
import io.camunda.gateway.protocol.model.ElementInstanceStateEnum;
import io.camunda.gateway.protocol.model.EvaluatedDecisionInputItem;
import io.camunda.gateway.protocol.model.EvaluatedDecisionOutputItem;
import io.camunda.gateway.protocol.model.FormResult;
import io.camunda.gateway.protocol.model.GlobalJobStatisticsQueryResult;
import io.camunda.gateway.protocol.model.GlobalListenerSourceEnum;
import io.camunda.gateway.protocol.model.GlobalTaskListenerEventTypeEnum;
import io.camunda.gateway.protocol.model.GlobalTaskListenerResult;
import io.camunda.gateway.protocol.model.GlobalTaskListenerSearchQueryResult;
import io.camunda.gateway.protocol.model.GroupClientResult;
import io.camunda.gateway.protocol.model.GroupClientSearchResult;
import io.camunda.gateway.protocol.model.GroupResult;
import io.camunda.gateway.protocol.model.GroupSearchQueryResult;
import io.camunda.gateway.protocol.model.GroupUserResult;
import io.camunda.gateway.protocol.model.GroupUserSearchResult;
import io.camunda.gateway.protocol.model.IncidentErrorTypeEnum;
import io.camunda.gateway.protocol.model.IncidentProcessInstanceStatisticsByDefinitionQueryResult;
import io.camunda.gateway.protocol.model.IncidentProcessInstanceStatisticsByDefinitionResult;
import io.camunda.gateway.protocol.model.IncidentProcessInstanceStatisticsByErrorQueryResult;
import io.camunda.gateway.protocol.model.IncidentProcessInstanceStatisticsByErrorResult;
import io.camunda.gateway.protocol.model.IncidentResult;
import io.camunda.gateway.protocol.model.IncidentSearchQueryResult;
import io.camunda.gateway.protocol.model.IncidentStateEnum;
import io.camunda.gateway.protocol.model.JobErrorStatisticsItem;
import io.camunda.gateway.protocol.model.JobErrorStatisticsQueryResult;
import io.camunda.gateway.protocol.model.JobKindEnum;
import io.camunda.gateway.protocol.model.JobListenerEventTypeEnum;
import io.camunda.gateway.protocol.model.JobSearchQueryResult;
import io.camunda.gateway.protocol.model.JobSearchResult;
import io.camunda.gateway.protocol.model.JobStateEnum;
import io.camunda.gateway.protocol.model.JobTimeSeriesStatisticsItem;
import io.camunda.gateway.protocol.model.JobTimeSeriesStatisticsQueryResult;
import io.camunda.gateway.protocol.model.JobTypeStatisticsItem;
import io.camunda.gateway.protocol.model.JobTypeStatisticsQueryResult;
import io.camunda.gateway.protocol.model.JobWorkerStatisticsItem;
import io.camunda.gateway.protocol.model.JobWorkerStatisticsQueryResult;
import io.camunda.gateway.protocol.model.MappingRuleResult;
import io.camunda.gateway.protocol.model.MappingRuleSearchQueryResult;
import io.camunda.gateway.protocol.model.MatchedDecisionRuleItem;
import io.camunda.gateway.protocol.model.MessageSubscriptionResult;
import io.camunda.gateway.protocol.model.MessageSubscriptionSearchQueryResult;
import io.camunda.gateway.protocol.model.MessageSubscriptionStateEnum;
import io.camunda.gateway.protocol.model.MessageSubscriptionTypeEnum;
import io.camunda.gateway.protocol.model.OwnerTypeEnum;
import io.camunda.gateway.protocol.model.PermissionTypeEnum;
import io.camunda.gateway.protocol.model.ProcessDefinitionElementStatisticsQueryResult;
import io.camunda.gateway.protocol.model.ProcessDefinitionInstanceStatisticsQueryResult;
import io.camunda.gateway.protocol.model.ProcessDefinitionInstanceStatisticsResult;
import io.camunda.gateway.protocol.model.ProcessDefinitionInstanceVersionStatisticsQueryResult;
import io.camunda.gateway.protocol.model.ProcessDefinitionInstanceVersionStatisticsResult;
import io.camunda.gateway.protocol.model.ProcessDefinitionMessageSubscriptionStatisticsQueryResult;
import io.camunda.gateway.protocol.model.ProcessDefinitionMessageSubscriptionStatisticsResult;
import io.camunda.gateway.protocol.model.ProcessDefinitionResult;
import io.camunda.gateway.protocol.model.ProcessDefinitionSearchQueryResult;
import io.camunda.gateway.protocol.model.ProcessElementStatisticsResult;
import io.camunda.gateway.protocol.model.ProcessInstanceCallHierarchyEntry;
import io.camunda.gateway.protocol.model.ProcessInstanceElementStatisticsQueryResult;
import io.camunda.gateway.protocol.model.ProcessInstanceResult;
import io.camunda.gateway.protocol.model.ProcessInstanceSearchQueryResult;
import io.camunda.gateway.protocol.model.ProcessInstanceSequenceFlowResult;
import io.camunda.gateway.protocol.model.ProcessInstanceSequenceFlowsQueryResult;
import io.camunda.gateway.protocol.model.ProcessInstanceStateEnum;
import io.camunda.gateway.protocol.model.ResourceResult;
import io.camunda.gateway.protocol.model.ResourceSearchQueryResult;
import io.camunda.gateway.protocol.model.ResourceTypeEnum;
import io.camunda.gateway.protocol.model.RoleClientResult;
import io.camunda.gateway.protocol.model.RoleClientSearchResult;
import io.camunda.gateway.protocol.model.RoleGroupResult;
import io.camunda.gateway.protocol.model.RoleGroupSearchResult;
import io.camunda.gateway.protocol.model.RoleResult;
import io.camunda.gateway.protocol.model.RoleSearchQueryResult;
import io.camunda.gateway.protocol.model.RoleUserResult;
import io.camunda.gateway.protocol.model.RoleUserSearchResult;
import io.camunda.gateway.protocol.model.SearchQueryPageResponse;
import io.camunda.gateway.protocol.model.StatusMetric;
import io.camunda.gateway.protocol.model.TenantClientResult;
import io.camunda.gateway.protocol.model.TenantClientSearchResult;
import io.camunda.gateway.protocol.model.TenantGroupResult;
import io.camunda.gateway.protocol.model.TenantGroupSearchResult;
import io.camunda.gateway.protocol.model.TenantResult;
import io.camunda.gateway.protocol.model.TenantSearchQueryResult;
import io.camunda.gateway.protocol.model.TenantUserResult;
import io.camunda.gateway.protocol.model.TenantUserSearchResult;
import io.camunda.gateway.protocol.model.UsageMetricsResponse;
import io.camunda.gateway.protocol.model.UsageMetricsResponseItem;
import io.camunda.gateway.protocol.model.UserResult;
import io.camunda.gateway.protocol.model.UserSearchResult;
import io.camunda.gateway.protocol.model.UserTaskResult;
import io.camunda.gateway.protocol.model.UserTaskSearchQueryResult;
import io.camunda.gateway.protocol.model.UserTaskStateEnum;
import io.camunda.gateway.protocol.model.VariableResult;
import io.camunda.gateway.protocol.model.VariableSearchQueryResult;
import io.camunda.gateway.protocol.model.VariableSearchResult;
import io.camunda.search.entities.AuditLogEntity;
import io.camunda.search.entities.AuthorizationEntity;
import io.camunda.search.entities.BatchOperationEntity;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationErrorEntity;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemEntity;
import io.camunda.search.entities.ClusterVariableEntity;
import io.camunda.search.entities.CorrelatedMessageSubscriptionEntity;
import io.camunda.search.entities.DecisionDefinitionEntity;
import io.camunda.search.entities.DecisionInstanceEntity;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionDefinitionType;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionInstanceInputEntity;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionInstanceOutputEntity;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionInstanceState;
import io.camunda.search.entities.DecisionRequirementsEntity;
import io.camunda.search.entities.DeployedResourceEntity;
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
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.util.collection.Tuple;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

public final class SearchQueryResponseMapper {

  // Emitted as a sentinel for required date-time response fields when the data layer has no value.
  // Only surfaces in rare, transient edge cases (exporter cache miss, or conditional-write intents
  // where the usual start event was compacted or not yet observed) and is typically backfilled on
  // the next exporter event. Preferred over a spec-incompatible `""` so ISO-8601 parsing on the
  // client still succeeds.
  private static final String EPOCH_DATE_SENTINEL = "1970-01-01T00:00:00Z";

  private SearchQueryResponseMapper() {}

  public static UsageMetricsResponse toUsageMetricsResponse(
      final SearchQueryResult<Tuple<UsageMetricStatisticsEntity, UsageMetricTUStatisticsEntity>>
          result,
      final boolean withTenants) {
    final var tuple = result.items().getFirst();
    final var statistics = tuple.getLeft();
    final var tuStatistics = tuple.getRight();

    final Map<String, UsageMetricsResponseItem> mergedTenants;
    if (withTenants) {
      final Map<String, UsageMetricStatisticsEntityTenant> tenants1 = statistics.tenants();
      final Map<String, UsageMetricTUStatisticsEntityTenant> tenants2 = tuStatistics.tenants();
      final var allTenantKeys = new HashSet<>(tenants1.keySet());
      allTenantKeys.addAll(tenants2.keySet());
      mergedTenants =
          allTenantKeys.stream()
              .collect(
                  Collectors.toMap(
                      key -> key,
                      key -> {
                        final UsageMetricStatisticsEntityTenant stats = tenants1.get(key);
                        final UsageMetricTUStatisticsEntityTenant tuStats = tenants2.get(key);
                        return UsageMetricsResponseItem.Builder.builder()
                            .processInstances(stats != null ? stats.rpi() : 0L)
                            .decisionInstances(stats != null ? stats.edi() : 0L)
                            .assignees(tuStats != null ? tuStats.tu() : 0L)
                            .build();
                      }));
    } else {
      mergedTenants = Map.of();
    }

    return UsageMetricsResponse.Builder.builder()
        .processInstances(statistics.totalRpi())
        .decisionInstances(statistics.totalEdi())
        .assignees(tuStatistics.totalTu())
        .tenants(mergedTenants)
        .activeTenants(statistics.at())
        .build();
  }

  public static ProcessDefinitionSearchQueryResult toProcessDefinitionSearchQueryResponse(
      final SearchQueryResult<ProcessDefinitionEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return ProcessDefinitionSearchQueryResult.Builder.builder()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toProcessDefinitions)
                .orElseGet(Collections::emptyList))
        .build();
  }

  public static ProcessDefinitionElementStatisticsQueryResult
      toProcessDefinitionElementStatisticsResult(
          final List<ProcessFlowNodeStatisticsEntity> result) {
    return ProcessDefinitionElementStatisticsQueryResult.Builder.builder()
        .items(
            result.stream()
                .map(SearchQueryResponseMapper::toProcessElementStatisticsResult)
                .toList())
        .build();
  }

  public static ProcessInstanceElementStatisticsQueryResult
      toProcessInstanceElementStatisticsResult(final List<ProcessFlowNodeStatisticsEntity> result) {
    return ProcessInstanceElementStatisticsQueryResult.Builder.builder()
        .items(
            result.stream()
                .map(SearchQueryResponseMapper::toProcessElementStatisticsResult)
                .toList())
        .build();
  }

  private static ProcessElementStatisticsResult toProcessElementStatisticsResult(
      final ProcessFlowNodeStatisticsEntity result) {
    return ProcessElementStatisticsResult.Builder.builder()
        .active(result.active())
        .canceled(result.canceled())
        .completed(result.completed())
        .elementId(result.flowNodeId())
        .incidents(result.incidents())
        .build();
  }

  public static ProcessDefinitionInstanceStatisticsQueryResult
      toProcessInstanceStatisticsQueryResult(
          final SearchQueryResult<ProcessDefinitionInstanceStatisticsEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return ProcessDefinitionInstanceStatisticsQueryResult.Builder.builder()
        .page(page)
        .items(
            result.items().stream()
                .map(SearchQueryResponseMapper::toProcessInstanceStatisticsResult)
                .toList())
        .build();
  }

  public static ProcessDefinitionInstanceVersionStatisticsQueryResult
      toProcessInstanceVersionStatisticsQueryResult(
          final SearchQueryResult<ProcessDefinitionInstanceVersionStatisticsEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return ProcessDefinitionInstanceVersionStatisticsQueryResult.Builder.builder()
        .page(page)
        .items(
            result.items().stream()
                .map(SearchQueryResponseMapper::toProcessInstanceVersionStatisticsResult)
                .toList())
        .build();
  }

  private static ProcessDefinitionInstanceStatisticsResult toProcessInstanceStatisticsResult(
      final ProcessDefinitionInstanceStatisticsEntity result) {
    return ProcessDefinitionInstanceStatisticsResult.Builder.builder()
        .activeInstancesWithIncidentCount(result.activeInstancesWithIncidentCount())
        .activeInstancesWithoutIncidentCount(result.activeInstancesWithoutIncidentCount())
        .hasMultipleVersions(result.hasMultipleVersions())
        .latestProcessDefinitionName(result.latestProcessDefinitionName())
        .processDefinitionId(result.processDefinitionId())
        .tenantId(result.tenantId())
        .build();
  }

  private static ProcessDefinitionInstanceVersionStatisticsResult
      toProcessInstanceVersionStatisticsResult(
          final ProcessDefinitionInstanceVersionStatisticsEntity result) {
    // `processDefinitionVersion` is null on exporter cache miss (version enrichment path).
    // Fall back to -1 rather than 500; caller can treat it as "unknown version".
    return ProcessDefinitionInstanceVersionStatisticsResult.Builder.builder()
        .processDefinitionId(result.processDefinitionId())
        .processDefinitionKey(keyToString(result.processDefinitionKey()))
        .tenantId(result.tenantId())
        .processDefinitionName(result.processDefinitionName())
        .processDefinitionVersion(requireNonNullElse(result.processDefinitionVersion(), -1))
        .activeInstancesWithIncidentCount(result.activeInstancesWithIncidentCount())
        .activeInstancesWithoutIncidentCount(result.activeInstancesWithoutIncidentCount())
        .build();
  }

  public static IncidentProcessInstanceStatisticsByErrorQueryResult
      toIncidentProcessInstanceStatisticsByErrorResult(
          final SearchQueryResult<IncidentProcessInstanceStatisticsByErrorEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return IncidentProcessInstanceStatisticsByErrorQueryResult.Builder.builder()
        .page(page)
        .items(
            result.items().stream()
                .map(SearchQueryResponseMapper::toIncidentProcessInstanceStatisticsByErrorResult)
                .toList())
        .build();
  }

  private static IncidentProcessInstanceStatisticsByErrorResult
      toIncidentProcessInstanceStatisticsByErrorResult(
          final IncidentProcessInstanceStatisticsByErrorEntity result) {
    // `errorMessage` is null when the incident aggregation has no representative message (e.g.
    // rows with no message columns populated in the source incidents). Fall back to empty string.
    return IncidentProcessInstanceStatisticsByErrorResult.Builder.builder()
        .activeInstancesWithErrorCount(result.activeInstancesWithErrorCount())
        .errorHashCode(result.errorHashCode())
        .errorMessage(requireNonNullElse(result.errorMessage(), ""))
        .build();
  }

  public static IncidentProcessInstanceStatisticsByDefinitionQueryResult
      toIncidentProcessInstanceStatisticsByDefinitionQueryResult(
          final SearchQueryResult<IncidentProcessInstanceStatisticsByDefinitionEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return IncidentProcessInstanceStatisticsByDefinitionQueryResult.Builder.builder()
        .page(page)
        .items(
            result.items().stream()
                .map(
                    SearchQueryResponseMapper
                        ::toIncidentProcessInstanceStatisticsByDefinitionResult)
                .toList())
        .build();
  }

  private static IncidentProcessInstanceStatisticsByDefinitionResult
      toIncidentProcessInstanceStatisticsByDefinitionResult(
          final IncidentProcessInstanceStatisticsByDefinitionEntity result) {
    // Process-definition fields are null on exporter cache miss (enrichment path). Fall back to
    // spec-compliant sentinels rather than 500 so partial statistics rows can still be returned.
    return IncidentProcessInstanceStatisticsByDefinitionResult.Builder.builder()
        .activeInstancesWithErrorCount(result.activeInstancesWithErrorCount())
        .processDefinitionId(requireNonNullElse(result.processDefinitionId(), ""))
        .processDefinitionKey(keyToString(result.processDefinitionKey()))
        .processDefinitionName(requireNonNullElse(result.processDefinitionName(), ""))
        .processDefinitionVersion(requireNonNullElse(result.processDefinitionVersion(), -1))
        .tenantId(requireNonNullElse(result.tenantId(), ""))
        .build();
  }

  public static ProcessInstanceSequenceFlowsQueryResult toSequenceFlowsResult(
      final List<SequenceFlowEntity> result) {
    return ProcessInstanceSequenceFlowsQueryResult.Builder.builder()
        .items(
            result.stream()
                .map(SearchQueryResponseMapper::toProcessInstanceSequenceFlowResult)
                .toList())
        .build();
  }

  private static ProcessInstanceSequenceFlowResult toProcessInstanceSequenceFlowResult(
      final SequenceFlowEntity result) {
    return ProcessInstanceSequenceFlowResult.Builder.builder()
        .rootProcessInstanceKey(keyToStringOrNull(result.rootProcessInstanceKey()))
        .elementId(result.flowNodeId())
        .processDefinitionId(result.processDefinitionId())
        .processDefinitionKey(keyToString(result.processDefinitionKey()))
        .processInstanceKey(keyToString(result.processInstanceKey()))
        .sequenceFlowId(result.sequenceFlowId())
        .tenantId(result.tenantId())
        .build();
  }

  public static ProcessInstanceSearchQueryResult toProcessInstanceSearchQueryResponse(
      final SearchQueryResult<ProcessInstanceEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return ProcessInstanceSearchQueryResult.Builder.builder()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toProcessInstances)
                .orElseGet(Collections::emptyList))
        .build();
  }

  public static JobSearchQueryResult toJobSearchQueryResponse(
      final SearchQueryResult<JobEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return JobSearchQueryResult.Builder.builder()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toJobs)
                .orElseGet(Collections::emptyList))
        .build();
  }

  public static RoleSearchQueryResult toRoleSearchQueryResponse(
      final SearchQueryResult<RoleEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return RoleSearchQueryResult.Builder.builder()
        .page(page)
        .items(
            ofNullable(result.items()).map(SearchQueryResponseMapper::toRoles).orElseGet(List::of))
        .build();
  }

  public static RoleGroupSearchResult toRoleGroupSearchQueryResponse(
      final SearchQueryResult<RoleMemberEntity> result) {
    return RoleGroupSearchResult.Builder.builder()
        .page(toSearchQueryPageResponse(result))
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toRoleGroups)
                .orElseGet(List::of))
        .build();
  }

  public static RoleUserSearchResult toRoleUserSearchQueryResponse(
      final SearchQueryResult<RoleMemberEntity> result) {
    return RoleUserSearchResult.Builder.builder()
        .page(toSearchQueryPageResponse(result))
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toRoleUsers)
                .orElseGet(List::of))
        .build();
  }

  public static RoleClientSearchResult toRoleClientSearchQueryResponse(
      final SearchQueryResult<RoleMemberEntity> result) {
    return RoleClientSearchResult.Builder.builder()
        .page(toSearchQueryPageResponse(result))
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toRoleClients)
                .orElseGet(List::of))
        .build();
  }

  public static GroupSearchQueryResult toGroupSearchQueryResponse(
      final SearchQueryResult<GroupEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return GroupSearchQueryResult.Builder.builder()
        .page(page)
        .items(
            ofNullable(result.items()).map(SearchQueryResponseMapper::toGroups).orElseGet(List::of))
        .build();
  }

  public static GroupUserSearchResult toGroupUserSearchQueryResponse(
      final SearchQueryResult<GroupMemberEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return GroupUserSearchResult.Builder.builder()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toGroupUsers)
                .orElseGet(List::of))
        .build();
  }

  public static GroupClientSearchResult toGroupClientSearchQueryResponse(
      final SearchQueryResult<GroupMemberEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return GroupClientSearchResult.Builder.builder()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toGroupClients)
                .orElseGet(List::of))
        .build();
  }

  public static TenantSearchQueryResult toTenantSearchQueryResponse(
      final SearchQueryResult<TenantEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return TenantSearchQueryResult.Builder.builder()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toTenants)
                .orElseGet(List::of))
        .build();
  }

  public static TenantGroupSearchResult toTenantGroupSearchQueryResponse(
      final SearchQueryResult<TenantMemberEntity> result) {
    return TenantGroupSearchResult.Builder.builder()
        .page(toSearchQueryPageResponse(result))
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toTenantGroups)
                .orElseGet(List::of))
        .build();
  }

  public static TenantUserSearchResult toTenantUserSearchQueryResponse(
      final SearchQueryResult<TenantMemberEntity> result) {
    return TenantUserSearchResult.Builder.builder()
        .page(toSearchQueryPageResponse(result))
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toTenantUsers)
                .orElseGet(List::of))
        .build();
  }

  public static TenantClientSearchResult toTenantClientSearchQueryResponse(
      final SearchQueryResult<TenantMemberEntity> result) {
    return TenantClientSearchResult.Builder.builder()
        .page(toSearchQueryPageResponse(result))
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toTenantClients)
                .orElseGet(List::of))
        .build();
  }

  public static MappingRuleSearchQueryResult toMappingRuleSearchQueryResponse(
      final SearchQueryResult<MappingRuleEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return MappingRuleSearchQueryResult.Builder.builder()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toMappingRules)
                .orElseGet(List::of))
        .build();
  }

  public static DecisionDefinitionSearchQueryResult toDecisionDefinitionSearchQueryResponse(
      final SearchQueryResult<DecisionDefinitionEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return DecisionDefinitionSearchQueryResult.Builder.builder()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toDecisionDefinitions)
                .orElseGet(Collections::emptyList))
        .build();
  }

  public static DecisionRequirementsSearchQueryResult toDecisionRequirementsSearchQueryResponse(
      final SearchQueryResult<DecisionRequirementsEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return DecisionRequirementsSearchQueryResult.Builder.builder()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toDecisionRequirements)
                .orElseGet(Collections::emptyList))
        .build();
  }

  public static ElementInstanceSearchQueryResult toElementInstanceSearchQueryResponse(
      final SearchQueryResult<FlowNodeInstanceEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return ElementInstanceSearchQueryResult.Builder.builder()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(instances -> toElementInstance(instances))
                .orElseGet(Collections::emptyList))
        .build();
  }

  public static DecisionInstanceSearchQueryResult toDecisionInstanceSearchQueryResponse(
      final SearchQueryResult<DecisionInstanceEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return DecisionInstanceSearchQueryResult.Builder.builder()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toDecisionInstances)
                .orElseGet(Collections::emptyList))
        .build();
  }

  public static UserTaskSearchQueryResult toUserTaskSearchQueryResponse(
      final SearchQueryResult<UserTaskEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return UserTaskSearchQueryResult.Builder.builder()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(tasks -> toUserTasks(tasks))
                .orElseGet(Collections::emptyList))
        .build();
  }

  public static UserSearchResult toUserSearchQueryResponse(
      final SearchQueryResult<UserEntity> result) {
    return UserSearchResult.Builder.builder()
        .page(toSearchQueryPageResponse(result))
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toUsers)
                .orElseGet(Collections::emptyList))
        .build();
  }

  public static BatchOperationSearchQueryResult toBatchOperationSearchQueryResult(
      final SearchQueryResult<BatchOperationEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return BatchOperationSearchQueryResult.Builder.builder()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toBatchOperations)
                .orElseGet(Collections::emptyList))
        .build();
  }

  public static BatchOperationItemSearchQueryResult toBatchOperationItemSearchQueryResult(
      final SearchQueryResult<BatchOperationItemEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return BatchOperationItemSearchQueryResult.Builder.builder()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toBatchOperationItems)
                .orElseGet(Collections::emptyList))
        .build();
  }

  public static IncidentSearchQueryResult toIncidentSearchQueryResponse(
      final SearchQueryResult<IncidentEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return IncidentSearchQueryResult.Builder.builder()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toIncidents)
                .orElseGet(Collections::emptyList))
        .build();
  }

  public static MessageSubscriptionSearchQueryResult toMessageSubscriptionSearchQueryResponse(
      final SearchQueryResult<MessageSubscriptionEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return MessageSubscriptionSearchQueryResult.Builder.builder()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toMessageSubscriptions)
                .orElseGet(Collections::emptyList))
        .build();
  }

  public static CorrelatedMessageSubscriptionSearchQueryResult
      toCorrelatedMessageSubscriptionSearchQueryResponse(
          final SearchQueryResult<CorrelatedMessageSubscriptionEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return CorrelatedMessageSubscriptionSearchQueryResult.Builder.builder()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toCorrelatedMessageSubscriptions)
                .orElseGet(Collections::emptyList))
        .build();
  }

  private static SearchQueryPageResponse toSearchQueryPageResponse(
      final SearchQueryResult<?> result) {

    return SearchQueryPageResponse.Builder.builder()
        .totalItems(result.total())
        .hasMoreTotalItems(result.hasMoreTotalItems())
        .startCursor(result.startCursor())
        .endCursor(result.endCursor())
        .build();
  }

  private static List<ProcessDefinitionResult> toProcessDefinitions(
      final List<ProcessDefinitionEntity> processDefinitions) {
    return processDefinitions.stream().map(SearchQueryResponseMapper::toProcessDefinition).toList();
  }

  public static ProcessDefinitionResult toProcessDefinition(final ProcessDefinitionEntity entity) {
    return ProcessDefinitionResult.Builder.builder()
        .processDefinitionKey(
            requireNonNull(
                keyToStringOrNull(entity.processDefinitionKey()), "processDefinitionKey"))
        .name(entity.name())
        .resourceName(entity.resourceName())
        .version(entity.version())
        .versionTag(entity.versionTag())
        .processDefinitionId(entity.processDefinitionId())
        .tenantId(entity.tenantId())
        .hasStartForm(StringUtils.isNotBlank(entity.formId()))
        .build();
  }

  private static List<ProcessInstanceResult> toProcessInstances(
      final List<ProcessInstanceEntity> instances) {
    return instances.stream().map(SearchQueryResponseMapper::toProcessInstance).toList();
  }

  private static List<JobSearchResult> toJobs(final List<JobEntity> jobs) {
    return jobs.stream().map(SearchQueryResponseMapper::toJob).toList();
  }

  private static JobSearchResult toJob(final JobEntity job) {
    return JobSearchResult.Builder.builder()
        .customHeaders(job.customHeaders())
        .elementId(job.elementId())
        .elementInstanceKey(keyToString(job.elementInstanceKey()))
        .hasFailedWithRetriesLeft(job.hasFailedWithRetriesLeft())
        .jobKey(keyToString(job.jobKey()))
        .kind(JobKindEnum.fromValue(job.kind().name()))
        .listenerEventType(JobListenerEventTypeEnum.fromValue(job.listenerEventType().name()))
        .processDefinitionId(job.processDefinitionId())
        .processDefinitionKey(keyToString(job.processDefinitionKey()))
        .processInstanceKey(keyToString(job.processInstanceKey()))
        .retries(job.retries())
        .state(JobStateEnum.fromValue(job.state().name()))
        .tenantId(job.tenantId())
        .type(job.type())
        .worker(job.worker())
        .rootProcessInstanceKey(keyToStringOrNull(job.rootProcessInstanceKey()))
        .creationTime(formatDateOrNull(job.creationTime()))
        .deadline(formatDateOrNull(job.deadline()))
        .deniedReason(job.deniedReason())
        .endTime(formatDateOrNull(job.endTime()))
        .errorCode(job.errorCode())
        .errorMessage(job.errorMessage())
        .isDenied(job.isDenied())
        .lastUpdateTime(formatDate(job.lastUpdateTime()))
        .build();
  }

  public static ProcessInstanceResult toProcessInstance(final ProcessInstanceEntity p) {
    // processDefinitionId/Version/Key are @Nullable on the entity due to the onlyKeys(true)
    // source projection in ProcessInstanceItemProvider#fetchItemPage; that path doesn't reach
    // this mapper, but the contract has to admit the null so we fall back to spec-compliant
    // sentinels here. Tracked: #51999. state and startDate are nullable for record-ordering
    // reasons (see entity).
    return ProcessInstanceResult.Builder.builder()
        .processDefinitionId(requireNonNullElse(p.processDefinitionId(), ""))
        .processDefinitionName(p.processDefinitionName())
        .processDefinitionVersion(requireNonNullElse(p.processDefinitionVersion(), -1))
        .processDefinitionVersionTag(p.processDefinitionVersionTag())
        .startDate(requireNonNullElse(formatDateOrNull(p.startDate()), EPOCH_DATE_SENTINEL))
        .endDate(formatDateOrNull(p.endDate()))
        .state(
            toProtocolState(
                requireNonNullElse(p.state(), ProcessInstanceEntity.ProcessInstanceState.ACTIVE)))
        .hasIncident(Boolean.TRUE.equals(p.hasIncident()))
        .tenantId(p.tenantId())
        .processInstanceKey(keyToString(p.processInstanceKey()))
        .processDefinitionKey(requireNonNullElse(keyToStringOrNull(p.processDefinitionKey()), ""))
        .parentProcessInstanceKey(keyToStringOrNull(p.parentProcessInstanceKey()))
        .parentElementInstanceKey(keyToStringOrNull(p.parentFlowNodeInstanceKey()))
        .rootProcessInstanceKey(keyToStringOrNull(p.rootProcessInstanceKey()))
        .tags(p.tags())
        .businessId(emptyToNull(p.businessId()))
        .build();
  }

  public static List<BatchOperationResponse> toBatchOperations(
      final List<BatchOperationEntity> batchOperations) {
    return batchOperations.stream().map(SearchQueryResponseMapper::toBatchOperation).toList();
  }

  public static BatchOperationResponse toBatchOperation(final BatchOperationEntity entity) {
    return BatchOperationResponse.Builder.builder()
        .batchOperationKey(entity.batchOperationKey())
        .batchOperationType(BatchOperationTypeEnum.fromValue(entity.operationType().name()))
        .state(BatchOperationStateEnum.fromValue(entity.state().name()))
        .operationsCompletedCount(entity.operationsCompletedCount())
        .operationsFailedCount(entity.operationsFailedCount())
        .operationsTotalCount(entity.operationsTotalCount())
        .actorType(
            ofNullable(entity.actorType())
                .map(Enum::name)
                .map(AuditLogActorTypeEnum::fromValue)
                .orElse(null))
        .actorId(entity.actorId())
        .errors(
            ofNullable(entity.errors())
                .map(
                    errors ->
                        errors.stream()
                            .map(SearchQueryResponseMapper::toBatchOperationError)
                            .toList())
                .orElseGet(Collections::emptyList))
        .endDate(formatDateOrNull(entity.endDate()))
        .startDate(formatDateOrNull(entity.startDate()))
        .build();
  }

  public static BatchOperationItemSearchQueryResult toBatchOperationItemSearchQueryResult(
      final List<BatchOperationItemEntity> batchOperations) {
    return BatchOperationItemSearchQueryResult.Builder.builder()
        .page(
            SearchQueryPageResponse.Builder.builder()
                .totalItems((long) batchOperations.size())
                .hasMoreTotalItems(false)
                .startCursor(null)
                .endCursor(null)
                .build())
        .items(
            batchOperations.stream().map(SearchQueryResponseMapper::toBatchOperationItem).toList())
        .build();
  }

  public static List<BatchOperationItemResponse> toBatchOperationItems(
      final List<BatchOperationItemEntity> batchOperationItems) {
    return batchOperationItems.stream()
        .map(SearchQueryResponseMapper::toBatchOperationItem)
        .toList();
  }

  public static BatchOperationItemResponse toBatchOperationItem(
      final BatchOperationItemEntity entity) {
    return BatchOperationItemResponse.Builder.builder()
        .rootProcessInstanceKey(keyToStringOrNull(entity.rootProcessInstanceKey()))
        .batchOperationKey(entity.batchOperationKey())
        .errorMessage(entity.errorMessage())
        .itemKey(keyToString(entity.itemKey()))
        .operationType(BatchOperationTypeEnum.fromValue(entity.operationType().name()))
        .processedDate(formatDateOrNull(entity.processedDate()))
        // `processInstanceKey` is null for batch-op targets that are not process instances (e.g.
        // DELETE_DECISION_INSTANCE, DELETE_DECISION_DEFINITION). Spec declares it nullable.
        .processInstanceKey(keyToStringOrNull(entity.processInstanceKey()))
        .state(BatchOperationItemResponse.StateEnum.fromValue(entity.state().name()))
        .build();
  }

  private static BatchOperationError toBatchOperationError(
      final BatchOperationErrorEntity batchOperationErrorEntity) {
    return BatchOperationError.Builder.builder()
        .message(batchOperationErrorEntity.message())
        .partitionId(batchOperationErrorEntity.partitionId())
        .type(TypeEnum.fromValue(batchOperationErrorEntity.type()))
        .build();
  }

  private static List<RoleResult> toRoles(final List<RoleEntity> roles) {
    return roles.stream().map(SearchQueryResponseMapper::toRole).toList();
  }

  public static RoleResult toRole(final RoleEntity roleEntity) {
    return RoleResult.Builder.builder()
        .roleId(roleEntity.roleId())
        .name(roleEntity.name())
        .description(roleEntity.description())
        .build();
  }

  private static List<GroupResult> toGroups(final List<GroupEntity> groups) {
    return groups.stream().map(SearchQueryResponseMapper::toGroup).toList();
  }

  public static GroupResult toGroup(final GroupEntity groupEntity) {
    return GroupResult.Builder.builder()
        .name(groupEntity.name())
        .groupId(groupEntity.groupId())
        .description(groupEntity.description())
        .build();
  }

  private static List<GroupUserResult> toGroupUsers(final List<GroupMemberEntity> groupMembers) {
    return groupMembers.stream().map(SearchQueryResponseMapper::toGroupUser).toList();
  }

  private static GroupUserResult toGroupUser(final GroupMemberEntity groupMember) {
    return GroupUserResult.Builder.builder().username(groupMember.id()).build();
  }

  private static List<GroupClientResult> toGroupClients(
      final List<GroupMemberEntity> groupMembers) {
    return groupMembers.stream().map(SearchQueryResponseMapper::toGroupClient).toList();
  }

  private static GroupClientResult toGroupClient(final GroupMemberEntity groupMember) {
    return GroupClientResult.Builder.builder().clientId(groupMember.id()).build();
  }

  private static List<TenantResult> toTenants(final List<TenantEntity> tenants) {
    return tenants.stream().map(SearchQueryResponseMapper::toTenant).toList();
  }

  public static TenantResult toTenant(final TenantEntity tenantEntity) {
    return TenantResult.Builder.builder()
        .tenantId(tenantEntity.tenantId())
        .name(tenantEntity.name())
        .description(tenantEntity.description())
        .build();
  }

  private static List<TenantUserResult> toTenantUsers(final List<TenantMemberEntity> members) {
    return members.stream().map(SearchQueryResponseMapper::toTenantUser).toList();
  }

  private static List<TenantClientResult> toTenantClients(final List<TenantMemberEntity> members) {
    return members.stream().map(SearchQueryResponseMapper::toTenantClient).toList();
  }

  private static List<TenantGroupResult> toTenantGroups(final List<TenantMemberEntity> members) {
    return members.stream().map(SearchQueryResponseMapper::toTenantGroup).toList();
  }

  private static TenantGroupResult toTenantGroup(final TenantMemberEntity tenantMember) {
    return TenantGroupResult.Builder.builder().groupId(tenantMember.id()).build();
  }

  private static TenantUserResult toTenantUser(final TenantMemberEntity tenantMember) {
    return TenantUserResult.Builder.builder().username(tenantMember.id()).build();
  }

  private static TenantClientResult toTenantClient(final TenantMemberEntity tenantMember) {
    return TenantClientResult.Builder.builder().clientId(tenantMember.id()).build();
  }

  private static List<RoleGroupResult> toRoleGroups(final List<RoleMemberEntity> members) {
    return members.stream().map(SearchQueryResponseMapper::toRoleGroup).toList();
  }

  private static List<RoleUserResult> toRoleUsers(final List<RoleMemberEntity> members) {
    return members.stream().map(SearchQueryResponseMapper::toRoleUser).toList();
  }

  private static List<RoleClientResult> toRoleClients(final List<RoleMemberEntity> members) {
    return members.stream().map(SearchQueryResponseMapper::toRoleClient).toList();
  }

  private static RoleGroupResult toRoleGroup(final RoleMemberEntity roleMember) {
    return RoleGroupResult.Builder.builder().groupId(roleMember.id()).build();
  }

  private static RoleUserResult toRoleUser(final RoleMemberEntity roleMember) {
    return RoleUserResult.Builder.builder().username(roleMember.id()).build();
  }

  private static RoleClientResult toRoleClient(final RoleMemberEntity roleMember) {
    return RoleClientResult.Builder.builder().clientId(roleMember.id()).build();
  }

  private static List<MappingRuleResult> toMappingRules(
      final List<MappingRuleEntity> mappingRules) {
    return mappingRules.stream().map(SearchQueryResponseMapper::toMappingRule).toList();
  }

  public static MappingRuleResult toMappingRule(final MappingRuleEntity mappingRuleEntity) {
    return MappingRuleResult.Builder.builder()
        .claimName(mappingRuleEntity.claimName())
        .claimValue(mappingRuleEntity.claimValue())
        .name(mappingRuleEntity.name())
        .mappingRuleId(mappingRuleEntity.mappingRuleId())
        .build();
  }

  private static List<DecisionDefinitionResult> toDecisionDefinitions(
      final List<DecisionDefinitionEntity> instances) {
    return instances.stream().map(SearchQueryResponseMapper::toDecisionDefinition).toList();
  }

  private static List<DecisionRequirementsResult> toDecisionRequirements(
      final List<DecisionRequirementsEntity> instances) {
    return instances.stream().map(SearchQueryResponseMapper::toDecisionRequirements).toList();
  }

  private static List<ElementInstanceResult> toElementInstance(
      final List<FlowNodeInstanceEntity> instances) {
    return instances.stream().map(SearchQueryResponseMapper::toElementInstance).toList();
  }

  public static ElementInstanceResult toElementInstance(final FlowNodeInstanceEntity instance) {
    // `flowNodeName` is null on exporter cache miss (process definition not yet cached when the
    // event is handled); fall back to the BPMN element id, which is always present. `hasIncident`
    // is populated asynchronously by IncidentUpdateTask. `startDate` is only written on
    // AI_START_STATES intents and is absent on docs first created by a later intent. Per 8.8
    // policy we fall back to spec-compliant sentinels rather than 500 on rare transient gaps.
    return ElementInstanceResult.Builder.builder()
        .processDefinitionId(instance.processDefinitionId())
        .startDate(requireNonNullElse(formatDateOrNull(instance.startDate()), EPOCH_DATE_SENTINEL))
        .elementId(instance.flowNodeId())
        .elementName(requireNonNullElse(instance.flowNodeName(), instance.flowNodeId()))
        .type(ElementInstanceResult.TypeEnum.fromValue(instance.type().name()))
        .state(ElementInstanceStateEnum.fromValue(instance.state().name()))
        .hasIncident(Boolean.TRUE.equals(instance.hasIncident()))
        .tenantId(instance.tenantId())
        .elementInstanceKey(keyToString(instance.flowNodeInstanceKey()))
        .processInstanceKey(keyToString(instance.processInstanceKey()))
        .processDefinitionKey(keyToString(instance.processDefinitionKey()))
        .rootProcessInstanceKey(keyToStringOrNull(instance.rootProcessInstanceKey()))
        .endDate(formatDateOrNull(instance.endDate()))
        .incidentKey(keyToStringOrNull(instance.incidentKey()))
        .build();
  }

  public static DecisionDefinitionResult toDecisionDefinition(final DecisionDefinitionEntity d) {
    return DecisionDefinitionResult.Builder.builder()
        .decisionDefinitionId(d.decisionDefinitionId())
        .decisionDefinitionKey(keyToString(d.decisionDefinitionKey()))
        .decisionRequirementsId(d.decisionRequirementsId())
        .decisionRequirementsKey(keyToString(d.decisionRequirementsKey()))
        .decisionRequirementsName(ofNullable(d.decisionRequirementsName()).orElse(""))
        .decisionRequirementsVersion(d.decisionRequirementsVersion())
        .name(d.name())
        .tenantId(d.tenantId())
        .version(d.version())
        .build();
  }

  public static DecisionRequirementsResult toDecisionRequirements(
      final DecisionRequirementsEntity d) {
    return DecisionRequirementsResult.Builder.builder()
        .decisionRequirementsId(d.decisionRequirementsId())
        .decisionRequirementsKey(keyToString(d.decisionRequirementsKey()))
        .decisionRequirementsName(d.name())
        .resourceName(d.resourceName())
        .tenantId(d.tenantId())
        .version(d.version())
        .build();
  }

  private static List<UserTaskResult> toUserTasks(final List<UserTaskEntity> tasks) {
    return tasks.stream()
        .map(
            (final UserTaskEntity t) -> {
              return toUserTask(t);
            })
        .toList();
  }

  public static List<IncidentResult> toIncidents(final List<IncidentEntity> incidents) {
    return incidents.stream().map(SearchQueryResponseMapper::toIncident).toList();
  }

  public static IncidentResult toIncident(final IncidentEntity t) {
    return IncidentResult.Builder.builder()
        .incidentKey(keyToString(t.incidentKey()))
        .processDefinitionKey(keyToString(t.processDefinitionKey()))
        .processInstanceKey(keyToString(t.processInstanceKey()))
        .elementInstanceKey(keyToString(t.flowNodeInstanceKey()))
        .creationTime(formatDate(t.creationTime()))
        .state(
            ofNullable(t.state())
                .map(Enum::name)
                .map(IncidentStateEnum::fromValue)
                .orElse(IncidentStateEnum.UNKNOWN))
        .errorType(
            ofNullable(t.errorType())
                .map(Enum::name)
                .map(IncidentErrorTypeEnum::fromValue)
                .orElse(IncidentErrorTypeEnum.UNKNOWN))
        .errorMessage(t.errorMessage())
        .elementId(t.flowNodeId())
        .processDefinitionId(t.processDefinitionId())
        .rootProcessInstanceKey(keyToStringOrNull(t.rootProcessInstanceKey()))
        .jobKey(keyToStringOrNull(t.jobKey()))
        .tenantId(t.tenantId())
        .build();
  }

  private static List<MessageSubscriptionResult> toMessageSubscriptions(
      final List<MessageSubscriptionEntity> messageSubscriptions) {
    return messageSubscriptions.stream()
        .map(SearchQueryResponseMapper::toMessageSubscription)
        .toList();
  }

  private static MessageSubscriptionResult toMessageSubscription(
      final MessageSubscriptionEntity messageSubscription) {
    return MessageSubscriptionResult.Builder.builder()
        .rootProcessInstanceKey(keyToStringOrNull(messageSubscription.rootProcessInstanceKey()))
        .correlationKey(messageSubscription.correlationKey())
        .elementId(messageSubscription.flowNodeId())
        .elementInstanceKey(keyToStringOrNull(messageSubscription.flowNodeInstanceKey()))
        .extensionProperties(messageSubscription.extensionProperties())
        .inboundConnectorType(messageSubscription.inboundConnectorType())
        // `dateTime` can be absent on subscriptions that predate the field being populated;
        // fall back to the epoch sentinel (see EPOCH_DATE_SENTINEL) rather than 500.
        .lastUpdatedDate(
            requireNonNullElse(
                formatDateOrNull(messageSubscription.dateTime()), EPOCH_DATE_SENTINEL))
        .messageName(messageSubscription.messageName())
        .messageSubscriptionKey(keyToString(messageSubscription.messageSubscriptionKey()))
        .messageSubscriptionState(
            MessageSubscriptionStateEnum.fromValue(
                messageSubscription.messageSubscriptionState().name()))
        .messageSubscriptionType(
            MessageSubscriptionTypeEnum.fromValue(
                messageSubscription.messageSubscriptionType().name()))
        .processDefinitionId(messageSubscription.processDefinitionId())
        .processDefinitionKey(keyToStringOrNull(messageSubscription.processDefinitionKey()))
        .processDefinitionName(messageSubscription.processDefinitionName())
        .processDefinitionVersion(messageSubscription.processDefinitionVersion())
        .processInstanceKey(keyToStringOrNull(messageSubscription.processInstanceKey()))
        .tenantId(messageSubscription.tenantId())
        .toolName(messageSubscription.toolName())
        .build();
  }

  private static List<CorrelatedMessageSubscriptionResult> toCorrelatedMessageSubscriptions(
      final List<CorrelatedMessageSubscriptionEntity> correlatedMessageSubscriptions) {
    return correlatedMessageSubscriptions.stream()
        .map(SearchQueryResponseMapper::toCorrelatedMessageSubscription)
        .toList();
  }

  private static CorrelatedMessageSubscriptionResult toCorrelatedMessageSubscription(
      final CorrelatedMessageSubscriptionEntity correlatedMessageSubscription) {
    return CorrelatedMessageSubscriptionResult.Builder.builder()
        .correlationKey(correlatedMessageSubscription.correlationKey())
        .correlationTime(formatDate(correlatedMessageSubscription.correlationTime()))
        .elementId(correlatedMessageSubscription.flowNodeId())
        .messageKey(keyToString(correlatedMessageSubscription.messageKey()))
        .messageName(correlatedMessageSubscription.messageName())
        .partitionId(correlatedMessageSubscription.partitionId())
        .processDefinitionId(correlatedMessageSubscription.processDefinitionId())
        .processInstanceKey(keyToString(correlatedMessageSubscription.processInstanceKey()))
        .subscriptionKey(keyToString(correlatedMessageSubscription.subscriptionKey()))
        .tenantId(correlatedMessageSubscription.tenantId())
        .rootProcessInstanceKey(
            keyToStringOrNull(correlatedMessageSubscription.rootProcessInstanceKey()))
        .elementInstanceKey(keyToStringOrNull(correlatedMessageSubscription.flowNodeInstanceKey()))
        .processDefinitionKey(keyToString(correlatedMessageSubscription.processDefinitionKey()))
        .build();
  }

  public static UserTaskResult toUserTask(final UserTaskEntity t) {
    return UserTaskResult.Builder.builder()
        .tenantId(t.tenantId())
        .userTaskKey(keyToString(t.userTaskKey()))
        .name(t.name())
        .processInstanceKey(keyToString(t.processInstanceKey()))
        .rootProcessInstanceKey(keyToStringOrNull(t.rootProcessInstanceKey()))
        .processDefinitionKey(keyToString(t.processDefinitionKey()))
        .elementInstanceKey(keyToString(t.elementInstanceKey()))
        .processDefinitionId(t.processDefinitionId())
        .processName(t.processName())
        .state(UserTaskStateEnum.fromValue(t.state().name()))
        .candidateGroups(t.candidateGroups())
        .candidateUsers(t.candidateUsers())
        .elementId(t.elementId())
        .creationDate(formatDate(t.creationDate()))
        .customHeaders(t.customHeaders())
        // `priority` is null when the user-task handler path did not propagate it (e.g. job-based
        // tasks before 8.8). Fall back to the Zeebe / BPMN default of 50.
        .priority(requireNonNullElse(t.priority(), 50))
        .tags(t.tags())
        .assignee(t.assignee())
        .completionDate(formatDateOrNull(t.completionDate()))
        .dueDate(formatDateOrNull(t.dueDate()))
        .externalFormReference(t.externalFormReference())
        .followUpDate(formatDateOrNull(t.followUpDate()))
        .processDefinitionVersion(t.processDefinitionVersion())
        .formKey(keyToStringOrNull(t.formKey()))
        .build();
  }

  public static FormResult toFormItem(final FormEntity f) {
    return FormResult.Builder.builder()
        .tenantId(f.tenantId())
        .formId(f.formId())
        .schema(f.schema())
        .version(f.version())
        .formKey(keyToString(f.formKey()))
        .build();
  }

  public static List<UserResult> toUsers(final List<UserEntity> users) {
    return users.stream().map(SearchQueryResponseMapper::toUser).toList();
  }

  public static UserResult toUser(final UserEntity user) {
    return UserResult.Builder.builder()
        .username(user.username())
        .name(user.name())
        .email(user.email())
        .build();
  }

  public static CamundaUserResult toCamundaUser(final CamundaUserDTO camundaUser) {
    return CamundaUserResult.Builder.builder()
        .authorizedComponents(camundaUser.authorizedComponents())
        .tenants(toTenants(camundaUser.tenants()))
        .groups(camundaUser.groups())
        .roles(camundaUser.roles())
        .salesPlanType(camundaUser.salesPlanType())
        .c8Links(toCamundaUserResultC8Links(camundaUser.c8Links()))
        .canLogout(camundaUser.canLogout())
        .displayName(camundaUser.displayName())
        .email(camundaUser.email())
        .username(camundaUser.username())
        .build();
  }

  private static Map<String, String> toCamundaUserResultC8Links(
      final Map<AppName, String> c8Links) {
    return c8Links.entrySet().stream()
        .collect(
            toMap(
                e -> {
                  final AppName appName = e.getKey();
                  return appName == AppName.IDENTITY ? "admin" : appName.getValue();
                },
                Map.Entry::getValue,
                (v1, v2) -> v1));
  }

  private static List<DecisionInstanceResult> toDecisionInstances(
      final List<DecisionInstanceEntity> instances) {
    return instances.stream().map(SearchQueryResponseMapper::toDecisionInstance).toList();
  }

  public static DecisionInstanceResult toDecisionInstance(final DecisionInstanceEntity entity) {
    return DecisionInstanceResult.Builder.builder()
        .decisionDefinitionId(entity.decisionDefinitionId())
        .decisionDefinitionKey(keyToString(entity.decisionDefinitionKey()))
        .decisionDefinitionName(entity.decisionDefinitionName())
        .decisionDefinitionType(toDecisionDefinitionTypeEnum(entity.decisionDefinitionType()))
        .decisionDefinitionVersion(
            requireNonNull(entity.decisionDefinitionVersion(), "decisionDefinitionVersion"))
        .decisionEvaluationInstanceKey(entity.decisionInstanceId())
        .decisionEvaluationKey(keyToString(entity.decisionInstanceKey()))
        .elementInstanceKey(keyToStringOrNull(entity.flowNodeInstanceKey()))
        .evaluationDate(formatDate(entity.evaluationDate()))
        .evaluationFailure(entity.evaluationFailure())
        .processDefinitionKey(keyToStringOrNull(entity.processDefinitionKey()))
        .processInstanceKey(keyToStringOrNull(entity.processInstanceKey()))
        .result(entity.result())
        .rootDecisionDefinitionKey(
            requireNonNull(
                keyToStringOrNull(entity.rootDecisionDefinitionKey()), "rootDecisionDefinitionKey"))
        .rootProcessInstanceKey(keyToStringOrNull(entity.rootProcessInstanceKey()))
        .state(toDecisionInstanceStateEnum(entity.state()))
        .tenantId(entity.tenantId())
        .build();
  }

  public static DecisionInstanceGetQueryResult toDecisionInstanceGetQueryResponse(
      final DecisionInstanceEntity entity) {
    return DecisionInstanceGetQueryResult.Builder.builder()
        .decisionDefinitionId(entity.decisionDefinitionId())
        .decisionDefinitionKey(keyToString(entity.decisionDefinitionKey()))
        .decisionDefinitionName(entity.decisionDefinitionName())
        .decisionDefinitionType(toDecisionDefinitionTypeEnum(entity.decisionDefinitionType()))
        .decisionDefinitionVersion(
            requireNonNull(entity.decisionDefinitionVersion(), "decisionDefinitionVersion"))
        .decisionEvaluationInstanceKey(entity.decisionInstanceId())
        .decisionEvaluationKey(keyToString(entity.decisionInstanceKey()))
        .elementInstanceKey(keyToStringOrNull(entity.flowNodeInstanceKey()))
        .evaluationDate(formatDate(entity.evaluationDate()))
        .evaluationFailure(entity.evaluationFailure())
        .processDefinitionKey(keyToStringOrNull(entity.processDefinitionKey()))
        .processInstanceKey(keyToStringOrNull(entity.processInstanceKey()))
        .result(entity.result())
        .rootDecisionDefinitionKey(
            requireNonNull(
                keyToStringOrNull(entity.rootDecisionDefinitionKey()), "rootDecisionDefinitionKey"))
        .rootProcessInstanceKey(keyToStringOrNull(entity.rootProcessInstanceKey()))
        .state(toDecisionInstanceStateEnum(entity.state()))
        .tenantId(entity.tenantId())
        .evaluatedInputs(
            requireNonNull(toEvaluatedInputs(entity.evaluatedInputs()), "evaluatedInputs"))
        .matchedRules(requireNonNull(toMatchedRules(entity.evaluatedOutputs()), "matchedRules"))
        .build();
  }

  private static @Nullable List<EvaluatedDecisionInputItem> toEvaluatedInputs(
      final @Nullable List<DecisionInstanceInputEntity> decisionInstanceInputEntities) {
    if (decisionInstanceInputEntities == null) {
      return null;
    }
    return decisionInstanceInputEntities.stream()
        .map(
            input ->
                EvaluatedDecisionInputItem.Builder.builder()
                    .inputId(requireNonNull(input.inputId(), "inputId"))
                    .inputName(requireNonNull(input.inputName(), "inputName"))
                    .inputValue(requireNonNull(input.inputValue(), "inputValue"))
                    .build())
        .toList();
  }

  private static @Nullable List<MatchedDecisionRuleItem> toMatchedRules(
      final @Nullable List<DecisionInstanceOutputEntity> decisionInstanceOutputEntities) {
    if (decisionInstanceOutputEntities == null) {
      return null;
    }
    final var outputEntitiesMappedByRule =
        decisionInstanceOutputEntities.stream()
            .collect(
                Collectors.groupingBy(
                    e -> new RuleIdentifier(requireNonNull(e.ruleId(), "ruleId"), e.ruleIndex())));
    return outputEntitiesMappedByRule.entrySet().stream()
        .map(
            entry -> {
              final var ruleIdentifier = entry.getKey();
              final var outputs = entry.getValue();
              return MatchedDecisionRuleItem.Builder.builder()
                  .evaluatedOutputs(
                      outputs.stream()
                          .map(
                              output ->
                                  EvaluatedDecisionOutputItem.Builder.builder()
                                      .ruleId(ruleIdentifier.ruleId())
                                      .ruleIndex(ruleIdentifier.ruleIndex())
                                      .outputId(requireNonNull(output.outputId(), "outputId"))
                                      .outputName(requireNonNull(output.outputName(), "outputName"))
                                      .outputValue(
                                          requireNonNull(output.outputValue(), "outputValue"))
                                      .build())
                          .toList())
                  .ruleId(requireNonNull(ruleIdentifier.ruleId(), "ruleId"))
                  .ruleIndex(ruleIdentifier.ruleIndex())
                  .build();
            })
        .toList();
  }

  private static DecisionInstanceStateEnum toDecisionInstanceStateEnum(
      final DecisionInstanceState state) {
    return switch (state) {
      case EVALUATED -> DecisionInstanceStateEnum.EVALUATED;
      case FAILED -> DecisionInstanceStateEnum.FAILED;
      case UNSPECIFIED -> DecisionInstanceStateEnum.UNSPECIFIED;
      default -> DecisionInstanceStateEnum.UNKNOWN;
    };
  }

  private static DecisionDefinitionTypeEnum toDecisionDefinitionTypeEnum(
      final DecisionDefinitionType decisionDefinitionType) {
    return switch (decisionDefinitionType) {
      case DECISION_TABLE -> DecisionDefinitionTypeEnum.DECISION_TABLE;
      case LITERAL_EXPRESSION -> DecisionDefinitionTypeEnum.LITERAL_EXPRESSION;
      case UNSPECIFIED -> DecisionDefinitionTypeEnum.UNSPECIFIED;
      default -> DecisionDefinitionTypeEnum.UNKNOWN;
    };
  }

  public static VariableSearchQueryResult toVariableSearchQueryResponse(
      final SearchQueryResult<VariableEntity> result, final boolean truncateValues) {
    final var page = toSearchQueryPageResponse(result);
    return VariableSearchQueryResult.Builder.builder()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(entity -> toVariables(entity, truncateValues))
                .orElseGet(Collections::emptyList))
        .build();
  }

  private static List<VariableSearchResult> toVariables(
      final List<VariableEntity> variableEntities, final boolean truncateValues) {
    return variableEntities.stream().map(entity -> toVariable(entity, truncateValues)).toList();
  }

  private static VariableSearchResult toVariable(
      final VariableEntity variableEntity, final boolean truncateValues) {
    return VariableSearchResult.Builder.builder()
        .name(variableEntity.name())
        .processInstanceKey(keyToString(variableEntity.processInstanceKey()))
        .tenantId(variableEntity.tenantId())
        .variableKey(keyToString(variableEntity.variableKey()))
        .scopeKey(keyToString(variableEntity.scopeKey()))
        .rootProcessInstanceKey(keyToStringOrNull(variableEntity.rootProcessInstanceKey()))
        .isTruncated(truncateValues && variableEntity.isPreview())
        .value(!truncateValues ? getFullValueIfPresent(variableEntity) : variableEntity.value())
        .build();
  }

  public static VariableResult toVariableItem(final VariableEntity variableEntity) {
    return VariableResult.Builder.builder()
        .name(variableEntity.name())
        .processInstanceKey(keyToString(variableEntity.processInstanceKey()))
        .tenantId(variableEntity.tenantId())
        .variableKey(keyToString(variableEntity.variableKey()))
        .scopeKey(keyToString(variableEntity.scopeKey()))
        .rootProcessInstanceKey(keyToStringOrNull(variableEntity.rootProcessInstanceKey()))
        .value(getFullValueIfPresent(variableEntity))
        .build();
  }

  private static String getFullValueIfPresent(final VariableEntity variableEntity) {
    return variableEntity.isPreview()
        ? requireNonNull(variableEntity.fullValue(), "fullValue")
        : variableEntity.value();
  }

  public static ClusterVariableSearchQueryResult toClusterVariableSearchQueryResponse(
      final SearchQueryResult<ClusterVariableEntity> result, final boolean truncateValues) {
    final var page = toSearchQueryPageResponse(result);
    return ClusterVariableSearchQueryResult.Builder.builder()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(
                    clusterVariableEntities ->
                        toClusterVariablesSearchResult(clusterVariableEntities, truncateValues))
                .orElseGet(Collections::emptyList))
        .build();
  }

  private static List<ClusterVariableSearchResult> toClusterVariablesSearchResult(
      final List<ClusterVariableEntity> clusterVariableEntities, final boolean truncateValues) {
    return clusterVariableEntities.stream()
        .map(
            clusterVariableEntity ->
                toClusterVariableSearchResult(clusterVariableEntity, truncateValues))
        .toList();
  }

  public static ClusterVariableSearchResult toClusterVariableSearchResult(
      final ClusterVariableEntity clusterVariableEntity, final boolean truncateValues) {
    final ClusterVariableScopeEnum scope =
        switch (clusterVariableEntity.scope()) {
          case GLOBAL -> ClusterVariableScopeEnum.GLOBAL;
          case TENANT -> ClusterVariableScopeEnum.TENANT;
        };
    final String tenantId =
        clusterVariableEntity.scope() == io.camunda.search.entities.ClusterVariableScope.TENANT
            ? clusterVariableEntity.tenantId()
            : null;
    return ClusterVariableSearchResult.Builder.builder()
        .name(clusterVariableEntity.name())
        .scope(scope)
        .tenantId(tenantId)
        .value(
            requireNonNull(
                !truncateValues
                    ? getFullValueIfPresent(clusterVariableEntity)
                    : clusterVariableEntity.value(),
                "value"))
        .isTruncated(
            truncateValues && requireNonNull(clusterVariableEntity.isPreview(), "isPreview"))
        .build();
  }

  public static ClusterVariableResult toClusterVariableResult(
      final ClusterVariableEntity clusterVariableEntity) {
    final ClusterVariableScopeEnum scope =
        switch (clusterVariableEntity.scope()) {
          case GLOBAL -> ClusterVariableScopeEnum.GLOBAL;
          case TENANT -> ClusterVariableScopeEnum.TENANT;
        };
    final String tenantId =
        clusterVariableEntity.scope() == io.camunda.search.entities.ClusterVariableScope.TENANT
            ? clusterVariableEntity.tenantId()
            : null;
    return ClusterVariableResult.Builder.builder()
        .name(clusterVariableEntity.name())
        .scope(scope)
        .tenantId(tenantId)
        .value(requireNonNull(getFullValueIfPresent(clusterVariableEntity), "value"))
        .build();
  }

  private static @Nullable String getFullValueIfPresent(
      final ClusterVariableEntity clusterVariableEntity) {
    return Boolean.TRUE.equals(clusterVariableEntity.isPreview())
        ? requireNonNull(clusterVariableEntity.fullValue(), "fullValue")
        : clusterVariableEntity.value();
  }

  public static AuthorizationSearchResult toAuthorizationSearchQueryResponse(
      final SearchQueryResult<AuthorizationEntity> result) {
    return AuthorizationSearchResult.Builder.builder()
        .page(toSearchQueryPageResponse(result))
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toAuthorizations)
                .orElseGet(Collections::emptyList))
        .build();
  }

  public static List<AuthorizationResult> toAuthorizations(
      final List<AuthorizationEntity> authorizations) {
    return authorizations.stream().map(SearchQueryResponseMapper::toAuthorization).toList();
  }

  public static AuthorizationResult toAuthorization(final AuthorizationEntity authorization) {
    return AuthorizationResult.Builder.builder()
        .ownerId(authorization.ownerId())
        .ownerType(OwnerTypeEnum.fromValue(authorization.ownerType()))
        .resourceType(ResourceTypeEnum.valueOf(authorization.resourceType()))
        .resourceId(defaultIfEmpty(authorization.resourceId(), null))
        .resourcePropertyName(defaultIfEmpty(authorization.resourcePropertyName(), null))
        .permissionTypes(
            authorization.permissionTypes().stream()
                .map(PermissionType::name)
                .map(PermissionTypeEnum::fromValue)
                .toList())
        .authorizationKey(
            requireNonNull(keyToStringOrNull(authorization.authorizationKey()), "authorizationKey"))
        .build();
  }

  public static AuditLogSearchQueryResult toAuditLogSearchQueryResponse(
      final SearchQueryResult<AuditLogEntity> result) {
    return AuditLogSearchQueryResult.Builder.builder()
        .page(toSearchQueryPageResponse(result))
        .items(toAuditLogs(result.items()))
        .build();
  }

  private static List<AuditLogResult> toAuditLogs(final List<AuditLogEntity> auditLogs) {
    return auditLogs.stream().map(SearchQueryResponseMapper::toAuditLog).toList();
  }

  public static AuditLogResult toAuditLog(final AuditLogEntity auditLog) {
    return AuditLogResult.Builder.builder()
        .auditLogKey(auditLog.auditLogKey())
        .entityKey(auditLog.entityKey())
        .entityType(AuditLogEntityTypeEnum.fromValue(auditLog.entityType().name()))
        .operationType(AuditLogOperationTypeEnum.fromValue(auditLog.operationType().name()))
        .batchOperationKey(keyToStringOrNull(auditLog.batchOperationKey()))
        .batchOperationType(
            ofNullable(auditLog.batchOperationType())
                .map(Enum::name)
                .map(BatchOperationTypeEnum::fromValue)
                .orElse(null))
        .timestamp(formatDate(auditLog.timestamp()))
        .actorId(auditLog.actorId())
        .actorType(
            ofNullable(auditLog.actorType())
                .map(Enum::name)
                .map(AuditLogActorTypeEnum::fromValue)
                .orElse(null))
        .agentElementId(auditLog.agentElementId())
        .tenantId(auditLog.tenantId())
        .result(AuditLogResultEnum.fromValue(auditLog.result().name()))
        .category(AuditLogCategoryEnum.fromValue(auditLog.category().name()))
        .processDefinitionId(auditLog.processDefinitionId())
        .processDefinitionKey(keyToStringOrNull(auditLog.processDefinitionKey()))
        .processInstanceKey(keyToStringOrNull(auditLog.processInstanceKey()))
        .rootProcessInstanceKey(keyToStringOrNull(auditLog.rootProcessInstanceKey()))
        .elementInstanceKey(keyToStringOrNull(auditLog.elementInstanceKey()))
        .jobKey(keyToStringOrNull(auditLog.jobKey()))
        .userTaskKey(keyToStringOrNull(auditLog.userTaskKey()))
        .decisionRequirementsId(auditLog.decisionRequirementsId())
        .decisionRequirementsKey(keyToStringOrNull(auditLog.decisionRequirementsKey()))
        .decisionDefinitionId(auditLog.decisionDefinitionId())
        .decisionDefinitionKey(keyToStringOrNull(auditLog.decisionDefinitionKey()))
        .decisionEvaluationKey(keyToStringOrNull(auditLog.decisionEvaluationKey()))
        .deploymentKey(keyToStringOrNull(auditLog.deploymentKey()))
        .formKey(keyToStringOrNull(auditLog.formKey()))
        .resourceKey(keyToStringOrNull(auditLog.resourceKey()))
        .relatedEntityKey(auditLog.relatedEntityKey())
        .relatedEntityType(
            ofNullable(auditLog.relatedEntityType())
                .map(Enum::name)
                .map(AuditLogEntityTypeEnum::fromValue)
                .orElse(null))
        .entityDescription(auditLog.entityDescription())
        .build();
  }

  private static ProcessInstanceStateEnum toProtocolState(
      final ProcessInstanceEntity.ProcessInstanceState value) {
    if (value == ProcessInstanceEntity.ProcessInstanceState.CANCELED) {
      return ProcessInstanceStateEnum.TERMINATED;
    }
    return ProcessInstanceStateEnum.fromValue(value.name());
  }

  public static List<ProcessInstanceCallHierarchyEntry> toProcessInstanceCallHierarchyEntries(
      final List<ProcessInstanceEntity> processInstanceEntities) {
    return processInstanceEntities.stream()
        .map(SearchQueryResponseMapper::toProcessInstanceCallHierarchyEntry)
        .toList();
  }

  public static ProcessInstanceCallHierarchyEntry toProcessInstanceCallHierarchyEntry(
      final ProcessInstanceEntity processInstanceEntity) {
    return ProcessInstanceCallHierarchyEntry.Builder.builder()
        .processInstanceKey(
            requireNonNull(
                keyToStringOrNull(processInstanceEntity.processInstanceKey()),
                "processInstanceKey"))
        .processDefinitionKey(
            requireNonNull(
                keyToStringOrNull(processInstanceEntity.processDefinitionKey()),
                "processDefinitionKey"))
        .processDefinitionName(
            requireNonNull(
                StringUtils.isBlank(processInstanceEntity.processDefinitionName())
                    ? processInstanceEntity.processDefinitionId()
                    : processInstanceEntity.processDefinitionName(),
                "processDefinitionName"))
        .build();
  }

  private static List<ProcessDefinitionMessageSubscriptionStatisticsResult>
      toProcessDefinitionMessageSubscriptionStatisticsQueryResponse(
          final List<ProcessDefinitionMessageSubscriptionStatisticsEntity> entities) {
    return entities.stream()
        .map(
            e ->
                ProcessDefinitionMessageSubscriptionStatisticsResult.Builder.builder()
                    .activeSubscriptions(e.activeSubscriptions())
                    .processDefinitionId(e.processDefinitionId())
                    .processDefinitionKey(keyToString(e.processDefinitionKey()))
                    .processInstancesWithActiveSubscriptions(
                        e.processInstancesWithActiveSubscriptions())
                    .tenantId(e.tenantId())
                    .build())
        .toList();
  }

  public static ProcessDefinitionMessageSubscriptionStatisticsQueryResult
      toProcessDefinitionMessageSubscriptionStatisticsQueryResponse(
          final SearchQueryResult<ProcessDefinitionMessageSubscriptionStatisticsEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return ProcessDefinitionMessageSubscriptionStatisticsQueryResult.Builder.builder()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(
                    SearchQueryResponseMapper
                        ::toProcessDefinitionMessageSubscriptionStatisticsQueryResponse)
                .orElseGet(Collections::emptyList))
        .build();
  }

  public static GlobalJobStatisticsQueryResult toGlobalJobStatisticsQueryResult(
      final GlobalJobStatisticsEntity entity) {
    if (entity == null) {
      final StatusMetric zero =
          StatusMetric.Builder.builder().count(0L).lastUpdatedAt(null).build();
      return GlobalJobStatisticsQueryResult.Builder.builder()
          .created(zero)
          .completed(zero)
          .failed(zero)
          .isIncomplete(false)
          .build();
    }

    return GlobalJobStatisticsQueryResult.Builder.builder()
        .created(toStatusMetric(entity.created()))
        .completed(toStatusMetric(entity.completed()))
        .failed(toStatusMetric(entity.failed()))
        .isIncomplete(entity.isIncomplete())
        .build();
  }

  public static JobTypeStatisticsQueryResult toJobTypeStatisticsQueryResult(
      final SearchQueryResult<JobTypeStatisticsEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return JobTypeStatisticsQueryResult.Builder.builder()
        .page(page)
        .items(
            result.items().stream()
                .map(SearchQueryResponseMapper::toJobTypeStatisticsItem)
                .toList())
        .build();
  }

  private static JobTypeStatisticsItem toJobTypeStatisticsItem(
      final JobTypeStatisticsEntity entity) {
    final StatusMetric zero = StatusMetric.Builder.builder().count(0L).lastUpdatedAt(null).build();
    if (entity == null) {
      return JobTypeStatisticsItem.Builder.builder()
          .jobType("")
          .created(zero)
          .completed(zero)
          .failed(zero)
          .workers(0)
          .build();
    }

    return JobTypeStatisticsItem.Builder.builder()
        .jobType(entity.jobType())
        .created(toStatusMetric(entity.created()))
        .completed(toStatusMetric(entity.completed()))
        .failed(toStatusMetric(entity.failed()))
        .workers(entity.workers())
        .build();
  }

  public static JobWorkerStatisticsQueryResult toJobWorkerStatisticsQueryResult(
      final SearchQueryResult<JobWorkerStatisticsEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return JobWorkerStatisticsQueryResult.Builder.builder()
        .page(page)
        .items(
            result.items().stream()
                .map(SearchQueryResponseMapper::toJobWorkerStatisticsItem)
                .toList())
        .build();
  }

  private static JobWorkerStatisticsItem toJobWorkerStatisticsItem(
      final JobWorkerStatisticsEntity entity) {
    final StatusMetric zero = StatusMetric.Builder.builder().count(0L).lastUpdatedAt(null).build();
    if (entity == null) {
      return JobWorkerStatisticsItem.Builder.builder()
          .worker("")
          .created(zero)
          .completed(zero)
          .failed(zero)
          .build();
    }

    return JobWorkerStatisticsItem.Builder.builder()
        .worker(entity.worker())
        .created(toStatusMetric(entity.created()))
        .completed(toStatusMetric(entity.completed()))
        .failed(toStatusMetric(entity.failed()))
        .build();
  }

  public static JobTimeSeriesStatisticsQueryResult toJobTimeSeriesStatisticsQueryResult(
      final SearchQueryResult<JobTimeSeriesStatisticsEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return JobTimeSeriesStatisticsQueryResult.Builder.builder()
        .page(page)
        .items(
            result.items().stream()
                .map(SearchQueryResponseMapper::toJobTimeSeriesStatisticsItem)
                .toList())
        .build();
  }

  private static JobTimeSeriesStatisticsItem toJobTimeSeriesStatisticsItem(
      final JobTimeSeriesStatisticsEntity entity) {
    final StatusMetric zero = StatusMetric.Builder.builder().count(0L).lastUpdatedAt(null).build();
    if (entity == null) {
      return JobTimeSeriesStatisticsItem.Builder.builder()
          .time(EPOCH_DATE_SENTINEL)
          .created(zero)
          .completed(zero)
          .failed(zero)
          .build();
    }

    return JobTimeSeriesStatisticsItem.Builder.builder()
        .time(formatDate(entity.time()))
        .created(toStatusMetric(entity.created()))
        .completed(toStatusMetric(entity.completed()))
        .failed(toStatusMetric(entity.failed()))
        .build();
  }

  public static JobErrorStatisticsQueryResult toJobErrorStatisticsQueryResult(
      final SearchQueryResult<JobErrorStatisticsEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return JobErrorStatisticsQueryResult.Builder.builder()
        .page(page)
        .items(
            result.items().stream()
                .map(SearchQueryResponseMapper::toJobErrorStatisticsItem)
                .toList())
        .build();
  }

  private static JobErrorStatisticsItem toJobErrorStatisticsItem(
      final JobErrorStatisticsEntity entity) {
    if (entity == null) {
      return JobErrorStatisticsItem.Builder.builder()
          .errorCode("")
          .errorMessage("")
          .workers(0)
          .build();
    }

    return JobErrorStatisticsItem.Builder.builder()
        .errorCode(ofNullable(entity.errorCode()).orElse(""))
        .errorMessage(ofNullable(entity.errorMessage()).orElse(""))
        .workers(entity.workers())
        .build();
  }

  private static StatusMetric toStatusMetric(final GlobalJobStatisticsEntity.StatusMetric metric) {
    if (metric == null) {
      return StatusMetric.Builder.builder().count(0L).lastUpdatedAt(null).build();
    }
    return StatusMetric.Builder.builder()
        .count(metric.count())
        .lastUpdatedAt(formatDateOrNull(metric.lastUpdatedAt()))
        .build();
  }

  public static GlobalTaskListenerSearchQueryResult toGlobalTaskListenerSearchQueryResponse(
      final SearchQueryResult<GlobalListenerEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return GlobalTaskListenerSearchQueryResult.Builder.builder()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(
                    entities ->
                        entities.stream()
                            .map(SearchQueryResponseMapper::toGlobalTaskListenerResult)
                            .toList())
                .orElseGet(Collections::emptyList))
        .build();
  }

  public static GlobalTaskListenerResult toGlobalTaskListenerResult(
      final GlobalListenerEntity entity) {
    return GlobalTaskListenerResult.Builder.builder()
        .id(entity.listenerId())
        .type(entity.type())
        .retries(entity.retries())
        .eventTypes(
            entity.eventTypes().stream().map(GlobalTaskListenerEventTypeEnum::fromValue).toList())
        .afterNonGlobal(entity.afterNonGlobal())
        .priority(entity.priority())
        .source(GlobalListenerSourceEnum.fromValue(entity.source().name()))
        .build();
  }

  private static @Nullable String emptyToNull(final @Nullable String value) {
    return value == null || value.isEmpty() ? null : value;
  }

  public static ResourceSearchQueryResult toResourceSearchQueryResponse(
      final SearchQueryResult<DeployedResourceEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return new ResourceSearchQueryResult()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toResources)
                .orElseGet(Collections::emptyList));
  }

  private static List<ResourceResult> toResources(final List<DeployedResourceEntity> resources) {
    return resources.stream().map(SearchQueryResponseMapper::toResource).toList();
  }

  public static ResourceResult toResource(final DeployedResourceEntity entity) {
    return new ResourceResult()
        .resourceKey(KeyUtil.keyToString(entity.resourceKey()))
        .resourceName(entity.resourceName())
        .resourceId(entity.resourceId())
        .version(entity.version())
        .versionTag(entity.versionTag())
        .tenantId(entity.tenantId());
  }

  private record RuleIdentifier(String ruleId, int ruleIndex) {}
}
