/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients;

import io.camunda.search.clients.auth.DocumentAuthorizationQueryStrategy;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.entities.AuthorizationEntity;
import io.camunda.search.entities.DecisionDefinitionEntity;
import io.camunda.search.entities.DecisionInstanceEntity;
import io.camunda.search.entities.DecisionRequirementsEntity;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.FormEntity;
import io.camunda.search.entities.IncidentEntity;
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
import io.camunda.search.query.IncidentQuery;
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

public class SearchClients
    implements AuthorizationSearchClient,
        DecisionDefinitionSearchClient,
        DecisionInstanceSearchClient,
        DecisionRequirementSearchClient,
        FlowNodeInstanceSearchClient,
        FormSearchClient,
        IncidentSearchClient,
        ProcessDefinitionSearchClient,
        ProcessInstanceSearchClient,
        RoleSearchClient,
        TenantSearchClient,
        UserTaskSearchClient,
        UserSearchClient,
        VariableSearchClient {

  private final DocumentBasedSearchClient searchClient;
  private final ServiceTransformers transformers;
  private final SecurityContext securityContext;

  public SearchClients(
      final DocumentBasedSearchClient searchClient, final boolean isCamundaExporterEnabled) {
    this(
        searchClient,
        ServiceTransformers.newInstance(isCamundaExporterEnabled),
        SecurityContext.withoutAuthentication());
  }

  private SearchClients(
      final DocumentBasedSearchClient searchClient,
      final ServiceTransformers transformers,
      final SecurityContext securityContext) {
    this.searchClient = searchClient;
    this.transformers = transformers;
    this.securityContext = securityContext;
  }

  @Override
  public SearchQueryResult<AuthorizationEntity> searchAuthorizations(
      final AuthorizationQuery filter) {
    final var executor =
        new SearchClientBasedQueryExecutor(
            searchClient,
            transformers,
            new DocumentAuthorizationQueryStrategy(this),
            securityContext);
    return executor.search(filter, AuthorizationEntity.class);
  }

  @Override
  public List<AuthorizationEntity> findAllAuthorizations(final AuthorizationQuery filter) {
    final var executor =
        new SearchClientBasedQueryExecutor(
            searchClient,
            transformers,
            new DocumentAuthorizationQueryStrategy(this),
            securityContext);
    return executor.findAll(filter, AuthorizationEntity.class);
  }

  @Override
  public SearchClients withSecurityContext(final SecurityContext securityContext) {
    return new SearchClients(searchClient, transformers, securityContext);
  }

  @Override
  public SearchQueryResult<DecisionDefinitionEntity> searchDecisionDefinitions(
      final DecisionDefinitionQuery filter) {
    final var executor =
        new SearchClientBasedQueryExecutor(
            searchClient,
            transformers,
            new DocumentAuthorizationQueryStrategy(this),
            securityContext);
    return executor.search(filter, DecisionDefinitionEntity.class);
  }

  @Override
  public SearchQueryResult<DecisionInstanceEntity> searchDecisionInstances(
      final DecisionInstanceQuery filter) {
    final var executor =
        new SearchClientBasedQueryExecutor(
            searchClient,
            transformers,
            new DocumentAuthorizationQueryStrategy(this),
            securityContext);
    return executor.search(filter, DecisionInstanceEntity.class);
  }

  @Override
  public SearchQueryResult<DecisionRequirementsEntity> searchDecisionRequirements(
      final DecisionRequirementsQuery filter) {
    final var executor =
        new SearchClientBasedQueryExecutor(
            searchClient,
            transformers,
            new DocumentAuthorizationQueryStrategy(this),
            securityContext);
    return executor.search(filter, DecisionRequirementsEntity.class);
  }

  @Override
  public SearchQueryResult<FlowNodeInstanceEntity> searchFlowNodeInstances(
      final FlowNodeInstanceQuery filter) {
    final var executor =
        new SearchClientBasedQueryExecutor(
            searchClient,
            transformers,
            new DocumentAuthorizationQueryStrategy(this),
            securityContext);
    return executor.search(filter, FlowNodeInstanceEntity.class);
  }

  @Override
  public SearchQueryResult<FormEntity> searchForms(final FormQuery filter) {
    final var executor =
        new SearchClientBasedQueryExecutor(
            searchClient,
            transformers,
            new DocumentAuthorizationQueryStrategy(this),
            securityContext);
    return executor.search(filter, FormEntity.class);
  }

  @Override
  public SearchQueryResult<IncidentEntity> searchIncidents(final IncidentQuery filter) {
    final var executor =
        new SearchClientBasedQueryExecutor(
            searchClient,
            transformers,
            new DocumentAuthorizationQueryStrategy(this),
            securityContext);
    return executor.search(filter, IncidentEntity.class);
  }

  @Override
  public SearchQueryResult<ProcessDefinitionEntity> searchProcessDefinitions(
      final ProcessDefinitionQuery filter) {
    final var executor =
        new SearchClientBasedQueryExecutor(
            searchClient,
            transformers,
            new DocumentAuthorizationQueryStrategy(this),
            securityContext);
    return executor.search(filter, ProcessDefinitionEntity.class);
  }

  @Override
  public SearchQueryResult<ProcessInstanceEntity> searchProcessInstances(
      final ProcessInstanceQuery filter) {
    final var executor =
        new SearchClientBasedQueryExecutor(
            searchClient,
            transformers,
            new DocumentAuthorizationQueryStrategy(this),
            securityContext);
    return executor.search(filter, ProcessInstanceEntity.class);
  }

  @Override
  public SearchQueryResult<RoleEntity> searchRoles(final RoleQuery filter) {
    final var executor =
        new SearchClientBasedQueryExecutor(
            searchClient,
            transformers,
            new DocumentAuthorizationQueryStrategy(this),
            securityContext);
    return executor.search(filter, RoleEntity.class);
  }

  @Override
  public SearchQueryResult<TenantEntity> searchTenants(final TenantQuery filter) {
    return new SearchClientBasedQueryExecutor(
            searchClient,
            transformers,
            new DocumentAuthorizationQueryStrategy(this),
            securityContext)
        .search(filter, TenantEntity.class);
  }

  @Override
  public SearchQueryResult<UserEntity> searchUsers(final UserQuery filter) {
    final var executor =
        new SearchClientBasedQueryExecutor(
            searchClient,
            transformers,
            new DocumentAuthorizationQueryStrategy(this),
            securityContext);
    return executor.search(filter, UserEntity.class);
  }

  @Override
  public SearchQueryResult<UserTaskEntity> searchUserTasks(final UserTaskQuery filter) {
    final var executor =
        new SearchClientBasedQueryExecutor(
            searchClient,
            transformers,
            new DocumentAuthorizationQueryStrategy(this),
            securityContext);
    return executor.search(filter, UserTaskEntity.class);
  }

  @Override
  public SearchQueryResult<VariableEntity> searchVariables(final VariableQuery filter) {
    final var executor =
        new SearchClientBasedQueryExecutor(
            searchClient,
            transformers,
            new DocumentAuthorizationQueryStrategy(this),
            securityContext);
    return executor.search(filter, VariableEntity.class);
  }
}
