/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.search;

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
import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.entities.MappingEntity;
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.entities.ProcessFlowNodeStatisticsEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.entities.RoleEntity;
import io.camunda.search.entities.TenantEntity;
import io.camunda.search.entities.UserEntity;
import io.camunda.search.entities.UserTaskEntity;
import io.camunda.search.entities.VariableEntity;
import io.camunda.search.filter.ProcessDefinitionStatisticsFilter;
import io.camunda.search.query.AuthorizationQuery;
import io.camunda.search.query.BatchOperationQuery;
import io.camunda.search.query.DecisionDefinitionQuery;
import io.camunda.search.query.DecisionInstanceQuery;
import io.camunda.search.query.DecisionRequirementsQuery;
import io.camunda.search.query.FlowNodeInstanceQuery;
import io.camunda.search.query.FormQuery;
import io.camunda.search.query.GroupQuery;
import io.camunda.search.query.IncidentQuery;
import io.camunda.search.query.MappingQuery;
import io.camunda.search.query.ProcessDefinitionQuery;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.search.query.RoleQuery;
import io.camunda.search.query.SearchQueryResult;
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
  public List<AuthorizationEntity> findAllAuthorizations(final AuthorizationQuery filter) {
    return List.of();
  }

  @Override
  public SearchQueryResult<BatchOperationEntity> searchBatchOperations(
      final BatchOperationQuery query) {
    return SearchQueryResult.empty();
  }

  @Override
  public List<BatchOperationItemEntity> getBatchOperationItems(final Long batchOperationKey) {
    return List.of();
  }

  @Override
  public SearchQueryResult<DecisionDefinitionEntity> searchDecisionDefinitions(
      final DecisionDefinitionQuery filter) {
    return SearchQueryResult.empty();
  }

  @Override
  public SearchQueryResult<DecisionInstanceEntity> searchDecisionInstances(
      final DecisionInstanceQuery filter) {
    return SearchQueryResult.empty();
  }

  @Override
  public SearchQueryResult<DecisionRequirementsEntity> searchDecisionRequirements(
      final DecisionRequirementsQuery filter) {
    return SearchQueryResult.empty();
  }

  @Override
  public SearchQueryResult<FlowNodeInstanceEntity> searchFlowNodeInstances(
      final FlowNodeInstanceQuery filter) {
    return SearchQueryResult.empty();
  }

  @Override
  public SearchQueryResult<FormEntity> searchForms(final FormQuery filter) {
    return SearchQueryResult.empty();
  }

  @Override
  public SearchQueryResult<GroupEntity> searchGroups(final GroupQuery query) {
    return SearchQueryResult.empty();
  }

  @Override
  public List<GroupEntity> findAllGroups(final GroupQuery query) {
    return List.of();
  }

  @Override
  public SearchQueryResult<IncidentEntity> searchIncidents(final IncidentQuery filter) {
    return SearchQueryResult.empty();
  }

  @Override
  public SearchQueryResult<MappingEntity> searchMappings(final MappingQuery filter) {
    return SearchQueryResult.empty();
  }

  @Override
  public List<MappingEntity> findAllMappings(final MappingQuery query) {
    return List.of();
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
  public SearchQueryResult<ProcessInstanceEntity> searchProcessInstances(
      final ProcessInstanceQuery query) {
    return SearchQueryResult.empty();
  }

  @Override
  public SearchQueryResult<RoleEntity> searchRoles(final RoleQuery filter) {
    return SearchQueryResult.empty();
  }

  @Override
  public List<RoleEntity> findAllRoles(final RoleQuery filter) {
    return List.of();
  }

  @Override
  public SearchQueryResult<TenantEntity> searchTenants(final TenantQuery filter) {
    return SearchQueryResult.empty();
  }

  @Override
  public List<TenantEntity> findAllTenants(final TenantQuery query) {
    return List.of();
  }

  @Override
  public SearchQueryResult<UserEntity> searchUsers(final UserQuery userQuery) {
    return SearchQueryResult.empty();
  }

  @Override
  public SearchQueryResult<UserTaskEntity> searchUserTasks(final UserTaskQuery filter) {
    return SearchQueryResult.empty();
  }

  @Override
  public SearchQueryResult<VariableEntity> searchVariables(final VariableQuery filter) {
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
}
