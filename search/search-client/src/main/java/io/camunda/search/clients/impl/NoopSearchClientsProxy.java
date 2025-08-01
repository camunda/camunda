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
import io.camunda.search.entities.UsageMetricTUStatisticsEntity;
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
import io.camunda.search.query.MappingRuleQuery;
import io.camunda.search.query.MessageSubscriptionQuery;
import io.camunda.search.query.ProcessDefinitionQuery;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.search.query.RoleQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.SequenceFlowQuery;
import io.camunda.search.query.TenantQuery;
import io.camunda.search.query.UsageMetricsQuery;
import io.camunda.search.query.UsageMetricsTUQuery;
import io.camunda.search.query.UserQuery;
import io.camunda.search.query.UserTaskQuery;
import io.camunda.search.query.VariableQuery;
import io.camunda.security.auth.SecurityContext;
import java.util.List;
import java.util.Map;

public class NoopSearchClientsProxy implements SearchClientsProxy {

  @Override
  public AuthorizationEntity getAuthorization(final long key) {
    return null;
  }

  @Override
  public SearchQueryResult<AuthorizationEntity> searchAuthorizations(
      final AuthorizationQuery filter) {
    return SearchQueryResult.empty();
  }

  @Override
  public BatchOperationEntity getBatchOperation(final String id) {
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
  public DecisionDefinitionEntity getDecisionDefinition(final long key) {
    return null;
  }

  @Override
  public SearchQueryResult<DecisionDefinitionEntity> searchDecisionDefinitions(
      final DecisionDefinitionQuery filter) {
    return SearchQueryResult.empty();
  }

  @Override
  public DecisionInstanceEntity getDecisionInstance(final String id) {
    return null;
  }

  @Override
  public SearchQueryResult<DecisionInstanceEntity> searchDecisionInstances(
      final DecisionInstanceQuery filter) {
    return SearchQueryResult.empty();
  }

  @Override
  public DecisionRequirementsEntity getDecisionRequirements(
      final long key, final boolean includeXml) {
    return null;
  }

  @Override
  public SearchQueryResult<DecisionRequirementsEntity> searchDecisionRequirements(
      final DecisionRequirementsQuery filter) {
    return SearchQueryResult.empty();
  }

  @Override
  public FlowNodeInstanceEntity getFlowNodeInstance(final long key) {
    return null;
  }

  @Override
  public SearchQueryResult<FlowNodeInstanceEntity> searchFlowNodeInstances(
      final FlowNodeInstanceQuery filter) {
    return SearchQueryResult.empty();
  }

  @Override
  public FormEntity getForm(final long key) {
    return null;
  }

  @Override
  public SearchQueryResult<FormEntity> searchForms(final FormQuery filter) {
    return SearchQueryResult.empty();
  }

  @Override
  public GroupEntity getGroup(final String id) {
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
  public IncidentEntity getIncident(final long key) {
    return null;
  }

  @Override
  public SearchQueryResult<IncidentEntity> searchIncidents(final IncidentQuery filter) {
    return SearchQueryResult.empty();
  }

  @Override
  public MappingRuleEntity getMappingRule(final String id) {
    return null;
  }

  @Override
  public SearchQueryResult<MappingRuleEntity> searchMappingRules(
      final MappingRuleQuery mappingRuleQuery) {
    return SearchQueryResult.empty();
  }

  @Override
  public ProcessDefinitionEntity getProcessDefinition(final long key) {
    return null;
  }

  @Override
  public SearchQueryResult<ProcessDefinitionEntity> searchProcessDefinitions(
      final ProcessDefinitionQuery filter) {
    return SearchQueryResult.empty();
  }

  @Override
  public List<ProcessFlowNodeStatisticsEntity> processDefinitionFlowNodeStatistics(
      final ProcessDefinitionStatisticsFilter filter) {
    return emptyList();
  }

  @Override
  public ProcessInstanceEntity getProcessInstance(final long processInstanceKey) {
    return null;
  }

  @Override
  public SearchQueryResult<ProcessInstanceEntity> searchProcessInstances(
      final ProcessInstanceQuery query) {
    return SearchQueryResult.empty();
  }

  @Override
  public List<ProcessFlowNodeStatisticsEntity> processInstanceFlowNodeStatistics(
      final long processInstanceKey) {
    return emptyList();
  }

  @Override
  public RoleEntity getRole(final String id) {
    return null;
  }

  @Override
  public SearchQueryResult<RoleEntity> searchRoles(final RoleQuery filter) {
    return SearchQueryResult.empty();
  }

  @Override
  public SearchQueryResult<RoleMemberEntity> searchRoleMembers(final RoleQuery filter) {
    return SearchQueryResult.empty();
  }

  @Override
  public TenantEntity getTenant(final String id) {
    return null;
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
  public UserEntity getUser(final String id) {
    return null;
  }

  @Override
  public SearchQueryResult<UserEntity> searchUsers(final UserQuery userQuery) {
    return SearchQueryResult.empty();
  }

  @Override
  public UserTaskEntity getUserTask(final long key) {
    return null;
  }

  @Override
  public SearchQueryResult<UserTaskEntity> searchUserTasks(final UserTaskQuery filter) {
    return SearchQueryResult.empty();
  }

  @Override
  public VariableEntity getVariable(final long key) {
    return null;
  }

  @Override
  public SearchQueryResult<VariableEntity> searchVariables(final VariableQuery filter) {
    return SearchQueryResult.empty();
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
  public UsageMetricStatisticsEntity usageMetricStatistics(final UsageMetricsQuery query) {
    return new UsageMetricStatisticsEntity(0, 0, 0, Map.of());
  }

  @Override
  public UsageMetricTUStatisticsEntity usageMetricTUStatistics(final UsageMetricsTUQuery query) {
    return new UsageMetricTUStatisticsEntity(0, Map.of());
  }

  @Override
  public SearchQueryResult<JobEntity> searchJobs(final JobQuery query) {
    return SearchQueryResult.empty();
  }
}
