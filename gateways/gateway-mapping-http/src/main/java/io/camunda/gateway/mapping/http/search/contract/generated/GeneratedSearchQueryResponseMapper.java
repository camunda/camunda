/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import static java.util.Optional.ofNullable;

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
import io.camunda.search.entities.GlobalListenerEntity;
import io.camunda.search.entities.GroupEntity;
import io.camunda.search.entities.GroupMemberEntity;
import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.entities.JobEntity;
import io.camunda.search.entities.MappingRuleEntity;
import io.camunda.search.entities.MessageSubscriptionEntity;
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.entities.RoleEntity;
import io.camunda.search.entities.RoleMemberEntity;
import io.camunda.search.entities.TenantEntity;
import io.camunda.search.entities.TenantMemberEntity;
import io.camunda.search.entities.UserEntity;
import io.camunda.search.entities.UserTaskEntity;
import io.camunda.search.entities.VariableEntity;
import io.camunda.search.query.SearchQueryResult;
import jakarta.annotation.Generated;
import java.util.Collections;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public final class GeneratedSearchQueryResponseMapper {

  private GeneratedSearchQueryResponseMapper() {}

  public static StrictSearchQueryPage toStrictSearchQueryPage(final SearchQueryResult<?> result) {
    return new StrictSearchQueryPage(
        result.total(), result.hasMoreTotalItems(), result.startCursor(), result.endCursor());
  }

  public static StrictSearchQueryResult<GeneratedProcessDefinitionStrictContract>
      toProcessDefinitionSearchQueryResponse(
          final SearchQueryResult<ProcessDefinitionEntity> result) {
    return new StrictSearchQueryResult<>(
        ofNullable(result.items())
            .map(items -> items.stream().map(ProcessDefinitionContractAdapter::adapt).toList())
            .orElseGet(Collections::emptyList),
        toStrictSearchQueryPage(result));
  }

  public static StrictSearchQueryResult<GeneratedProcessInstanceStrictContract>
      toProcessInstanceSearchQueryResponse(final SearchQueryResult<ProcessInstanceEntity> result) {
    return new StrictSearchQueryResult<>(
        ofNullable(result.items())
            .map(items -> items.stream().map(ProcessInstanceContractAdapter::adapt).toList())
            .orElseGet(Collections::emptyList),
        toStrictSearchQueryPage(result));
  }

  public static StrictSearchQueryResult<GeneratedJobSearchStrictContract> toJobSearchQueryResponse(
      final SearchQueryResult<JobEntity> result) {
    return new StrictSearchQueryResult<>(
        ofNullable(result.items())
            .map(items -> items.stream().map(JobContractAdapter::adapt).toList())
            .orElseGet(Collections::emptyList),
        toStrictSearchQueryPage(result));
  }

  public static StrictSearchQueryResult<GeneratedRoleStrictContract> toRoleSearchQueryResponse(
      final SearchQueryResult<RoleEntity> result) {
    return new StrictSearchQueryResult<>(
        ofNullable(result.items())
            .map(RoleContractAdapter::adapt)
            .orElseGet(Collections::emptyList),
        toStrictSearchQueryPage(result));
  }

  public static StrictSearchQueryResult<GeneratedRoleGroupStrictContract>
      toRoleGroupSearchQueryResponse(final SearchQueryResult<RoleMemberEntity> result) {
    return new StrictSearchQueryResult<>(
        ofNullable(result.items())
            .map(MemberContractAdapter::toRoleGroups)
            .orElseGet(Collections::emptyList),
        toStrictSearchQueryPage(result));
  }

  public static StrictSearchQueryResult<GeneratedRoleUserStrictContract>
      toRoleUserSearchQueryResponse(final SearchQueryResult<RoleMemberEntity> result) {
    return new StrictSearchQueryResult<>(
        ofNullable(result.items())
            .map(MemberContractAdapter::toRoleUsers)
            .orElseGet(Collections::emptyList),
        toStrictSearchQueryPage(result));
  }

  public static StrictSearchQueryResult<GeneratedRoleClientStrictContract>
      toRoleClientSearchQueryResponse(final SearchQueryResult<RoleMemberEntity> result) {
    return new StrictSearchQueryResult<>(
        ofNullable(result.items())
            .map(MemberContractAdapter::toRoleClients)
            .orElseGet(Collections::emptyList),
        toStrictSearchQueryPage(result));
  }

  public static StrictSearchQueryResult<GeneratedGroupStrictContract> toGroupSearchQueryResponse(
      final SearchQueryResult<GroupEntity> result) {
    return new StrictSearchQueryResult<>(
        ofNullable(result.items())
            .map(GroupContractAdapter::adapt)
            .orElseGet(Collections::emptyList),
        toStrictSearchQueryPage(result));
  }

  public static StrictSearchQueryResult<GeneratedGroupUserStrictContract>
      toGroupUserSearchQueryResponse(final SearchQueryResult<GroupMemberEntity> result) {
    return new StrictSearchQueryResult<>(
        ofNullable(result.items())
            .map(MemberContractAdapter::toGroupUsers)
            .orElseGet(Collections::emptyList),
        toStrictSearchQueryPage(result));
  }

  public static StrictSearchQueryResult<GeneratedGroupClientStrictContract>
      toGroupClientSearchQueryResponse(final SearchQueryResult<GroupMemberEntity> result) {
    return new StrictSearchQueryResult<>(
        ofNullable(result.items())
            .map(MemberContractAdapter::toGroupClients)
            .orElseGet(Collections::emptyList),
        toStrictSearchQueryPage(result));
  }

  public static StrictSearchQueryResult<GeneratedTenantStrictContract> toTenantSearchQueryResponse(
      final SearchQueryResult<TenantEntity> result) {
    return new StrictSearchQueryResult<>(
        ofNullable(result.items())
            .map(TenantContractAdapter::adapt)
            .orElseGet(Collections::emptyList),
        toStrictSearchQueryPage(result));
  }

  public static StrictSearchQueryResult<GeneratedTenantGroupStrictContract>
      toTenantGroupSearchQueryResponse(final SearchQueryResult<TenantMemberEntity> result) {
    return new StrictSearchQueryResult<>(
        ofNullable(result.items())
            .map(MemberContractAdapter::toTenantGroups)
            .orElseGet(Collections::emptyList),
        toStrictSearchQueryPage(result));
  }

  public static StrictSearchQueryResult<GeneratedTenantUserStrictContract>
      toTenantUserSearchQueryResponse(final SearchQueryResult<TenantMemberEntity> result) {
    return new StrictSearchQueryResult<>(
        ofNullable(result.items())
            .map(MemberContractAdapter::toTenantUsers)
            .orElseGet(Collections::emptyList),
        toStrictSearchQueryPage(result));
  }

  public static StrictSearchQueryResult<GeneratedTenantClientStrictContract>
      toTenantClientSearchQueryResponse(final SearchQueryResult<TenantMemberEntity> result) {
    return new StrictSearchQueryResult<>(
        ofNullable(result.items())
            .map(MemberContractAdapter::toTenantClients)
            .orElseGet(Collections::emptyList),
        toStrictSearchQueryPage(result));
  }

  public static StrictSearchQueryResult<GeneratedMappingRuleStrictContract>
      toMappingRuleSearchQueryResponse(final SearchQueryResult<MappingRuleEntity> result) {
    return new StrictSearchQueryResult<>(
        ofNullable(result.items())
            .map(MappingRuleContractAdapter::adapt)
            .orElseGet(Collections::emptyList),
        toStrictSearchQueryPage(result));
  }

  public static StrictSearchQueryResult<GeneratedDecisionDefinitionStrictContract>
      toDecisionDefinitionSearchQueryResponse(
          final SearchQueryResult<DecisionDefinitionEntity> result) {
    return new StrictSearchQueryResult<>(
        ofNullable(result.items())
            .map(items -> items.stream().map(DecisionDefinitionContractAdapter::adapt).toList())
            .orElseGet(Collections::emptyList),
        toStrictSearchQueryPage(result));
  }

  public static StrictSearchQueryResult<GeneratedDecisionRequirementsStrictContract>
      toDecisionRequirementsSearchQueryResponse(
          final SearchQueryResult<DecisionRequirementsEntity> result) {
    return new StrictSearchQueryResult<>(
        ofNullable(result.items())
            .map(items -> items.stream().map(DecisionRequirementsContractAdapter::adapt).toList())
            .orElseGet(Collections::emptyList),
        toStrictSearchQueryPage(result));
  }

  public static StrictSearchQueryResult<GeneratedElementInstanceStrictContract>
      toElementInstanceSearchQueryResponse(final SearchQueryResult<FlowNodeInstanceEntity> result) {
    return new StrictSearchQueryResult<>(
        ofNullable(result.items())
            .map(items -> items.stream().map(ElementInstanceContractAdapter::adapt).toList())
            .orElseGet(Collections::emptyList),
        toStrictSearchQueryPage(result));
  }

  public static StrictSearchQueryResult<GeneratedDecisionInstanceStrictContract>
      toDecisionInstanceSearchQueryResponse(
          final SearchQueryResult<DecisionInstanceEntity> result) {
    return new StrictSearchQueryResult<>(
        ofNullable(result.items())
            .map(DecisionInstanceContractAdapter::toSearchProjections)
            .orElseGet(Collections::emptyList),
        toStrictSearchQueryPage(result));
  }

  public static StrictSearchQueryResult<GeneratedUserTaskStrictContract>
      toUserTaskSearchQueryResponse(final SearchQueryResult<UserTaskEntity> result) {
    return new StrictSearchQueryResult<>(
        ofNullable(result.items())
            .map(items -> items.stream().map(UserTaskContractAdapter::adapt).toList())
            .orElseGet(Collections::emptyList),
        toStrictSearchQueryPage(result));
  }

  public static StrictSearchQueryResult<GeneratedUserStrictContract> toUserSearchQueryResponse(
      final SearchQueryResult<UserEntity> result) {
    return new StrictSearchQueryResult<>(
        ofNullable(result.items())
            .map(items -> items.stream().map(UserContractAdapter::adapt).toList())
            .orElseGet(Collections::emptyList),
        toStrictSearchQueryPage(result));
  }

  public static StrictSearchQueryResult<GeneratedBatchOperationResponseStrictContract>
      toBatchOperationSearchQueryResult(final SearchQueryResult<BatchOperationEntity> result) {
    return new StrictSearchQueryResult<>(
        ofNullable(result.items())
            .map(items -> items.stream().map(BatchOperationResponseContractAdapter::adapt).toList())
            .orElseGet(Collections::emptyList),
        toStrictSearchQueryPage(result));
  }

  public static StrictSearchQueryResult<GeneratedBatchOperationItemResponseStrictContract>
      toBatchOperationItemSearchQueryResult(
          final SearchQueryResult<BatchOperationItemEntity> result) {
    return new StrictSearchQueryResult<>(
        ofNullable(result.items())
            .map(
                items ->
                    items.stream().map(BatchOperationItemResponseContractAdapter::adapt).toList())
            .orElseGet(Collections::emptyList),
        toStrictSearchQueryPage(result));
  }

  public static StrictSearchQueryResult<GeneratedIncidentStrictContract>
      toIncidentSearchQueryResponse(final SearchQueryResult<IncidentEntity> result) {
    return new StrictSearchQueryResult<>(
        ofNullable(result.items())
            .map(items -> items.stream().map(IncidentContractAdapter::adapt).toList())
            .orElseGet(Collections::emptyList),
        toStrictSearchQueryPage(result));
  }

  public static StrictSearchQueryResult<GeneratedMessageSubscriptionStrictContract>
      toMessageSubscriptionSearchQueryResponse(
          final SearchQueryResult<MessageSubscriptionEntity> result) {
    return new StrictSearchQueryResult<>(
        ofNullable(result.items())
            .map(items -> items.stream().map(MessageSubscriptionContractAdapter::adapt).toList())
            .orElseGet(Collections::emptyList),
        toStrictSearchQueryPage(result));
  }

  public static StrictSearchQueryResult<GeneratedCorrelatedMessageSubscriptionStrictContract>
      toCorrelatedMessageSubscriptionSearchQueryResponse(
          final SearchQueryResult<CorrelatedMessageSubscriptionEntity> result) {
    return new StrictSearchQueryResult<>(
        ofNullable(result.items())
            .map(
                items ->
                    items.stream()
                        .map(CorrelatedMessageSubscriptionContractAdapter::adapt)
                        .toList())
            .orElseGet(Collections::emptyList),
        toStrictSearchQueryPage(result));
  }

  public static StrictSearchQueryResult<GeneratedAuthorizationStrictContract>
      toAuthorizationSearchQueryResponse(final SearchQueryResult<AuthorizationEntity> result) {
    return new StrictSearchQueryResult<>(
        ofNullable(result.items())
            .map(items -> items.stream().map(AuthorizationContractAdapter::adapt).toList())
            .orElseGet(Collections::emptyList),
        toStrictSearchQueryPage(result));
  }

  public static StrictSearchQueryResult<GeneratedAuditLogStrictContract>
      toAuditLogSearchQueryResponse(final SearchQueryResult<AuditLogEntity> result) {
    return new StrictSearchQueryResult<>(
        ofNullable(result.items())
            .map(items -> items.stream().map(AuditLogContractAdapter::adapt).toList())
            .orElseGet(Collections::emptyList),
        toStrictSearchQueryPage(result));
  }

  public static StrictSearchQueryResult<GeneratedGlobalTaskListenerStrictContract>
      toGlobalTaskListenerSearchQueryResponse(
          final SearchQueryResult<GlobalListenerEntity> result) {
    return new StrictSearchQueryResult<>(
        ofNullable(result.items())
            .map(items -> items.stream().map(GlobalTaskListenerContractAdapter::adapt).toList())
            .orElseGet(Collections::emptyList),
        toStrictSearchQueryPage(result));
  }

  public static StrictSearchQueryResult<GeneratedVariableSearchStrictContract>
      toVariableSearchQueryResponse(
          final SearchQueryResult<VariableEntity> result, final boolean truncateValues) {
    return new StrictSearchQueryResult<>(
        ofNullable(result.items())
            .map(items -> VariableContractAdapter.toSearchProjections(items, truncateValues))
            .orElseGet(Collections::emptyList),
        toStrictSearchQueryPage(result));
  }

  public static StrictSearchQueryResult<GeneratedClusterVariableSearchStrictContract>
      toClusterVariableSearchQueryResponse(
          final SearchQueryResult<ClusterVariableEntity> result, final boolean truncateValues) {
    return new StrictSearchQueryResult<>(
        ofNullable(result.items())
            .map(items -> ClusterVariableContractAdapter.toSearchProjections(items, truncateValues))
            .orElseGet(Collections::emptyList),
        toStrictSearchQueryPage(result));
  }

  public static GeneratedProcessDefinitionStrictContract toProcessDefinition(
      final ProcessDefinitionEntity entity) {
    return ProcessDefinitionContractAdapter.adapt(entity);
  }

  public static GeneratedProcessInstanceStrictContract toProcessInstance(
      final ProcessInstanceEntity entity) {
    return ProcessInstanceContractAdapter.adapt(entity);
  }

  public static GeneratedBatchOperationResponseStrictContract toBatchOperation(
      final BatchOperationEntity entity) {
    return BatchOperationResponseContractAdapter.adapt(entity);
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

  public static GeneratedDecisionInstanceStrictContract toDecisionInstance(
      final DecisionInstanceEntity entity) {
    return DecisionInstanceContractAdapter.toSearchProjection(entity);
  }

  public static GeneratedVariableStrictContract toVariableItem(final VariableEntity entity) {
    return VariableContractAdapter.toItemProjection(entity);
  }

  public static GeneratedClusterVariableStrictContract toClusterVariableResult(
      final ClusterVariableEntity entity) {
    return ClusterVariableContractAdapter.toItemProjection(entity);
  }

  public static GeneratedAuthorizationStrictContract toAuthorization(
      final AuthorizationEntity entity) {
    return AuthorizationContractAdapter.adapt(entity);
  }

  public static GeneratedAuditLogStrictContract toAuditLog(final AuditLogEntity entity) {
    return AuditLogContractAdapter.adapt(entity);
  }

  public static GeneratedProcessInstanceCallHierarchyEntryStrictContract
      toProcessInstanceCallHierarchyEntry(final ProcessInstanceEntity entity) {
    return ProcessInstanceCallHierarchyEntryContractAdapter.adapt(entity);
  }

  public static GeneratedGlobalTaskListenerStrictContract toGlobalTaskListenerResult(
      final GlobalListenerEntity entity) {
    return GlobalTaskListenerContractAdapter.adapt(entity);
  }
}
