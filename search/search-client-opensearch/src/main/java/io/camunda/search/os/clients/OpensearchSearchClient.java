/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.os.clients;

import io.camunda.search.DocumentCamundaSearchClient;
import io.camunda.search.ProcessDefinitionSearchClient;
import io.camunda.search.SearchClientBasedQueryExecutor;
import io.camunda.search.clients.AuthorizationSearchClient;
import io.camunda.search.clients.DecisionDefinitionSearchClient;
import io.camunda.search.clients.DecisionInstanceSearchClient;
import io.camunda.search.clients.DecisionRequirementSearchClient;
import io.camunda.search.clients.FlowNodeInstanceSearchClient;
import io.camunda.search.clients.FormSearchClient;
import io.camunda.search.clients.IncidentSearchClient;
import io.camunda.search.clients.ProcessInstanceSearchClient;
import io.camunda.search.clients.UserSearchClient;
import io.camunda.search.clients.UserTaskSearchClient;
import io.camunda.search.clients.VariableSearchClient;
import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.core.SearchQueryResponse;
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
import io.camunda.search.exception.SearchQueryExecutionException;
import io.camunda.search.os.transformers.OpensearchTransformers;
import io.camunda.search.os.transformers.search.SearchResponseTransformer;
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
import io.camunda.search.security.auth.Authentication;
import io.camunda.search.transformers.SearchTransfomer;
import io.camunda.search.transformers.ServiceTransformers;
import java.io.IOException;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;

public class OpensearchSearchClient
    implements DocumentCamundaSearchClient,
        AuthorizationSearchClient,
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

  private final OpenSearchClient client;
  private final OpensearchTransformers transformers;

  public OpensearchSearchClient(final OpenSearchClient client) {
    this(client, new OpensearchTransformers());
  }

  public OpensearchSearchClient(
      final OpenSearchClient client, final OpensearchTransformers transformers) {
    this.client = client;
    this.transformers = transformers;
  }

  @Override
  public <T> SearchQueryResponse<T> search(
      final SearchQueryRequest searchRequest, final Class<T> documentClass) {
    try {
      final var requestTransformer = getSearchRequestTransformer();
      final var request = requestTransformer.apply(searchRequest);
      final SearchResponse<T> rawSearchResponse = client.search(request, documentClass);
      final SearchResponseTransformer<T> searchResponseTransformer = getSearchResponseTransformer();
      return searchResponseTransformer.apply(rawSearchResponse);
    } catch (final IOException | OpenSearchException e) {
      throw new SearchQueryExecutionException("Failed to execute search query", e);
    }
  }

  @Override
  public SearchQueryResult<AuthorizationEntity> searchAuthorizations(
      final AuthorizationQuery filter, final Authentication authentication) {
    final var executor =
        new SearchClientBasedQueryExecutor(this, ServiceTransformers.newInstance(), authentication);
    return executor.search(filter, AuthorizationEntity.class);
  }

  @Override
  public SearchQueryResult<DecisionDefinitionEntity> searchDecisionDefinitions(
      final DecisionDefinitionQuery filter, final Authentication authentication) {
    final var executor =
        new SearchClientBasedQueryExecutor(this, ServiceTransformers.newInstance(), authentication);
    return executor.search(filter, DecisionDefinitionEntity.class);
  }

  @Override
  public SearchQueryResult<DecisionInstanceEntity> searchDecisionInstances(
      final DecisionInstanceQuery filter, final Authentication authentication) {
    final var executor =
        new SearchClientBasedQueryExecutor(this, ServiceTransformers.newInstance(), authentication);
    return executor.search(filter, DecisionInstanceEntity.class);
  }

  @Override
  public SearchQueryResult<DecisionRequirementsEntity> searchDecisionRequirements(
      final DecisionRequirementsQuery filter, final Authentication authentication) {
    final var executor =
        new SearchClientBasedQueryExecutor(this, ServiceTransformers.newInstance(), authentication);
    return executor.search(filter, DecisionRequirementsEntity.class);
  }

  @Override
  public SearchQueryResult<FlowNodeInstanceEntity> searchFlowNodeInstances(
      final FlowNodeInstanceQuery filter, final Authentication authentication) {
    final var executor =
        new SearchClientBasedQueryExecutor(this, ServiceTransformers.newInstance(), authentication);
    return executor.search(filter, FlowNodeInstanceEntity.class);
  }

  @Override
  public SearchQueryResult<FormEntity> searchForms(
      final FormQuery filter, final Authentication authentication) {
    final var executor =
        new SearchClientBasedQueryExecutor(this, ServiceTransformers.newInstance(), authentication);
    return executor.search(filter, FormEntity.class);
  }

  @Override
  public SearchQueryResult<IncidentEntity> searchIncidents(
      final IncidentQuery filter, final Authentication authentication) {
    final var executor =
        new SearchClientBasedQueryExecutor(this, ServiceTransformers.newInstance(), authentication);
    return executor.search(filter, IncidentEntity.class);
  }

  @Override
  public SearchQueryResult<ProcessDefinitionEntity> searchProcessDefinitions(
      final ProcessDefinitionQuery filter, final Authentication authentication) {
    final var executor =
        new SearchClientBasedQueryExecutor(this, ServiceTransformers.newInstance(), authentication);
    return executor.search(filter, ProcessDefinitionEntity.class);
  }

  @Override
  public SearchQueryResult<ProcessInstanceEntity> searchProcessInstances(
      final ProcessInstanceQuery filter, final Authentication authentication) {
    final var executor =
        new SearchClientBasedQueryExecutor(this, ServiceTransformers.newInstance(), authentication);
    return executor.search(filter, ProcessInstanceEntity.class);
  }

  @Override
  public SearchQueryResult<UserEntity> searchUsers(
      final UserQuery filter, final Authentication authentication) {
    final var executor =
        new SearchClientBasedQueryExecutor(this, ServiceTransformers.newInstance(), authentication);
    return executor.search(filter, UserEntity.class);
  }

  @Override
  public SearchQueryResult<UserTaskEntity> searchUserTasks(
      final UserTaskQuery filter, final Authentication authentication) {
    final var executor =
        new SearchClientBasedQueryExecutor(this, ServiceTransformers.newInstance(), authentication);
    return executor.search(filter, UserTaskEntity.class);
  }

  @Override
  public SearchQueryResult<VariableEntity> searchVariables(
      final VariableQuery filter, final Authentication authentication) {
    final var executor =
        new SearchClientBasedQueryExecutor(this, ServiceTransformers.newInstance(), authentication);
    return executor.search(filter, VariableEntity.class);
  }

  private SearchTransfomer<SearchQueryRequest, SearchRequest> getSearchRequestTransformer() {
    return transformers.getTransformer(SearchQueryRequest.class);
  }

  private <T> SearchResponseTransformer<T> getSearchResponseTransformer() {
    return new SearchResponseTransformer<>(transformers);
  }

  @Override
  public void close() throws Exception {
    if (client != null) {
      try {
        client._transport().close();
      } catch (final IOException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
