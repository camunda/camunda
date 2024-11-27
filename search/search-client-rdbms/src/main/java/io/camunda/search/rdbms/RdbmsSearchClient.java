/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.rdbms;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.domain.UserTaskDbQuery;
import io.camunda.search.clients.AuthorizationSearchClient;
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
import io.camunda.search.clients.UserSearchClient;
import io.camunda.search.clients.UserTaskSearchClient;
import io.camunda.search.clients.VariableSearchClient;
import io.camunda.search.entities.AuthorizationEntity;
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
        GroupSearchClient {

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
      final AuthorizationQuery filter) {
    return null;
  }

  @Override
  public List<AuthorizationEntity> findAllAuthorizations(final AuthorizationQuery filter) {
    return null;
  }

  @Override
  public RdbmsSearchClient withSecurityContext(final SecurityContext securityContext) {
    return this;
  }

  @Override
  public SearchQueryResult<MappingEntity> searchMappings(final MappingQuery filter) {
    LOG.debug("[RDBMS Search Client] Search for mappings: {}", filter);

    return rdbmsService.getMappingReader().search(filter);
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
      final DecisionRequirementsQuery filter) {
    return null;
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
  public SearchQueryResult<IncidentEntity> searchIncidents(final IncidentQuery filter) {
    return null;
  }

  @Override
  public SearchQueryResult<UserEntity> searchUsers(final UserQuery filter) {
    return null;
  }

  @Override
  public SearchQueryResult<GroupEntity> searchGroups(final GroupQuery filter) {
    return null;
  }

  @Override
  public SearchQueryResult<UserTaskEntity> searchUserTasks(final UserTaskQuery query) {
    final var searchResult =
        rdbmsService
            .getUserTaskReader()
            .search(
                UserTaskDbQuery.of(
                    b -> b.filter(query.filter()).sort(query.sort()).page(query.page())));

    return new SearchQueryResult<>(searchResult.total(), searchResult.hits(), null);
  }

  @Override
  public SearchQueryResult<VariableEntity> searchVariables(final VariableQuery query) {
    LOG.debug("[RDBMS Search Client] Search for variables: {}", query);

    return rdbmsService.getVariableReader().search(query);
  }

  @Override
  public SearchQueryResult<RoleEntity> searchRoles(final RoleQuery filter) {
    return null;
  }

  @Override
  public SearchQueryResult<TenantEntity> searchTenants(final TenantQuery query) {
    LOG.debug("[RDBMS Search Client] Search for tenants: {}", query);

    return rdbmsService.getTenantReader().search(query);
  }

  @Override
  public SearchQueryResult<ProcessDefinitionEntity> searchProcessDefinitions(
      final ProcessDefinitionQuery query) {
    LOG.debug("[RDBMS Search Client] Search for processDefinition: {}", query);

    return rdbmsService.getProcessDefinitionReader().search(query);
  }
}
