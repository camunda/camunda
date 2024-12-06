/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients;

import io.camunda.search.clients.auth.DocumentAuthorizationQueryStrategy;
import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.transformers.ServiceTransformers;
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
import io.camunda.search.query.TypedSearchQuery;
import io.camunda.search.query.UserQuery;
import io.camunda.search.query.UserTaskQuery;
import io.camunda.search.query.VariableQuery;
import io.camunda.security.auth.SecurityContext;
import io.camunda.webapps.schema.descriptors.usermanagement.index.RoleIndex;
import io.camunda.webapps.schema.entities.usermanagement.EntityJoinRelation.IdentityJoinRelationshipType;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
        VariableSearchClient,
        MappingSearchClient,
        GroupSearchClient {

  private final DocumentBasedSearchClient searchClient;
  private final ServiceTransformers transformers;
  private final SecurityContext securityContext;

  public SearchClients(final DocumentBasedSearchClient searchClient, final String prefix) {
    this(
        searchClient,
        ServiceTransformers.newInstance(prefix),
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

  private <T> SearchQueryResult<T> execute(
      final TypedSearchQuery<?, ?> query, final Class<?> documentClass) {
    final var executor =
        new SearchClientBasedQueryExecutor(
            searchClient,
            transformers,
            new DocumentAuthorizationQueryStrategy(this),
            securityContext);
    return executor.search(query, documentClass);
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
    return executor.search(
        filter, io.camunda.webapps.schema.entities.usermanagement.AuthorizationEntity.class);
  }

  @Override
  public List<AuthorizationEntity> findAllAuthorizations(final AuthorizationQuery filter) {
    final var executor =
        new SearchClientBasedQueryExecutor(
            searchClient,
            transformers,
            new DocumentAuthorizationQueryStrategy(this),
            securityContext);
    return executor.findAll(
        filter, io.camunda.webapps.schema.entities.usermanagement.AuthorizationEntity.class);
  }

  @Override
  public SearchClients withSecurityContext(final SecurityContext securityContext) {
    return new SearchClients(searchClient, transformers, securityContext);
  }

  @Override
  public SearchQueryResult<MappingEntity> searchMappings(final MappingQuery filter) {
    final var executor =
        new SearchClientBasedQueryExecutor(
            searchClient,
            transformers,
            new DocumentAuthorizationQueryStrategy(this),
            securityContext);
    return executor.search(
        filter, io.camunda.webapps.schema.entities.usermanagement.MappingEntity.class);
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
    return executor.search(
        filter,
        io.camunda.webapps.schema.entities.operate.dmn.definition.DecisionDefinitionEntity.class);
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
    return executor.search(
        filter, io.camunda.webapps.schema.entities.operate.dmn.DecisionInstanceEntity.class);
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
    return executor.search(
        filter,
        io.camunda.webapps.schema.entities.operate.dmn.definition.DecisionRequirementsEntity.class);
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
    return executor.search(
        filter, io.camunda.webapps.schema.entities.operate.FlowNodeInstanceEntity.class);
  }

  @Override
  public SearchQueryResult<FormEntity> searchForms(final FormQuery filter) {
    final var executor =
        new SearchClientBasedQueryExecutor(
            searchClient,
            transformers,
            new DocumentAuthorizationQueryStrategy(this),
            securityContext);
    return executor.search(filter, io.camunda.webapps.schema.entities.tasklist.FormEntity.class);
  }

  @Override
  public SearchQueryResult<IncidentEntity> searchIncidents(final IncidentQuery filter) {
    final var executor =
        new SearchClientBasedQueryExecutor(
            searchClient,
            transformers,
            new DocumentAuthorizationQueryStrategy(this),
            securityContext);
    return executor.search(filter, io.camunda.webapps.schema.entities.operate.IncidentEntity.class);
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
    return executor.search(filter, io.camunda.webapps.schema.entities.operate.ProcessEntity.class);
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
    return executor.search(
        filter,
        io.camunda.webapps.schema.entities.operate.listview.ProcessInstanceForListViewEntity.class);
  }

  @Override
  public SearchQueryResult<RoleEntity> searchRoles(final RoleQuery filter) {
    final var executor =
        new SearchClientBasedQueryExecutor(
            searchClient,
            transformers,
            new DocumentAuthorizationQueryStrategy(this),
            securityContext);
    return executor.search(
        filter, io.camunda.webapps.schema.entities.usermanagement.RoleEntity.class);
  }

  @Override
  public SearchQueryResult<TenantEntity> searchTenants(final TenantQuery filter) {
    return new SearchClientBasedQueryExecutor(
            searchClient,
            transformers,
            new DocumentAuthorizationQueryStrategy(this),
            securityContext)
        .search(filter, io.camunda.webapps.schema.entities.usermanagement.TenantEntity.class);
  }

  @Override
  public SearchQueryResult<GroupEntity> searchGroups(final GroupQuery filter) {
    return new SearchClientBasedQueryExecutor(
            searchClient,
            transformers,
            new DocumentAuthorizationQueryStrategy(this),
            securityContext)
        .search(filter, GroupEntity.class);
  }

  @Override
  public SearchQueryResult<UserEntity> searchUsers(final UserQuery query) {
    final var roleKey = query.filter().roleKey();
    var effectiveUserQuery = query;
    if (roleKey != null) {
      final Set<Long> userKeys =
          searchClient
              .findAll(
                  SearchQueryRequest.of(
                      builder ->
                          builder
                              .index(List.of())
                              .query(
                                  SearchQuery.of(
                                      q ->
                                          q.term(
                                              t ->
                                                  t.field(RoleIndex.JOIN)
                                                      .value(
                                                          IdentityJoinRelationshipType.MEMBER
                                                              .getType()))))),
                  io.camunda.webapps.schema.entities.usermanagement.RoleEntity.class)
              .stream()
              .map(io.camunda.webapps.schema.entities.usermanagement.RoleEntity::getMemberKey)
              .filter(
                  userKey ->
                      query.filter().keys() == null
                          || query.filter().keys().isEmpty()
                          || query.filter().keys().contains(userKey))
              .collect(Collectors.toSet());
      effectiveUserQuery =
          query.toBuilder()
              .filter(query.filter().toBuilder().roleKey(null).keys(userKeys).build())
              .build();
    }
    return execute(
        effectiveUserQuery, io.camunda.webapps.schema.entities.usermanagement.UserEntity.class);
  }

  @Override
  public SearchQueryResult<UserTaskEntity> searchUserTasks(final UserTaskQuery filter) {
    final var executor =
        new SearchClientBasedQueryExecutor(
            searchClient,
            transformers,
            new DocumentAuthorizationQueryStrategy(this),
            securityContext);
    return executor.search(filter, io.camunda.webapps.schema.entities.tasklist.TaskEntity.class);
  }

  @Override
  public SearchQueryResult<VariableEntity> searchVariables(final VariableQuery filter) {
    final var executor =
        new SearchClientBasedQueryExecutor(
            searchClient,
            transformers,
            new DocumentAuthorizationQueryStrategy(this),
            securityContext);
    return executor.search(filter, io.camunda.webapps.schema.entities.operate.VariableEntity.class);
  }
}
