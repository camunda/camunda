/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.rdbms;

import io.camunda.search.clients.AuthorizationSearchClient;
import io.camunda.search.clients.DecisionDefinitionSearchClient;
import io.camunda.search.clients.DecisionInstanceSearchClient;
import io.camunda.search.clients.DecisionRequirementSearchClient;
import io.camunda.search.clients.FlowNodeInstanceSearchClient;
import io.camunda.search.clients.FormSearchClient;
import io.camunda.search.clients.IncidentSearchClient;
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
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.UserQuery;
import io.camunda.search.query.UserTaskQuery;
import io.camunda.search.query.VariableQuery;
import io.camunda.search.security.auth.Authentication;
import java.util.List;

public class RdbmsNoopSearchClient
    implements AuthorizationSearchClient,
        DecisionDefinitionSearchClient,
        DecisionInstanceSearchClient,
        DecisionRequirementSearchClient,
        FlowNodeInstanceSearchClient,
        FormSearchClient,
        IncidentSearchClient,
        UserTaskSearchClient,
        UserSearchClient,
        VariableSearchClient {

  public RdbmsNoopSearchClient() {}

  @Override
  public SearchQueryResult<AuthorizationEntity> searchAuthorizations(
      final AuthorizationQuery filter, final Authentication authentication) {
    return new SearchQueryResult<>(0L, List.of(), null);
  }

  @Override
  public SearchQueryResult<DecisionDefinitionEntity> searchDecisionDefinitions(
      final DecisionDefinitionQuery filter, final Authentication authentication) {
    return new SearchQueryResult<>(0L, List.of(), null);
  }

  @Override
  public SearchQueryResult<DecisionInstanceEntity> searchDecisionInstances(
      final DecisionInstanceQuery filter, final Authentication authentication) {
    return new SearchQueryResult<>(0L, List.of(), null);
  }

  @Override
  public SearchQueryResult<DecisionRequirementsEntity> searchDecisionRequirements(
      final DecisionRequirementsQuery filter, final Authentication authentication) {
    return new SearchQueryResult<>(0L, List.of(), null);
  }

  @Override
  public SearchQueryResult<FlowNodeInstanceEntity> searchFlowNodeInstances(
      final FlowNodeInstanceQuery filter, final Authentication authentication) {
    return new SearchQueryResult<>(0L, List.of(), null);
  }

  @Override
  public SearchQueryResult<FormEntity> searchForms(
      final FormQuery filter, final Authentication authentication) {
    return new SearchQueryResult<>(0L, List.of(), null);
  }

  @Override
  public SearchQueryResult<IncidentEntity> searchIncidents(
      final IncidentQuery filter, final Authentication authentication) {
    return new SearchQueryResult<>(0L, List.of(), null);
  }

  @Override
  public SearchQueryResult<UserEntity> searchUsers(
      final UserQuery filter, final Authentication authentication) {
    return new SearchQueryResult<>(0L, List.of(), null);
  }

  @Override
  public SearchQueryResult<UserTaskEntity> searchUserTasks(
      final UserTaskQuery filter, final Authentication authentication) {
    return new SearchQueryResult<>(0L, List.of(), null);
  }

  @Override
  public SearchQueryResult<VariableEntity> searchVariables(
      final VariableQuery filter, final Authentication authentication) {
    return new SearchQueryResult<>(0L, List.of(), null);
  }
}
