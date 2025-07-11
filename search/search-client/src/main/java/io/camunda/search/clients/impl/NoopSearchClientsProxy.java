/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.impl;

import static java.util.Collections.emptyList;

import io.camunda.search.clients.SearchClientsProxy;
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
import io.camunda.search.entities.UserEntity;
import io.camunda.search.entities.UserTaskEntity;
import io.camunda.search.entities.VariableEntity;
import io.camunda.search.filter.ProcessDefinitionStatisticsFilter;
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
import io.camunda.search.query.ProcessDefinitionQuery;
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
import java.util.List;

public class NoopSearchClientsProxy implements SearchClientsProxy {

  @Override
  public SearchQueryResult<AuthorizationEntity> searchAuthorizations(
      final AuthorizationQuery filter) {
    return SearchQueryResult.empty();
  }

  @Override
  public AuthorizationEntity getAuthorizationByKey(final long key) {
    return null;
  }

  @Override
  public SearchQueryResult<BatchOperationEntity> searchBatchOperations(
      final BatchOperationQuery query) {
    return SearchQueryResult.empty();
  }

  @Override
  public SearchQueryResult<BatchOperationItemEntity> searchBatchOperationItems(
      final BatchOperationItemQuery query) {
    return SearchQueryResult.empty();
  }

  @Override
  public BatchOperationEntity getBatchOperationByKey(final String key) {
    return null;
  }

  @Override
  public SearchQueryResult<DecisionDefinitionEntity> searchDecisionDefinitions(
      final DecisionDefinitionQuery filter) {
    return SearchQueryResult.empty();
  }

  @Override
  public DecisionDefinitionEntity getDecisionDefinitionByKey(final long definitionKey) {
    return null;
  }

  @Override
  public SearchQueryResult<DecisionInstanceEntity> searchDecisionInstances(
      final DecisionInstanceQuery filter) {
    return SearchQueryResult.empty();
  }

  @Override
  public DecisionInstanceEntity getDecisionInstanceById(final String decisionInstanceId) {
    return null;
  }

  @Override
  public SearchQueryResult<DecisionRequirementsEntity> searchDecisionRequirements(
      final DecisionRequirementsQuery filter) {
    return SearchQueryResult.empty();
  }

  @Override
  public DecisionRequirementsEntity getDecisionRequirementsByKey(
      final long decisionRequirementsKey, final boolean includeXml) {
    return null;
  }

  @Override
  public SearchQueryResult<FlowNodeInstanceEntity> searchFlowNodeInstances(
      final FlowNodeInstanceQuery filter) {
    return SearchQueryResult.empty();
  }

  @Override
  public FlowNodeInstanceEntity getFlowNodeInstanceByKey(final Long key) {
    return null;
  }

  @Override
  public SearchQueryResult<FormEntity> searchForms(final FormQuery filter) {
    return SearchQueryResult.empty();
  }

  @Override
  public FormEntity getFormByKey(final long key) {
    return null;
  }

  @Override
  public GroupEntity getGroupById(final String groupId) {
    return null;
  }

  @Override
  public SearchQueryResult<GroupEntity> searchGroups(final GroupQuery query) {
    return SearchQueryResult.empty();
  }

  @Override
  public SearchQueryResult<GroupMemberEntity> searchGroupMembers(final GroupQuery query) {
    return SearchQueryResult.empty();
  }

  @Override
  public SearchQueryResult<IncidentEntity> searchIncidents(final IncidentQuery filter) {
    return SearchQueryResult.empty();
  }

  @Override
  public IncidentEntity getIncidentByKey(final long key) {
    return null;
  }

  @Override
  public SearchQueryResult<MappingEntity> searchMappings(final MappingQuery filter) {
    return SearchQueryResult.empty();
  }

  @Override
  public MappingEntity getMappingByKey(final String key) {
    return null;
  }

  @Override
  public SearchQueryResult<ProcessDefinitionEntity> searchProcessDefinitions(
      final ProcessDefinitionQuery filter) {
    return SearchQueryResult.empty();
  }

  @Override
  public ProcessDefinitionEntity getProcessDefinitionByKey(final long processDefinitionKey) {
    return null;
  }

  @Override
  public List<ProcessFlowNodeStatisticsEntity> processDefinitionFlowNodeStatistics(
      final ProcessDefinitionStatisticsFilter filter) {
    return emptyList();
  }

  @Override
  public SearchQueryResult<ProcessInstanceEntity> searchProcessInstances(
      final ProcessInstanceQuery query) {
    return SearchQueryResult.empty();
  }

  @Override
  public ProcessInstanceEntity getProcessInstanceByKey(final long processInstanceKey) {
    return null;
  }

  @Override
  public List<ProcessFlowNodeStatisticsEntity> processInstanceFlowNodeStatistics(
      final long processInstanceKey) {
    return emptyList();
  }

  @Override
  public SearchQueryResult<RoleEntity> searchRoles(final RoleQuery filter) {
    return SearchQueryResult.empty();
  }

  @Override
  public RoleEntity getRoleByKey(final String key) {
    return null;
  }

  @Override
  public SearchQueryResult<RoleMemberEntity> searchRoleMembers(final RoleQuery filter) {
    return SearchQueryResult.empty();
  }

  @Override
  public SearchQueryResult<TenantEntity> searchTenants(final TenantQuery filter) {
    return SearchQueryResult.empty();
  }

  @Override
  public SearchQueryResult<TenantMemberEntity> searchTenantMembers(final TenantQuery filter) {
    return SearchQueryResult.empty();
  }

  @Override
  public TenantEntity getTenantByKey(final String tenantKey) {
    return null;
  }

  @Override
  public SearchQueryResult<UserEntity> searchUsers(final UserQuery userQuery) {
    return SearchQueryResult.empty();
  }

  @Override
  public UserEntity getUserByUsername(final String username) {
    return null;
  }

  @Override
  public SearchQueryResult<UserTaskEntity> searchUserTasks(final UserTaskQuery filter) {
    return SearchQueryResult.empty();
  }

  @Override
  public UserTaskEntity getUserTaskByKey(final long key) {
    return null;
  }

  @Override
  public SearchQueryResult<VariableEntity> searchVariables(final VariableQuery filter) {
    return SearchQueryResult.empty();
  }

  @Override
  public VariableEntity getVariableByKey(final long variableKey) {
    return null;
  }

  @Override
  public SearchQueryResult<SequenceFlowEntity> searchSequenceFlows(final SequenceFlowQuery filter) {
    return SearchQueryResult.empty();
  }

  @Override
  public SearchQueryResult<MessageSubscriptionEntity> searchMessageSubscriptions(
      final MessageSubscriptionQuery filter) {
    return SearchQueryResult.empty();
  }

  @Override
  public SearchClientsProxy withSecurityContext(final SecurityContext securityContext) {
    return this;
  }

  @Override
  public Long countAssignees(final UsageMetricsQuery query) {
    return 0L;
  }

  @Override
  public Long countProcessInstances(final UsageMetricsQuery query) {
    return 0L;
  }

  @Override
  public Long countDecisionInstances(final UsageMetricsQuery query) {
    return 0L;
  }

  @Override
  public SearchQueryResult<JobEntity> searchJobs(final JobQuery query) {
    return SearchQueryResult.empty();
  }
}
