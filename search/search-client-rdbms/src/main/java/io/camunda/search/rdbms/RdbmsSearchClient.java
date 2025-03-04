/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.rdbms;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.search.clients.AuthorizationSearchClient;
import io.camunda.search.clients.BatchOperationSearchClient;
import io.camunda.search.clients.DecisionDefinitionSearchClient;
import io.camunda.search.clients.DecisionInstanceSearchClient;
import io.camunda.search.clients.DecisionRequirementSearchClient;
import io.camunda.search.clients.FlowNodeInstanceSearchClient;
import io.camunda.search.clients.FormSearchClient;
import io.camunda.search.clients.GroupSearchClient;
import io.camunda.search.clients.IncidentSearchClient;
import io.camunda.search.clients.MappingSearchClient;
import io.camunda.search.clients.ProcessDefinitionSearchClient;
import io.camunda.search.clients.ProcessInstanceSearchClient;
import io.camunda.search.clients.RoleSearchClient;
import io.camunda.search.clients.TenantSearchClient;
import io.camunda.search.clients.UsageMetricsSearchClient;
import io.camunda.search.clients.UserSearchClient;
import io.camunda.search.clients.UserTaskSearchClient;
import io.camunda.search.clients.VariableSearchClient;
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
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.entities.RoleEntity;
import io.camunda.search.entities.TenantEntity;
import io.camunda.search.entities.UserEntity;
import io.camunda.search.entities.UserTaskEntity;
import io.camunda.search.entities.VariableEntity;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RdbmsSearchClient
    implements AuthorizationSearchClient,
        DecisionDefinitionSearchClient,
        DecisionInstanceSearchClient,
        DecisionRequirementSearchClient,
        FlowNodeInstanceSearchClient,
        FormSearchClient,
        IncidentSearchClient,
        ProcessInstanceSearchClient,
        ProcessDefinitionSearchClient,
        UserTaskSearchClient,
        UserSearchClient,
        VariableSearchClient,
        RoleSearchClient,
        TenantSearchClient,
        MappingSearchClient,
        GroupSearchClient,
        UsageMetricsSearchClient,
        BatchOperationSearchClient {

  private static final Logger LOG = LoggerFactory.getLogger(RdbmsSearchClient.class);

  private final RdbmsService rdbmsService;

  public RdbmsSearchClient(final RdbmsService rdbmsService) {
    this.rdbmsService = rdbmsService;
  }

  @Override
  public SearchQueryResult<ProcessInstanceEntity> searchProcessInstances(
      final ProcessInstanceQuery query) {
    LOG.debug("[RDBMS Search Client] Search for processInstance: {}", query);

    return rdbmsService.getProcessInstanceReader().search(query);
  }

  @Override
  public SearchQueryResult<AuthorizationEntity> searchAuthorizations(
      final AuthorizationQuery query) {
    LOG.debug("[RDBMS Search Client] Search for authorizations: {}", query);

    return rdbmsService.getAuthorizationReader().search(query);
  }

  @Override
  public List<AuthorizationEntity> findAllAuthorizations(final AuthorizationQuery query) {
    LOG.debug("[RDBMS Search Client] Search for all authorizations: {}", query);

    // search without size boundary to find all items
    return rdbmsService
        .getAuthorizationReader()
        .search(
            AuthorizationQuery.of(
                b ->
                    b.filter(query.filter())
                        .sort(query.sort())
                        .page(p -> p.size(Integer.MAX_VALUE))))
        .items();
  }

  @Override
  public RdbmsSearchClient withSecurityContext(final SecurityContext securityContext) {
    return this;
  }

  @Override
  public Long countAssignees(final UsageMetricsQuery query) {
    throw new UnsupportedOperationException(
        "UsageMetricsClient countAssignees not implemented yet.");
  }

  @Override
  public Long countProcessInstances(final UsageMetricsQuery query) {
    throw new UnsupportedOperationException(
        "UsageMetricsClient countProcessInstances not implemented yet.");
  }

  @Override
  public Long countDecisionInstances(final UsageMetricsQuery query) {
    throw new UnsupportedOperationException(
        "UsageMetricsClient countDecisionInstances not implemented yet.");
  }

  @Override
  public SearchQueryResult<MappingEntity> searchMappings(final MappingQuery filter) {
    LOG.debug("[RDBMS Search Client] Search for mappings: {}", filter);

    return rdbmsService.getMappingReader().search(filter);
  }

  @Override
  public List<MappingEntity> findAllMappings(final MappingQuery query) {
    LOG.debug("[RDBMS Search Client] Search for all mappings: {}", query);

    // search without size boundary to find all items
    return rdbmsService
        .getMappingReader()
        .search(
            MappingQuery.of(
                b ->
                    b.filter(query.filter())
                        .sort(query.sort())
                        .page(p -> p.size(Integer.MAX_VALUE))))
        .items();
  }

  @Override
  public SearchQueryResult<DecisionDefinitionEntity> searchDecisionDefinitions(
      final DecisionDefinitionQuery query) {
    LOG.debug("[RDBMS Search Client] Search for decisionDefinition: {}", query);

    return rdbmsService.getDecisionDefinitionReader().search(query);
  }

  @Override
  public SearchQueryResult<DecisionInstanceEntity> searchDecisionInstances(
      final DecisionInstanceQuery query) {
    LOG.debug("[RDBMS Search Client] Search for decisionInstances: {}", query);

    return rdbmsService.getDecisionInstanceReader().search(query);
  }

  @Override
  public SearchQueryResult<DecisionRequirementsEntity> searchDecisionRequirements(
      final DecisionRequirementsQuery query) {
    LOG.debug("[RDBMS Search Client] Search for decisionRequirements: {}", query);

    return rdbmsService.getDecisionRequirementsReader().search(query);
  }

  @Override
  public SearchQueryResult<FlowNodeInstanceEntity> searchFlowNodeInstances(
      final FlowNodeInstanceQuery query) {
    return rdbmsService.getFlowNodeInstanceReader().search(query);
  }

  @Override
  public SearchQueryResult<FormEntity> searchForms(final FormQuery filter) {
    return rdbmsService.getFormReader().search(filter);
  }

  @Override
  public SearchQueryResult<IncidentEntity> searchIncidents(final IncidentQuery query) {
    LOG.debug("[RDBMS Search Client] Search for incidents: {}", query);

    return rdbmsService.getIncidentReader().search(query);
  }

  @Override
  public SearchQueryResult<UserEntity> searchUsers(final UserQuery query) {
    LOG.debug("[RDBMS Search Client] Search for users: {}", query);

    return rdbmsService.getUserReader().search(query);
  }

  @Override
  public SearchQueryResult<GroupEntity> searchGroups(final GroupQuery query) {
    LOG.debug("[RDBMS Search Client] Search for groups: {}", query);

    return rdbmsService.getGroupReader().search(query);
  }

  @Override
  public List<GroupEntity> findAllGroups(final GroupQuery query) {
    LOG.debug("[RDBMS Search Client] Search for all groups: {}", query);

    // search without size boundary to find all items
    return rdbmsService
        .getGroupReader()
        .search(
            GroupQuery.of(
                b ->
                    b.filter(query.filter())
                        .sort(query.sort())
                        .page(p -> p.size(Integer.MAX_VALUE))))
        .items();
  }

  @Override
  public SearchQueryResult<UserTaskEntity> searchUserTasks(final UserTaskQuery query) {
    return rdbmsService
        .getUserTaskReader()
        .search(
            UserTaskQuery.of(b -> b.filter(query.filter()).sort(query.sort()).page(query.page())));
  }

  @Override
  public SearchQueryResult<VariableEntity> searchVariables(final VariableQuery query) {
    LOG.debug("[RDBMS Search Client] Search for variables: {}", query);

    return rdbmsService.getVariableReader().search(query);
  }

  @Override
  public SearchQueryResult<RoleEntity> searchRoles(final RoleQuery query) {
    LOG.debug("[RDBMS Search Client] Search for roles: {}", query);

    return rdbmsService.getRoleReader().search(query);
  }

  @Override
  public List<RoleEntity> findAllRoles(final RoleQuery filter) {
    return List.of();
  }

  @Override
  public SearchQueryResult<TenantEntity> searchTenants(final TenantQuery query) {
    LOG.debug("[RDBMS Search Client] Search for tenants: {}", query);

    return rdbmsService.getTenantReader().search(query);
  }

  @Override
  public List<TenantEntity> findAllTenants(final TenantQuery query) {
    LOG.debug("[RDBMS Search Client] Search for all tenants: {}", query);

    // search without size boundary to find all items
    return rdbmsService
        .getTenantReader()
        .search(
            TenantQuery.of(
                b ->
                    b.filter(query.filter())
                        .sort(query.sort())
                        .page(p -> p.size(Integer.MAX_VALUE))))
        .items();
  }

  @Override
  public SearchQueryResult<ProcessDefinitionEntity> searchProcessDefinitions(
      final ProcessDefinitionQuery query) {
    LOG.debug("[RDBMS Search Client] Search for processDefinition: {}", query);

    return rdbmsService.getProcessDefinitionReader().search(query);
  }

  @Override
  public SearchQueryResult<BatchOperationEntity> searchBatchOperations(
      final BatchOperationQuery query) {
    LOG.debug("[RDBMS Search Client] Search for batch operations: {}", query);

    return rdbmsService.getBatchOperationReader().search(query);
  }

  @Override
  public List<BatchOperationItemEntity> getBatchOperationItems(final Long batchOperationKey) {
    LOG.debug("[RDBMS Search Client] Search for batch operation items by batchOperationKey: {}", batchOperationKey);

    return rdbmsService.getBatchOperationReader().getItems(batchOperationKey);
  }
}
