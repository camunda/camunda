/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.rdbms;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.domain.FlowNodeInstanceDbQuery;
import io.camunda.db.rdbms.read.domain.ProcessInstanceDbQuery;
import io.camunda.search.clients.AuthorizationSearchClient;
import io.camunda.search.clients.DecisionDefinitionSearchClient;
import io.camunda.search.clients.DecisionInstanceSearchClient;
import io.camunda.search.clients.DecisionRequirementSearchClient;
import io.camunda.search.clients.FlowNodeInstanceSearchClient;
import io.camunda.search.clients.FormSearchClient;
import io.camunda.search.clients.IncidentSearchClient;
import io.camunda.search.clients.ProcessDefinitionSearchClient;
import io.camunda.search.clients.ProcessInstanceSearchClient;
import io.camunda.search.clients.UserSearchClient;
import io.camunda.search.clients.UserTaskSearchClient;
import io.camunda.search.clients.VariableSearchClient;
import io.camunda.search.entities.AuthorizationEntity;
import io.camunda.search.entities.DecisionDefinitionEntity;
import io.camunda.search.entities.DecisionInstanceEntity;
import io.camunda.search.entities.DecisionRequirementsEntity;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.FormEntity;
import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.entities.UserEntity;
import io.camunda.search.entities.UserTaskEntity;
import io.camunda.search.entities.VariableEntity;
import io.camunda.search.query.AuthorizationQuery;
import io.camunda.search.query.DecisionDefinitionQuery;
import io.camunda.search.query.DecisionInstanceQuery;
import io.camunda.search.query.DecisionRequirementsQuery;
import io.camunda.search.query.FlowNodeInstanceQuery;
import io.camunda.search.query.FormQuery;
import io.camunda.search.query.IncidentQuery;
import io.camunda.search.query.ProcessDefinitionQuery;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.UserQuery;
import io.camunda.search.query.UserTaskQuery;
import io.camunda.search.query.VariableQuery;
import io.camunda.security.auth.SecurityContext;
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
        VariableSearchClient {

  private static final Logger LOG = LoggerFactory.getLogger(RdbmsSearchClient.class);

  private final RdbmsService rdbmsService;

  public RdbmsSearchClient(final RdbmsService rdbmsService) {
    this.rdbmsService = rdbmsService;
  }

  @Override
  public SearchQueryResult<ProcessInstanceEntity> searchProcessInstances(
      final ProcessInstanceQuery query, final SecurityContext securityContext) {
    LOG.debug("[RDBMS Search Client] Search for processInstance: {}", query);

    final var searchResult =
        rdbmsService
            .getProcessInstanceReader()
            .search(
                ProcessInstanceDbQuery.of(
                    b -> b.filter(query.filter()).sort(query.sort()).page(query.page())));

    return new SearchQueryResult<>(searchResult.total(), searchResult.hits(), null);
  }

  @Override
  public SearchQueryResult<AuthorizationEntity> searchAuthorizations(
      final AuthorizationQuery filter, final SecurityContext securityContext) {
    return null;
  }

  @Override
  public SearchQueryResult<DecisionDefinitionEntity> searchDecisionDefinitions(
      final DecisionDefinitionQuery filter, final SecurityContext securityContext) {
    return null;
  }

  @Override
  public SearchQueryResult<DecisionInstanceEntity> searchDecisionInstances(
      final DecisionInstanceQuery filter, final SecurityContext securityContext) {
    return null;
  }

  @Override
  public SearchQueryResult<DecisionRequirementsEntity> searchDecisionRequirements(
      final DecisionRequirementsQuery filter, final SecurityContext securityContext) {
    return null;
  }

  @Override
  public SearchQueryResult<FlowNodeInstanceEntity> searchFlowNodeInstances(
      final FlowNodeInstanceQuery query, final SecurityContext securityContext) {
    final var searchResult =
        rdbmsService
            .getFlowNodeInstanceReader()
            .search(
                FlowNodeInstanceDbQuery.of(
                    b -> b.filter(query.filter()).sort(query.sort()).page(query.page())));

    return new SearchQueryResult<>(searchResult.total(), searchResult.hits(), null);
  }

  @Override
  public SearchQueryResult<FormEntity> searchForms(
      final FormQuery filter, final SecurityContext securityContext) {
    return null;
  }

  @Override
  public SearchQueryResult<IncidentEntity> searchIncidents(
      final IncidentQuery filter, final SecurityContext securityContext) {
    return null;
  }

  @Override
  public SearchQueryResult<UserEntity> searchUsers(
      final UserQuery filter, final SecurityContext securityContext) {
    return null;
  }

  @Override
  public SearchQueryResult<UserTaskEntity> searchUserTasks(
      final UserTaskQuery filter, final SecurityContext securityContext) {
    return null;
  }

  @Override
  public SearchQueryResult<VariableEntity> searchVariables(
      final VariableQuery filter, final SecurityContext securityContext) {
    return null;
  }

  @Override
  public SearchQueryResult<ProcessDefinitionEntity> searchProcessDefinitions(
      final ProcessDefinitionQuery filter, final SecurityContext securityContext) {
    return null;
  }
}
