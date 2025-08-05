/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest;

import static io.camunda.zeebe.gateway.rest.ResponseMapper.formatDate;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toMap;

import io.camunda.authentication.entity.CamundaUserDTO;
import io.camunda.search.entities.AuthorizationEntity;
import io.camunda.search.entities.BatchOperationEntity;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationErrorEntity;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemEntity;
import io.camunda.search.entities.DecisionDefinitionEntity;
import io.camunda.search.entities.DecisionInstanceEntity;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionDefinitionType;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionInstanceInputEntity;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionInstanceOutputEntity;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionInstanceState;
import io.camunda.search.entities.DecisionRequirementsEntity;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.FormEntity;
import io.camunda.search.entities.GroupEntity;
import io.camunda.search.entities.GroupMemberEntity;
import io.camunda.search.entities.IncidentEntity;
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
import io.camunda.search.entities.UsageMetricStatisticsEntity;
import io.camunda.search.entities.UsageMetricStatisticsEntity.UsageMetricStatisticsEntityTenant;
import io.camunda.search.entities.UsageMetricTUStatisticsEntity;
import io.camunda.search.entities.UsageMetricTUStatisticsEntity.UsageMetricTUStatisticsEntityTenant;
import io.camunda.search.entities.UserEntity;
import io.camunda.search.entities.UserTaskEntity;
import io.camunda.search.entities.VariableEntity;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.entity.ClusterMetadata.AppName;
import io.camunda.zeebe.gateway.protocol.rest.AuthorizationResult;
import io.camunda.zeebe.gateway.protocol.rest.AuthorizationSearchResult;
import io.camunda.zeebe.gateway.protocol.rest.BatchOperationError;
import io.camunda.zeebe.gateway.protocol.rest.BatchOperationError.TypeEnum;
import io.camunda.zeebe.gateway.protocol.rest.BatchOperationItemResponse;
import io.camunda.zeebe.gateway.protocol.rest.BatchOperationItemSearchQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.BatchOperationResponse;
import io.camunda.zeebe.gateway.protocol.rest.BatchOperationSearchQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.BatchOperationTypeEnum;
import io.camunda.zeebe.gateway.protocol.rest.CamundaUserResult;
import io.camunda.zeebe.gateway.protocol.rest.DecisionDefinitionResult;
import io.camunda.zeebe.gateway.protocol.rest.DecisionDefinitionSearchQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.DecisionDefinitionTypeEnum;
import io.camunda.zeebe.gateway.protocol.rest.DecisionInstanceGetQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.DecisionInstanceResult;
import io.camunda.zeebe.gateway.protocol.rest.DecisionInstanceSearchQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.DecisionInstanceStateEnum;
import io.camunda.zeebe.gateway.protocol.rest.DecisionRequirementsResult;
import io.camunda.zeebe.gateway.protocol.rest.DecisionRequirementsSearchQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.ElementInstanceResult;
import io.camunda.zeebe.gateway.protocol.rest.ElementInstanceSearchQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.ElementInstanceStateEnum;
import io.camunda.zeebe.gateway.protocol.rest.EvaluatedDecisionInputItem;
import io.camunda.zeebe.gateway.protocol.rest.EvaluatedDecisionOutputItem;
import io.camunda.zeebe.gateway.protocol.rest.FormResult;
import io.camunda.zeebe.gateway.protocol.rest.GroupClientResult;
import io.camunda.zeebe.gateway.protocol.rest.GroupClientSearchResult;
import io.camunda.zeebe.gateway.protocol.rest.GroupResult;
import io.camunda.zeebe.gateway.protocol.rest.GroupSearchQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.GroupUserResult;
import io.camunda.zeebe.gateway.protocol.rest.GroupUserSearchResult;
import io.camunda.zeebe.gateway.protocol.rest.IncidentResult;
import io.camunda.zeebe.gateway.protocol.rest.IncidentSearchQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.JobKindEnum;
import io.camunda.zeebe.gateway.protocol.rest.JobListenerEventTypeEnum;
import io.camunda.zeebe.gateway.protocol.rest.JobSearchQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.JobSearchResult;
import io.camunda.zeebe.gateway.protocol.rest.JobStateEnum;
import io.camunda.zeebe.gateway.protocol.rest.MappingRuleResult;
import io.camunda.zeebe.gateway.protocol.rest.MappingRuleSearchQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.MatchedDecisionRuleItem;
import io.camunda.zeebe.gateway.protocol.rest.MessageSubscriptionResult;
import io.camunda.zeebe.gateway.protocol.rest.MessageSubscriptionSearchQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.MessageSubscriptionTypeEnum;
import io.camunda.zeebe.gateway.protocol.rest.OwnerTypeEnum;
import io.camunda.zeebe.gateway.protocol.rest.PermissionTypeEnum;
import io.camunda.zeebe.gateway.protocol.rest.ProcessDefinitionElementStatisticsQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.ProcessDefinitionResult;
import io.camunda.zeebe.gateway.protocol.rest.ProcessDefinitionSearchQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.ProcessElementStatisticsResult;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceCallHierarchyEntry;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceElementStatisticsQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceResult;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceSearchQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceSequenceFlowResult;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceSequenceFlowsQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceStateEnum;
import io.camunda.zeebe.gateway.protocol.rest.ResourceTypeEnum;
import io.camunda.zeebe.gateway.protocol.rest.RoleClientResult;
import io.camunda.zeebe.gateway.protocol.rest.RoleClientSearchResult;
import io.camunda.zeebe.gateway.protocol.rest.RoleGroupResult;
import io.camunda.zeebe.gateway.protocol.rest.RoleGroupSearchResult;
import io.camunda.zeebe.gateway.protocol.rest.RoleResult;
import io.camunda.zeebe.gateway.protocol.rest.RoleSearchQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.RoleUserResult;
import io.camunda.zeebe.gateway.protocol.rest.RoleUserSearchResult;
import io.camunda.zeebe.gateway.protocol.rest.SearchQueryPageResponse;
import io.camunda.zeebe.gateway.protocol.rest.TenantClientResult;
import io.camunda.zeebe.gateway.protocol.rest.TenantClientSearchResult;
import io.camunda.zeebe.gateway.protocol.rest.TenantGroupResult;
import io.camunda.zeebe.gateway.protocol.rest.TenantGroupSearchResult;
import io.camunda.zeebe.gateway.protocol.rest.TenantResult;
import io.camunda.zeebe.gateway.protocol.rest.TenantSearchQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.TenantUserResult;
import io.camunda.zeebe.gateway.protocol.rest.TenantUserSearchResult;
import io.camunda.zeebe.gateway.protocol.rest.UsageMetricsResponse;
import io.camunda.zeebe.gateway.protocol.rest.UsageMetricsResponseItem;
import io.camunda.zeebe.gateway.protocol.rest.UserResult;
import io.camunda.zeebe.gateway.protocol.rest.UserSearchResult;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskResult;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskSearchQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskStateEnum;
import io.camunda.zeebe.gateway.protocol.rest.VariableResult;
import io.camunda.zeebe.gateway.protocol.rest.VariableSearchQueryResult;
import io.camunda.zeebe.gateway.protocol.rest.VariableSearchResult;
import io.camunda.zeebe.gateway.rest.util.KeyUtil;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.util.collection.Tuple;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

public final class SearchQueryResponseMapper {

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
        .tenantId(job.tenantId());
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
        .tenantId(p.tenantId());
  }

  public static List<BatchOperationResponse> toBatchOperations(
      final List<BatchOperationEntity> batchOperations) {
    return batchOperations.stream().map(SearchQueryResponseMapper::toBatchOperation).toList();
  }

  public static BatchOperationResponse toBatchOperation(final BatchOperationEntity entity) {
    return new BatchOperationResponse()
        .batchOperationKey(entity.batchOperationKey())
        .state(BatchOperationResponse.StateEnum.fromValue(entity.state().name()))
        .batchOperationType(BatchOperationTypeEnum.fromValue(entity.operationType().name()))
        .startDate(formatDate(entity.startDate()))
        .endDate(formatDate(entity.endDate()))
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
        .itemKey(entity.itemKey().toString())
        .processInstanceKey(entity.processInstanceKey().toString())
        .processedDate(formatDate(entity.processedDate()))
        .errorMessage(entity.errorMessage())
        .state(BatchOperationItemResponse.StateEnum.fromValue(entity.state().name()));
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
        .decisionRequirementsId(d.decisionRequirementsId());
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
        .errorType(IncidentResult.ErrorTypeEnum.fromValue(t.errorType().name()))
        .errorMessage(t.errorMessage())
        .elementId(t.flowNodeId())
        .elementInstanceKey(KeyUtil.keyToString(t.flowNodeInstanceKey()))
        .creationTime(formatDate(t.creationTime()))
        .state(IncidentResult.StateEnum.fromValue(t.state().name()))
        .jobKey(KeyUtil.keyToString(t.jobKey()))
        .tenantId(t.tenantId());
  }

  public static List<MessageSubscriptionResult> toMessageSubscriptions(
      final List<MessageSubscriptionEntity> messageSubscriptions) {
    return messageSubscriptions.stream()
        .map(SearchQueryResponseMapper::toMessageSubscription)
        .toList();
  }

  public static MessageSubscriptionResult toMessageSubscription(
      final MessageSubscriptionEntity messageSubscription) {
    return new MessageSubscriptionResult()
        .messageSubscriptionKey(KeyUtil.keyToString(messageSubscription.messageSubscriptionKey()))
        .processDefinitionId(messageSubscription.processDefinitionId())
        .processDefinitionKey(KeyUtil.keyToString(messageSubscription.processDefinitionKey()))
        .processInstanceKey(KeyUtil.keyToString(messageSubscription.processInstanceKey()))
        .elementId(messageSubscription.flowNodeId())
        .elementInstanceKey(KeyUtil.keyToString(messageSubscription.flowNodeInstanceKey()))
        .messageSubscriptionType(
            MessageSubscriptionTypeEnum.fromValue(
                messageSubscription.messageSubscriptionType().name()))
        .lastUpdatedDate(formatDate(messageSubscription.dateTime()))
        .messageName(messageSubscription.messageName())
        .correlationKey(messageSubscription.correlationKey())
        .tenantId(messageSubscription.tenantId());
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
        .priority(t.priority());
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
        .authorizedApplications(camundaUser.authorizedApplications())
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
        .decisionInstanceKey(KeyUtil.keyToString(entity.decisionInstanceKey()))
        .decisionInstanceId(entity.decisionInstanceId())
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
        .result(entity.result())
        .tenantId(entity.tenantId());
  }

  public static DecisionInstanceGetQueryResult toDecisionInstanceGetQueryResponse(
      final DecisionInstanceEntity entity) {
    return new DecisionInstanceGetQueryResult()
        .decisionInstanceKey(KeyUtil.keyToString(entity.decisionInstanceKey()))
        .decisionInstanceId(entity.decisionInstanceId())
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
      case UNSPECIFIED -> DecisionInstanceStateEnum.UNSPECIFIED;
      default -> DecisionInstanceStateEnum.UNKNOWN;
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
      case UNSPECIFIED -> DecisionDefinitionTypeEnum.UNSPECIFIED;
      default -> DecisionDefinitionTypeEnum.UNKNOWN;
    };
  }

  public static VariableSearchQueryResult toVariableSearchQueryResponse(
      final SearchQueryResult<VariableEntity> result) {
    final var page = toSearchQueryPageResponse(result);
    return new VariableSearchQueryResult()
        .page(page)
        .items(
            ofNullable(result.items())
                .map(SearchQueryResponseMapper::toVariables)
                .orElseGet(Collections::emptyList));
  }

  private static List<VariableSearchResult> toVariables(
      final List<VariableEntity> variableEntities) {
    return variableEntities.stream().map(SearchQueryResponseMapper::toVariable).toList();
  }

  private static VariableSearchResult toVariable(final VariableEntity variableEntity) {
    return new VariableSearchResult()
        .variableKey(KeyUtil.keyToString(variableEntity.variableKey()))
        .name(variableEntity.name())
        .value(variableEntity.value())
        .processInstanceKey(KeyUtil.keyToString(variableEntity.processInstanceKey()))
        .tenantId(variableEntity.tenantId())
        .isTruncated(variableEntity.isPreview())
        .scopeKey(KeyUtil.keyToString(variableEntity.scopeKey()));
  }

  public static VariableResult toVariableItem(final VariableEntity variableEntity) {
    return new VariableResult()
        .variableKey(KeyUtil.keyToString(variableEntity.variableKey()))
        .name(variableEntity.name())
        .value(variableEntity.isPreview() ? variableEntity.fullValue() : variableEntity.value())
        .processInstanceKey(KeyUtil.keyToString(variableEntity.processInstanceKey()))
        .tenantId(variableEntity.tenantId())
        .scopeKey(KeyUtil.keyToString(variableEntity.scopeKey()));
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
    // TODO: handle with WILDCARD constant
    final var resourceId =
        (AuthorizationResourceMatcher.ANY.value() == authorization.resourceMatcher())
            ? "*"
            : authorization.resourceId();
    return new AuthorizationResult()
        .authorizationKey(KeyUtil.keyToString(authorization.authorizationKey()))
        .ownerId(authorization.ownerId())
        .ownerType(OwnerTypeEnum.fromValue(authorization.ownerType()))
        .resourceType(ResourceTypeEnum.valueOf(authorization.resourceType()))
        .resourceId(resourceId)
        .permissionTypes(
            authorization.permissionTypes().stream()
                .map(PermissionType::name)
                .map(PermissionTypeEnum::fromValue)
                .toList());
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

  private record RuleIdentifier(String ruleId, int ruleIndex) {}
}
