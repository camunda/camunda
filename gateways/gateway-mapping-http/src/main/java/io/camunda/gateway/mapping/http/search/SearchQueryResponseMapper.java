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
import io.camunda.gateway.protocol.model.JobKindEnum;
import io.camunda.gateway.protocol.model.JobListenerEventTypeEnum;
import io.camunda.gateway.protocol.model.JobSearchQueryResult;
import io.camunda.gateway.protocol.model.JobSearchResult;
import io.camunda.gateway.protocol.model.JobStateEnum;
import io.camunda.gateway.protocol.model.MappingRuleResult;
import io.camunda.gateway.protocol.model.MappingRuleSearchQueryResult;
import io.camunda.gateway.protocol.model.MatchedDecisionRuleItem;
import io.camunda.gateway.protocol.model.MessageSubscriptionResult;
import io.camunda.gateway.protocol.model.MessageSubscriptionSearchQueryResult;
import io.camunda.gateway.protocol.model.MessageSubscriptionStateEnum;
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
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.FormEntity;
import io.camunda.search.entities.GlobalJobStatisticsEntity;
import io.camunda.search.entities.GroupEntity;
import io.camunda.search.entities.GroupMemberEntity;
import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.entities.IncidentProcessInstanceStatisticsByDefinitionEntity;
import io.camunda.search.entities.IncidentProcessInstanceStatisticsByErrorEntity;
import io.camunda.search.entities.JobEntity;
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
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
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

  public static ProcessDefinitionSearchQueryResult toProcessDefinitionSearchQueryResponse(
      final SearchQueryResult<ProcessDefinitionEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return new ProcessDefinitionSearchQueryResult()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toProcessDefinitions)
                .orElseGet(Collections::emptyList));
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
        .processDefinitionKey(KeyUtil.keyToString(result.processDefinitionKey()))
        .processDefinitionId(result.processDefinitionId())
        .elementId(result.flowNodeId())
        .tenantId(result.tenantId());
  }

  public static ProcessInstanceSearchQueryResult toProcessInstanceSearchQueryResponse(
      final SearchQueryResult<ProcessInstanceEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return new ProcessInstanceSearchQueryResult()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toProcessInstances)
                .orElseGet(Collections::emptyList));
  }

  public static JobSearchQueryResult toJobSearchQueryResponse(
      final SearchQueryResult<JobEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return new JobSearchQueryResult()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toJobs)
                .orElseGet(Collections::emptyList));
  }

  public static RoleSearchQueryResult toRoleSearchQueryResponse(
      final SearchQueryResult<RoleEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return new RoleSearchQueryResult()
        .page(page)
        .items(
            ofNullable(result.items()).map(SearchQueryResponseMapper::toRoles).orElseGet(List::of));
  }

  public static RoleGroupSearchResult toRoleGroupSearchQueryResponse(
      final SearchQueryResult<RoleMemberEntity> result) {
    return new RoleGroupSearchResult()
        .page(toSearchQueryPageResponse(result))
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toRoleGroups)
                .orElseGet(List::of));
  }

  public static RoleUserSearchResult toRoleUserSearchQueryResponse(
      final SearchQueryResult<RoleMemberEntity> result) {
    return new RoleUserSearchResult()
        .page(toSearchQueryPageResponse(result))
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toRoleUsers)
                .orElseGet(List::of));
  }

  public static RoleClientSearchResult toRoleClientSearchQueryResponse(
      final SearchQueryResult<RoleMemberEntity> result) {
    return new RoleClientSearchResult()
        .page(toSearchQueryPageResponse(result))
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toRoleClients)
                .orElseGet(List::of));
  }

  public static GroupSearchQueryResult toGroupSearchQueryResponse(
      final SearchQueryResult<GroupEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return new GroupSearchQueryResult()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toGroups)
                .orElseGet(List::of));
  }

  public static GroupUserSearchResult toGroupUserSearchQueryResponse(
      final SearchQueryResult<GroupMemberEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return new GroupUserSearchResult()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toGroupUsers)
                .orElseGet(List::of));
  }

  public static GroupClientSearchResult toGroupClientSearchQueryResponse(
      final SearchQueryResult<GroupMemberEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return new GroupClientSearchResult()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toGroupClients)
                .orElseGet(List::of));
  }

  public static TenantSearchQueryResult toTenantSearchQueryResponse(
      final SearchQueryResult<TenantEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return new TenantSearchQueryResult()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toTenants)
                .orElseGet(List::of));
  }

  public static TenantGroupSearchResult toTenantGroupSearchQueryResponse(
      final SearchQueryResult<TenantMemberEntity> result) {
    return new TenantGroupSearchResult()
        .page(toSearchQueryPageResponse(result))
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toTenantGroups)
                .orElseGet(List::of));
  }

  public static TenantUserSearchResult toTenantUserSearchQueryResponse(
      final SearchQueryResult<TenantMemberEntity> result) {
    return new TenantUserSearchResult()
        .page(toSearchQueryPageResponse(result))
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toTenantUsers)
                .orElseGet(List::of));
  }

  public static TenantClientSearchResult toTenantClientSearchQueryResponse(
      final SearchQueryResult<TenantMemberEntity> result) {
    return new TenantClientSearchResult()
        .page(toSearchQueryPageResponse(result))
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toTenantClients)
                .orElseGet(List::of));
  }

  public static MappingRuleSearchQueryResult toMappingRuleSearchQueryResponse(
      final SearchQueryResult<MappingRuleEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return new MappingRuleSearchQueryResult()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toMappingRules)
                .orElseGet(List::of));
  }

  public static DecisionDefinitionSearchQueryResult toDecisionDefinitionSearchQueryResponse(
      final SearchQueryResult<DecisionDefinitionEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return new DecisionDefinitionSearchQueryResult()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toDecisionDefinitions)
                .orElseGet(Collections::emptyList));
  }

  public static DecisionRequirementsSearchQueryResult toDecisionRequirementsSearchQueryResponse(
      final SearchQueryResult<DecisionRequirementsEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return new DecisionRequirementsSearchQueryResult()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toDecisionRequirements)
                .orElseGet(Collections::emptyList));
  }

  public static ElementInstanceSearchQueryResult toElementInstanceSearchQueryResponse(
      final SearchQueryResult<FlowNodeInstanceEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return new ElementInstanceSearchQueryResult()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(instances -> toElementInstance(instances))
                .orElseGet(Collections::emptyList));
  }

  public static DecisionInstanceSearchQueryResult toDecisionInstanceSearchQueryResponse(
      final SearchQueryResult<DecisionInstanceEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return new DecisionInstanceSearchQueryResult()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toDecisionInstances)
                .orElseGet(Collections::emptyList));
  }

  public static UserTaskSearchQueryResult toUserTaskSearchQueryResponse(
      final SearchQueryResult<UserTaskEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return new UserTaskSearchQueryResult()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(tasks -> toUserTasks(tasks))
                .orElseGet(Collections::emptyList));
  }

  public static UserSearchResult toUserSearchQueryResponse(
      final SearchQueryResult<UserEntity> result) {
    return new UserSearchResult()
        .page(toSearchQueryPageResponse(result))
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toUsers)
                .orElseGet(Collections::emptyList));
  }

  public static BatchOperationSearchQueryResult toBatchOperationSearchQueryResult(
      final SearchQueryResult<BatchOperationEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return new BatchOperationSearchQueryResult()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toBatchOperations)
                .orElseGet(Collections::emptyList));
  }

  public static BatchOperationItemSearchQueryResult toBatchOperationItemSearchQueryResult(
      final SearchQueryResult<BatchOperationItemEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return new BatchOperationItemSearchQueryResult()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toBatchOperationItems)
                .orElseGet(Collections::emptyList));
  }

  public static IncidentSearchQueryResult toIncidentSearchQueryResponse(
      final SearchQueryResult<IncidentEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return new IncidentSearchQueryResult()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toIncidents)
                .orElseGet(Collections::emptyList));
  }

  public static MessageSubscriptionSearchQueryResult toMessageSubscriptionSearchQueryResponse(
      final SearchQueryResult<MessageSubscriptionEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return new MessageSubscriptionSearchQueryResult()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toMessageSubscriptions)
                .orElseGet(Collections::emptyList));
  }

  public static CorrelatedMessageSubscriptionSearchQueryResult
      toCorrelatedMessageSubscriptionSearchQueryResponse(
          final SearchQueryResult<CorrelatedMessageSubscriptionEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return new CorrelatedMessageSubscriptionSearchQueryResult()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toCorrelatedMessageSubscriptions)
                .orElseGet(Collections::emptyList));
  }

  private static SearchQueryPageResponse toSearchQueryPageResponse(
      final SearchQueryResult<?> result) {

    return new SearchQueryPageResponse()
        .totalItems(result.total())
        .hasMoreTotalItems(result.hasMoreTotalItems())
        .startCursor(result.startCursor())
        .endCursor(result.endCursor());
  }

  private static List<ProcessDefinitionResult> toProcessDefinitions(
      final List<ProcessDefinitionEntity> processDefinitions) {
    return processDefinitions.stream().map(SearchQueryResponseMapper::toProcessDefinition).toList();
  }

  public static ProcessDefinitionResult toProcessDefinition(final ProcessDefinitionEntity entity) {
    return new ProcessDefinitionResult()
        .processDefinitionKey(KeyUtil.keyToString(entity.processDefinitionKey()))
        .name(entity.name())
        .resourceName(entity.resourceName())
        .version(entity.version())
        .versionTag(entity.versionTag())
        .processDefinitionId(entity.processDefinitionId())
        .tenantId(entity.tenantId())
        .hasStartForm(StringUtils.isNotBlank(entity.formId()));
  }

  private static List<ProcessInstanceResult> toProcessInstances(
      final List<ProcessInstanceEntity> instances) {
    return instances.stream().map(SearchQueryResponseMapper::toProcessInstance).toList();
  }

  private static List<JobSearchResult> toJobs(final List<JobEntity> jobs) {
    return jobs.stream().map(SearchQueryResponseMapper::toJob).toList();
  }

  private static JobSearchResult toJob(final JobEntity job) {
    return new JobSearchResult()
        .jobKey(KeyUtil.keyToString(job.jobKey()))
        .type(job.type())
        .worker(job.worker())
        .state(JobStateEnum.fromValue(job.state().name()))
        .kind(JobKindEnum.fromValue(job.kind().name()))
        .listenerEventType(JobListenerEventTypeEnum.fromValue(job.listenerEventType().name()))
        .retries(job.retries())
        .isDenied(job.isDenied())
        .deniedReason(job.deniedReason())
        .hasFailedWithRetriesLeft(job.hasFailedWithRetriesLeft())
        .errorCode(job.errorCode())
        .errorMessage(job.errorMessage())
        .customHeaders(job.customHeaders())
        .deadline(formatDate(job.deadline()))
        .endTime(formatDate(job.endTime()))
        .processDefinitionId(job.processDefinitionId())
        .processDefinitionKey(KeyUtil.keyToString(job.processDefinitionKey()))
        .processInstanceKey(KeyUtil.keyToString(job.processInstanceKey()))
        .elementId(job.elementId())
        .elementInstanceKey(KeyUtil.keyToString(job.elementInstanceKey()))
        .tenantId(job.tenantId())
        .creationTime(formatDate(job.creationTime()))
        .lastUpdateTime(formatDate(job.lastUpdateTime()));
  }

  public static ProcessInstanceResult toProcessInstance(final ProcessInstanceEntity p) {
    return new ProcessInstanceResult()
        .processInstanceKey(KeyUtil.keyToString(p.processInstanceKey()))
        .processDefinitionId(p.processDefinitionId())
        .processDefinitionName(p.processDefinitionName())
        .processDefinitionVersion(p.processDefinitionVersion())
        .processDefinitionVersionTag(p.processDefinitionVersionTag())
        .processDefinitionKey(KeyUtil.keyToString(p.processDefinitionKey()))
        .parentProcessInstanceKey(KeyUtil.keyToString(p.parentProcessInstanceKey()))
        .parentElementInstanceKey(KeyUtil.keyToString(p.parentFlowNodeInstanceKey()))
        .startDate(formatDate(p.startDate()))
        .endDate(formatDate(p.endDate()))
        .state(toProtocolState(p.state()))
        .hasIncident(p.hasIncident())
        .tenantId(p.tenantId())
        .tags(p.tags());
  }

  public static List<BatchOperationResponse> toBatchOperations(
      final List<BatchOperationEntity> batchOperations) {
    return batchOperations.stream().map(SearchQueryResponseMapper::toBatchOperation).toList();
  }

  public static BatchOperationResponse toBatchOperation(final BatchOperationEntity entity) {
    return new BatchOperationResponse()
        .batchOperationKey(entity.batchOperationKey())
        .state(BatchOperationStateEnum.fromValue(entity.state().name()))
        .batchOperationType(BatchOperationTypeEnum.fromValue(entity.operationType().name()))
        .startDate(formatDate(entity.startDate()))
        .endDate(formatDate(entity.endDate()))
        .actorType(
            ofNullable(entity.actorType())
                .map(Enum::name)
                .map(AuditLogActorTypeEnum::fromValue)
                .orElse(null))
        .actorId(entity.actorId())
        .operationsTotalCount(entity.operationsTotalCount())
        .operationsFailedCount(entity.operationsFailedCount())
        .operationsCompletedCount(entity.operationsCompletedCount())
        .errors(
            ofNullable(entity.errors())
                .map(
                    errors ->
                        errors.stream()
                            .map(SearchQueryResponseMapper::toBatchOperationError)
                            .toList())
                .orElseGet(Collections::emptyList));
  }

  public static BatchOperationItemSearchQueryResult toBatchOperationItemSearchQueryResult(
      final List<BatchOperationItemEntity> batchOperations) {
    return new BatchOperationItemSearchQueryResult()
        .items(
            batchOperations.stream().map(SearchQueryResponseMapper::toBatchOperationItem).toList());
  }

  public static List<BatchOperationItemResponse> toBatchOperationItems(
      final List<BatchOperationItemEntity> batchOperationItems) {
    return batchOperationItems.stream()
        .map(SearchQueryResponseMapper::toBatchOperationItem)
        .toList();
  }

  public static BatchOperationItemResponse toBatchOperationItem(
      final BatchOperationItemEntity entity) {
    return new BatchOperationItemResponse()
        .batchOperationKey(entity.batchOperationKey())
        .operationType(
            entity.operationType() != null
                ? BatchOperationTypeEnum.fromValue(entity.operationType().name())
                : null)
        .itemKey(
            warnIfNull(entity, BatchOperationItemEntity::itemKey, "itemKey")
                .map(Object::toString)
                .orElse(null))
        .processInstanceKey(
            warnIfNull(entity, BatchOperationItemEntity::processInstanceKey, "processInstanceKey")
                .map(Object::toString)
                .orElse(null))
        .processedDate(formatDate(entity.processedDate()))
        .errorMessage(entity.errorMessage())
        .state(
            warnIfNull(entity, BatchOperationItemEntity::state, "state")
                .map(Enum::name)
                .map(BatchOperationItemResponse.StateEnum::fromValue)
                .orElse(null));
  }

  private static BatchOperationError toBatchOperationError(
      final BatchOperationErrorEntity batchOperationErrorEntity) {
    return new BatchOperationError()
        .partitionId(batchOperationErrorEntity.partitionId())
        .type(TypeEnum.fromValue(batchOperationErrorEntity.type()))
        .message(batchOperationErrorEntity.message());
  }

  private static List<RoleResult> toRoles(final List<RoleEntity> roles) {
    return roles.stream().map(SearchQueryResponseMapper::toRole).toList();
  }

  public static RoleResult toRole(final RoleEntity roleEntity) {
    return new RoleResult()
        .roleId(roleEntity.roleId())
        .description(roleEntity.description())
        .name(roleEntity.name());
  }

  private static List<GroupResult> toGroups(final List<GroupEntity> groups) {
    return groups.stream().map(SearchQueryResponseMapper::toGroup).toList();
  }

  public static GroupResult toGroup(final GroupEntity groupEntity) {
    return new GroupResult()
        .groupId(groupEntity.groupId())
        .name(groupEntity.name())
        .description(groupEntity.description());
  }

  private static List<GroupUserResult> toGroupUsers(final List<GroupMemberEntity> groupMembers) {
    return groupMembers.stream().map(SearchQueryResponseMapper::toGroupUser).toList();
  }

  private static GroupUserResult toGroupUser(final GroupMemberEntity groupMember) {
    return new GroupUserResult().username(groupMember.id());
  }

  private static List<GroupClientResult> toGroupClients(
      final List<GroupMemberEntity> groupMembers) {
    return groupMembers.stream().map(SearchQueryResponseMapper::toGroupClient).toList();
  }

  private static GroupClientResult toGroupClient(final GroupMemberEntity groupMember) {
    return new GroupClientResult().clientId(groupMember.id());
  }

  private static List<TenantResult> toTenants(final List<TenantEntity> tenants) {
    return tenants.stream().map(SearchQueryResponseMapper::toTenant).toList();
  }

  public static TenantResult toTenant(final TenantEntity tenantEntity) {
    return new TenantResult()
        .name(tenantEntity.name())
        .description(tenantEntity.description())
        .tenantId(tenantEntity.tenantId());
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
    return new TenantGroupResult().groupId(tenantMember.id());
  }

  private static TenantUserResult toTenantUser(final TenantMemberEntity tenantMember) {
    return new TenantUserResult().username(tenantMember.id());
  }

  private static TenantClientResult toTenantClient(final TenantMemberEntity tenantMember) {
    return new TenantClientResult().clientId(tenantMember.id());
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
    return new RoleGroupResult().groupId(roleMember.id());
  }

  private static RoleUserResult toRoleUser(final RoleMemberEntity roleMember) {
    return new RoleUserResult().username(roleMember.id());
  }

  private static RoleClientResult toRoleClient(final RoleMemberEntity roleMember) {
    return new RoleClientResult().clientId(roleMember.id());
  }

  private static List<MappingRuleResult> toMappingRules(
      final List<MappingRuleEntity> mappingRules) {
    return mappingRules.stream().map(SearchQueryResponseMapper::toMappingRule).toList();
  }

  public static MappingRuleResult toMappingRule(final MappingRuleEntity mappingRuleEntity) {
    return new MappingRuleResult()
        .claimName(mappingRuleEntity.claimName())
        .claimValue(mappingRuleEntity.claimValue())
        .mappingRuleId(mappingRuleEntity.mappingRuleId())
        .name(mappingRuleEntity.name());
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
    return new ElementInstanceResult()
        .elementInstanceKey(KeyUtil.keyToString(instance.flowNodeInstanceKey()))
        .elementId(instance.flowNodeId())
        .elementName(instance.flowNodeName())
        .processDefinitionKey(KeyUtil.keyToString(instance.processDefinitionKey()))
        .processDefinitionId(instance.processDefinitionId())
        .processInstanceKey(KeyUtil.keyToString(instance.processInstanceKey()))
        .incidentKey(KeyUtil.keyToString(instance.incidentKey()))
        .hasIncident(instance.hasIncident())
        .startDate(formatDate(instance.startDate()))
        .endDate(formatDate(instance.endDate()))
        .state(ElementInstanceStateEnum.fromValue(instance.state().name()))
        .type(ElementInstanceResult.TypeEnum.fromValue(instance.type().name()))
        .tenantId(instance.tenantId());
  }

  public static DecisionDefinitionResult toDecisionDefinition(final DecisionDefinitionEntity d) {
    return new DecisionDefinitionResult()
        .tenantId(d.tenantId())
        .decisionDefinitionKey(KeyUtil.keyToString(d.decisionDefinitionKey()))
        .name(d.name())
        .version(d.version())
        .decisionDefinitionId(d.decisionDefinitionId())
        .decisionRequirementsKey(KeyUtil.keyToString(d.decisionRequirementsKey()))
        .decisionRequirementsId(d.decisionRequirementsId())
        .decisionRequirementsName(d.decisionRequirementsName())
        .decisionRequirementsVersion(d.decisionRequirementsVersion());
  }

  public static DecisionRequirementsResult toDecisionRequirements(
      final DecisionRequirementsEntity d) {
    return new DecisionRequirementsResult()
        .tenantId(d.tenantId())
        .decisionRequirementsKey(KeyUtil.keyToString(d.decisionRequirementsKey()))
        .decisionRequirementsName(d.name())
        .version(d.version())
        .resourceName(d.resourceName())
        .decisionRequirementsId(d.decisionRequirementsId());
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
    return new IncidentResult()
        .incidentKey(KeyUtil.keyToString(t.incidentKey()))
        .processDefinitionKey(KeyUtil.keyToString(t.processDefinitionKey()))
        .processDefinitionId(t.processDefinitionId())
        .processInstanceKey(KeyUtil.keyToString(t.processInstanceKey()))
        .errorType(IncidentErrorTypeEnum.fromValue(t.errorType().name()))
        .errorMessage(t.errorMessage())
        .elementId(t.flowNodeId())
        .elementInstanceKey(KeyUtil.keyToString(t.flowNodeInstanceKey()))
        .creationTime(formatDate(t.creationTime()))
        .state(IncidentStateEnum.fromValue(t.state().name()))
        .jobKey(KeyUtil.keyToString(t.jobKey()))
        .tenantId(t.tenantId());
  }

  private static List<MessageSubscriptionResult> toMessageSubscriptions(
      final List<MessageSubscriptionEntity> messageSubscriptions) {
    return messageSubscriptions.stream()
        .map(SearchQueryResponseMapper::toMessageSubscription)
        .toList();
  }

  private static MessageSubscriptionResult toMessageSubscription(
      final MessageSubscriptionEntity messageSubscription) {
    return new MessageSubscriptionResult()
        .messageSubscriptionKey(KeyUtil.keyToString(messageSubscription.messageSubscriptionKey()))
        .processDefinitionId(messageSubscription.processDefinitionId())
        .processDefinitionKey(KeyUtil.keyToString(messageSubscription.processDefinitionKey()))
        .processInstanceKey(KeyUtil.keyToString(messageSubscription.processInstanceKey()))
        .elementId(messageSubscription.flowNodeId())
        .elementInstanceKey(KeyUtil.keyToString(messageSubscription.flowNodeInstanceKey()))
        .messageSubscriptionState(
            MessageSubscriptionStateEnum.fromValue(
                messageSubscription.messageSubscriptionState().name()))
        .lastUpdatedDate(formatDate(messageSubscription.dateTime()))
        .messageName(messageSubscription.messageName())
        .correlationKey(messageSubscription.correlationKey())
        .tenantId(messageSubscription.tenantId());
  }

  private static List<CorrelatedMessageSubscriptionResult> toCorrelatedMessageSubscriptions(
      final List<CorrelatedMessageSubscriptionEntity> correlatedMessageSubscriptions) {
    return correlatedMessageSubscriptions.stream()
        .map(SearchQueryResponseMapper::toCorrelatedMessageSubscription)
        .toList();
  }

  private static CorrelatedMessageSubscriptionResult toCorrelatedMessageSubscription(
      final CorrelatedMessageSubscriptionEntity correlatedMessageSubscription) {
    return new CorrelatedMessageSubscriptionResult()
        .correlationKey(correlatedMessageSubscription.correlationKey())
        .correlationTime(formatDate(correlatedMessageSubscription.correlationTime()))
        .elementId(correlatedMessageSubscription.flowNodeId())
        .elementInstanceKey(
            KeyUtil.keyToString(correlatedMessageSubscription.flowNodeInstanceKey()))
        .messageKey(KeyUtil.keyToString(correlatedMessageSubscription.messageKey()))
        .messageName(correlatedMessageSubscription.messageName())
        .partitionId(correlatedMessageSubscription.partitionId())
        .processDefinitionId(correlatedMessageSubscription.processDefinitionId())
        .processDefinitionKey(
            KeyUtil.keyToString(correlatedMessageSubscription.processDefinitionKey()))
        .processInstanceKey(KeyUtil.keyToString(correlatedMessageSubscription.processInstanceKey()))
        .subscriptionKey(KeyUtil.keyToString(correlatedMessageSubscription.subscriptionKey()))
        .tenantId(correlatedMessageSubscription.tenantId());
  }

  public static UserTaskResult toUserTask(final UserTaskEntity t) {
    return new UserTaskResult()
        .tenantId(t.tenantId())
        .userTaskKey(KeyUtil.keyToString(t.userTaskKey()))
        .name(t.name())
        .processInstanceKey(KeyUtil.keyToString(t.processInstanceKey()))
        .processDefinitionKey(KeyUtil.keyToString(t.processDefinitionKey()))
        .elementInstanceKey(KeyUtil.keyToString(t.elementInstanceKey()))
        .processDefinitionId(t.processDefinitionId())
        .processName(t.processName())
        .state(UserTaskStateEnum.fromValue(t.state().name()))
        .assignee(t.assignee())
        .candidateUsers(t.candidateUsers())
        .candidateGroups(t.candidateGroups())
        .formKey(KeyUtil.keyToString(t.formKey()))
        .elementId(t.elementId())
        .creationDate(formatDate(t.creationDate()))
        .completionDate(formatDate(t.completionDate()))
        .dueDate(formatDate(t.dueDate()))
        .followUpDate(formatDate(t.followUpDate()))
        .externalFormReference(t.externalFormReference())
        .processDefinitionVersion(t.processDefinitionVersion())
        .customHeaders(t.customHeaders())
        .priority(t.priority())
        .tags(t.tags());
  }

  public static FormResult toFormItem(final FormEntity f) {
    return new FormResult()
        .formKey(KeyUtil.keyToString(f.formKey()))
        .formId(f.formId())
        .version(f.version())
        .schema(f.schema())
        .tenantId(f.tenantId());
  }

  public static List<UserResult> toUsers(final List<UserEntity> users) {
    return users.stream().map(SearchQueryResponseMapper::toUser).toList();
  }

  public static UserResult toUser(final UserEntity user) {
    return new UserResult().username(user.username()).email(user.email()).name(user.name());
  }

  public static CamundaUserResult toCamundaUser(final CamundaUserDTO camundaUser) {
    return new CamundaUserResult()
        .displayName(camundaUser.displayName())
        .username(camundaUser.username())
        .email(camundaUser.email())
        .authorizedComponents(camundaUser.authorizedComponents())
        .tenants(toTenants(camundaUser.tenants()))
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

  private static List<DecisionInstanceResult> toDecisionInstances(
      final List<DecisionInstanceEntity> instances) {
    return instances.stream().map(SearchQueryResponseMapper::toDecisionInstance).toList();
  }

  public static DecisionInstanceResult toDecisionInstance(final DecisionInstanceEntity entity) {
    return new DecisionInstanceResult()
        .decisionEvaluationKey(KeyUtil.keyToString(entity.decisionInstanceKey()))
        .decisionEvaluationInstanceKey(entity.decisionInstanceId())
        .state(toDecisionInstanceStateEnum(entity.state()))
        .evaluationDate(formatDate(entity.evaluationDate()))
        .evaluationFailure(entity.evaluationFailure())
        .processDefinitionKey(KeyUtil.keyToString(entity.processDefinitionKey()))
        .processInstanceKey(KeyUtil.keyToString(entity.processInstanceKey()))
        .elementInstanceKey(KeyUtil.keyToString(entity.flowNodeInstanceKey()))
        .decisionDefinitionKey(KeyUtil.keyToString(entity.decisionDefinitionKey()))
        .decisionDefinitionId(entity.decisionDefinitionId())
        .decisionDefinitionName(entity.decisionDefinitionName())
        .decisionDefinitionVersion(entity.decisionDefinitionVersion())
        .decisionDefinitionType(toDecisionDefinitionTypeEnum(entity.decisionDefinitionType()))
        .rootDecisionDefinitionKey(KeyUtil.keyToString(entity.rootDecisionDefinitionKey()))
        .result(entity.result())
        .tenantId(entity.tenantId());
  }

  public static DecisionInstanceGetQueryResult toDecisionInstanceGetQueryResponse(
      final DecisionInstanceEntity entity) {
    return new DecisionInstanceGetQueryResult()
        .decisionEvaluationKey(KeyUtil.keyToString(entity.decisionInstanceKey()))
        .decisionEvaluationInstanceKey(entity.decisionInstanceId())
        .state(toDecisionInstanceStateEnum(entity.state()))
        .evaluationDate(formatDate(entity.evaluationDate()))
        .evaluationFailure(entity.evaluationFailure())
        .processDefinitionKey(KeyUtil.keyToString(entity.processDefinitionKey()))
        .processInstanceKey(KeyUtil.keyToString(entity.processInstanceKey()))
        .elementInstanceKey(KeyUtil.keyToString(entity.flowNodeInstanceKey()))
        .decisionDefinitionKey(KeyUtil.keyToString(entity.decisionDefinitionKey()))
        .decisionDefinitionId(entity.decisionDefinitionId())
        .decisionDefinitionName(entity.decisionDefinitionName())
        .decisionDefinitionVersion(entity.decisionDefinitionVersion())
        .decisionDefinitionType(toDecisionDefinitionTypeEnum(entity.decisionDefinitionType()))
        .rootDecisionDefinitionKey(KeyUtil.keyToString(entity.rootDecisionDefinitionKey()))
        .result(entity.result())
        .evaluatedInputs(toEvaluatedInputs(entity.evaluatedInputs()))
        .matchedRules(toMatchedRules(entity.evaluatedOutputs()))
        .tenantId(entity.tenantId());
  }

  private static List<EvaluatedDecisionInputItem> toEvaluatedInputs(
      final List<DecisionInstanceInputEntity> decisionInstanceInputEntities) {
    if (decisionInstanceInputEntities == null) {
      return null;
    }
    return decisionInstanceInputEntities.stream()
        .map(
            input ->
                new EvaluatedDecisionInputItem()
                    .inputId(input.inputId())
                    .inputName(input.inputName())
                    .inputValue(input.inputValue()))
        .toList();
  }

  private static List<MatchedDecisionRuleItem> toMatchedRules(
      final List<DecisionInstanceOutputEntity> decisionInstanceOutputEntities) {
    if (decisionInstanceOutputEntities == null) {
      return null;
    }
    final var outputEntitiesMappedByRule =
        decisionInstanceOutputEntities.stream()
            .collect(Collectors.groupingBy(e -> new RuleIdentifier(e.ruleId(), e.ruleIndex())));
    return outputEntitiesMappedByRule.entrySet().stream()
        .map(
            entry -> {
              final var ruleIdentifier = entry.getKey();
              final var outputs = entry.getValue();
              return new MatchedDecisionRuleItem()
                  .ruleId(ruleIdentifier.ruleId())
                  .ruleIndex(ruleIdentifier.ruleIndex())
                  .evaluatedOutputs(
                      outputs.stream()
                          .map(
                              output ->
                                  new EvaluatedDecisionOutputItem()
                                      .outputId(output.outputId())
                                      .outputName(output.outputName())
                                      .outputValue(output.outputValue()))
                          .toList());
            })
        .toList();
  }

  private static DecisionInstanceStateEnum toDecisionInstanceStateEnum(
      final DecisionInstanceState state) {
    if (state == null) {
      return null;
    }
    return switch (state) {
      case EVALUATED -> DecisionInstanceStateEnum.EVALUATED;
      case FAILED -> DecisionInstanceStateEnum.FAILED;
    };
  }

  private static DecisionDefinitionTypeEnum toDecisionDefinitionTypeEnum(
      final DecisionDefinitionType decisionDefinitionType) {
    if (decisionDefinitionType == null) {
      return null;
    }
    return switch (decisionDefinitionType) {
      case DECISION_TABLE -> DecisionDefinitionTypeEnum.DECISION_TABLE;
      case LITERAL_EXPRESSION -> DecisionDefinitionTypeEnum.LITERAL_EXPRESSION;
      default -> DecisionDefinitionTypeEnum.UNKNOWN;
    };
  }

  public static VariableSearchQueryResult toVariableSearchQueryResponse(
      final SearchQueryResult<VariableEntity> result, final boolean truncateValues) {
    final var page = toSearchQueryPageResponse(result);
    return new VariableSearchQueryResult()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(entity -> toVariables(entity, truncateValues))
                .orElseGet(Collections::emptyList));
  }

  private static List<VariableSearchResult> toVariables(
      final List<VariableEntity> variableEntities, final boolean truncateValues) {
    return variableEntities.stream().map(entity -> toVariable(entity, truncateValues)).toList();
  }

  private static VariableSearchResult toVariable(
      final VariableEntity variableEntity, final boolean truncateValues) {
    return new VariableSearchResult()
        .variableKey(KeyUtil.keyToString(variableEntity.variableKey()))
        .name(variableEntity.name())
        .value(!truncateValues ? getFullValueIfPresent(variableEntity) : variableEntity.value())
        .processInstanceKey(KeyUtil.keyToString(variableEntity.processInstanceKey()))
        .tenantId(variableEntity.tenantId())
        .isTruncated(truncateValues && variableEntity.isPreview())
        .scopeKey(KeyUtil.keyToString(variableEntity.scopeKey()));
  }

  public static VariableResult toVariableItem(final VariableEntity variableEntity) {
    return new VariableResult()
        .variableKey(KeyUtil.keyToString(variableEntity.variableKey()))
        .name(variableEntity.name())
        .value(getFullValueIfPresent(variableEntity))
        .processInstanceKey(KeyUtil.keyToString(variableEntity.processInstanceKey()))
        .tenantId(variableEntity.tenantId())
        .scopeKey(KeyUtil.keyToString(variableEntity.scopeKey()));
  }

  private static String getFullValueIfPresent(final VariableEntity variableEntity) {
    return variableEntity.isPreview() ? variableEntity.fullValue() : variableEntity.value();
  }

  public static ClusterVariableSearchQueryResult toClusterVariableSearchQueryResponse(
      final SearchQueryResult<ClusterVariableEntity> result, final boolean truncateValues) {
    final var page = toSearchQueryPageResponse(result);
    return new ClusterVariableSearchQueryResult()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(
                    clusterVariableEntities ->
                        toClusterVariablesSearchResult(clusterVariableEntities, truncateValues))
                .orElseGet(Collections::emptyList));
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
    final var clusterVariableResult =
        new ClusterVariableSearchResult()
            .name(clusterVariableEntity.name())
            .value(
                !truncateValues
                    ? getFullValueIfPresent(clusterVariableEntity)
                    : clusterVariableEntity.value())
            .isTruncated(truncateValues && clusterVariableEntity.isPreview());
    return switch (clusterVariableEntity.scope()) {
      case GLOBAL -> clusterVariableResult.scope(ClusterVariableScopeEnum.GLOBAL);
      case TENANT ->
          clusterVariableResult
              .scope(ClusterVariableScopeEnum.TENANT)
              .tenantId(clusterVariableEntity.tenantId());
    };
  }

  public static ClusterVariableResult toClusterVariableResult(
      final ClusterVariableEntity clusterVariableEntity) {

    final var clusterVariableResult =
        new ClusterVariableResult()
            .name(clusterVariableEntity.name())
            .value(getFullValueIfPresent(clusterVariableEntity));
    return switch (clusterVariableEntity.scope()) {
      case GLOBAL -> clusterVariableResult.scope(ClusterVariableScopeEnum.GLOBAL);
      case TENANT ->
          clusterVariableResult
              .scope(ClusterVariableScopeEnum.TENANT)
              .tenantId(clusterVariableEntity.tenantId());
    };
  }

  private static String getFullValueIfPresent(final ClusterVariableEntity clusterVariableEntity) {
    return clusterVariableEntity.isPreview()
        ? clusterVariableEntity.fullValue()
        : clusterVariableEntity.value();
  }

  public static AuthorizationSearchResult toAuthorizationSearchQueryResponse(
      final SearchQueryResult<AuthorizationEntity> result) {
    return new AuthorizationSearchResult()
        .page(toSearchQueryPageResponse(result))
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toAuthorizations)
                .orElseGet(Collections::emptyList));
  }

  public static List<AuthorizationResult> toAuthorizations(
      final List<AuthorizationEntity> authorizations) {
    return authorizations.stream().map(SearchQueryResponseMapper::toAuthorization).toList();
  }

  public static AuthorizationResult toAuthorization(final AuthorizationEntity authorization) {
    return new AuthorizationResult()
        .authorizationKey(KeyUtil.keyToString(authorization.authorizationKey()))
        .ownerId(authorization.ownerId())
        .ownerType(OwnerTypeEnum.fromValue(authorization.ownerType()))
        .resourceType(ResourceTypeEnum.valueOf(authorization.resourceType()))
        .resourceId(defaultIfEmpty(authorization.resourceId(), null))
        .resourcePropertyName(defaultIfEmpty(authorization.resourcePropertyName(), null))
        .permissionTypes(
            authorization.permissionTypes().stream()
                .map(PermissionType::name)
                .map(PermissionTypeEnum::fromValue)
                .toList());
  }

  public static AuditLogSearchQueryResult toAuditLogSearchQueryResponse(
      final SearchQueryResult<AuditLogEntity> result) {
    return new AuditLogSearchQueryResult()
        .items(toAuditLogs(result.items()))
        .page(toSearchQueryPageResponse(result));
  }

  private static List<AuditLogResult> toAuditLogs(final List<AuditLogEntity> auditLogs) {
    return auditLogs.stream().map(SearchQueryResponseMapper::toAuditLog).toList();
  }

  public static AuditLogResult toAuditLog(final AuditLogEntity auditLog) {
    return new AuditLogResult()
        .auditLogKey(auditLog.auditLogKey())
        .entityKey(auditLog.entityKey())
        .entityType(
            ofNullable(auditLog.entityType())
                .map(Enum::name)
                .map(AuditLogEntityTypeEnum::fromValue)
                .orElse(null))
        .operationType(
            ofNullable(auditLog.operationType())
                .map(Enum::name)
                .map(AuditLogOperationTypeEnum::fromValue)
                .orElse(null))
        .batchOperationKey(KeyUtil.keyToString(auditLog.batchOperationKey()))
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
        .tenantId(auditLog.tenantId())
        .result(
            ofNullable(auditLog.result())
                .map(Enum::name)
                .map(AuditLogResultEnum::fromValue)
                .orElse(null))
        .annotation(auditLog.annotation())
        .category(
            ofNullable(auditLog.category())
                .map(Enum::name)
                .map(AuditLogCategoryEnum::fromValue)
                .orElse(null))
        .processDefinitionId(auditLog.processDefinitionId())
        .processDefinitionKey(KeyUtil.keyToString(auditLog.processDefinitionKey()))
        .processInstanceKey(KeyUtil.keyToString(auditLog.processInstanceKey()))
        .elementInstanceKey(KeyUtil.keyToString(auditLog.elementInstanceKey()))
        .jobKey(KeyUtil.keyToString(auditLog.jobKey()))
        .userTaskKey(KeyUtil.keyToString(auditLog.userTaskKey()))
        .decisionRequirementsId(auditLog.decisionRequirementsId())
        .decisionRequirementsKey(KeyUtil.keyToString(auditLog.decisionRequirementsKey()))
        .decisionDefinitionId(auditLog.decisionDefinitionId())
        .decisionDefinitionKey(KeyUtil.keyToString(auditLog.decisionDefinitionKey()))
        .decisionEvaluationKey(KeyUtil.keyToString(auditLog.decisionEvaluationKey()))
        .deploymentKey(KeyUtil.keyToString(auditLog.deploymentKey()))
        .formKey(KeyUtil.keyToString(auditLog.formKey()))
        .resourceKey(KeyUtil.keyToString(auditLog.resourceKey()))
        .relatedEntityKey(auditLog.relatedEntityKey())
        .relatedEntityType(
            ofNullable(auditLog.relatedEntityType())
                .map(Enum::name)
                .map(AuditLogEntityTypeEnum::fromValue)
                .orElse(null))
        .entityDescription(auditLog.entityDescription());
  }

  private static ProcessInstanceStateEnum toProtocolState(
      final ProcessInstanceEntity.ProcessInstanceState value) {
    if (value == null) {
      return null;
    }
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
    return new ProcessInstanceCallHierarchyEntry()
        .processInstanceKey(KeyUtil.keyToString(processInstanceEntity.processInstanceKey()))
        .processDefinitionKey(KeyUtil.keyToString(processInstanceEntity.processDefinitionKey()))
        .processDefinitionName(
            processInstanceEntity.processDefinitionName().isBlank()
                ? processInstanceEntity.processDefinitionId()
                : processInstanceEntity.processDefinitionName());
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

  private static StatusMetric toStatusMetric(final GlobalJobStatisticsEntity.StatusMetric metric) {
    if (metric == null) {
      return new StatusMetric().count(0L);
    }
    return new StatusMetric()
        .count(metric.count())
        .lastUpdatedAt(formatDate(metric.lastUpdatedAt()));
  }

  // sometimes we've seen null for properties that should not be null; log a warning if that happens
  // so we can at least return data - rather than erroring out the whole response
  private static <T, R> Optional<R> warnIfNull(
      final T entity, final Function<T, R> mapper, final String propertyName) {
    final R value = mapper.apply(entity);
    if (value == null) {
      LOGGER.warn("{} value is null for entity: {}", propertyName, entity);
      return Optional.empty();
    }
    return Optional.of(value);
  }

  private record RuleIdentifier(String ruleId, int ruleIndex) {}
}
