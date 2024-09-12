/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.clients;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import io.camunda.search.DocumentCamundaSearchClient;
import io.camunda.search.SearchClientBasedQueryExecutor;
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
import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.core.SearchQueryResponse;
import io.camunda.search.es.transformers.ElasticsearchTransformers;
import io.camunda.search.es.transformers.search.SearchResponseTransformer;
import io.camunda.search.transformers.SearchTransfomer;
import io.camunda.search.transformers.ServiceTransformers;
import io.camunda.service.entities.AuthorizationEntity;
import io.camunda.service.entities.DecisionDefinitionEntity;
import io.camunda.service.entities.DecisionInstanceEntity;
import io.camunda.service.entities.DecisionRequirementsEntity;
import io.camunda.service.entities.FlowNodeInstanceEntity;
import io.camunda.service.entities.FormEntity;
import io.camunda.service.entities.IncidentEntity;
import io.camunda.service.entities.UserEntity;
import io.camunda.service.entities.UserTaskEntity;
import io.camunda.service.entities.VariableEntity;
import io.camunda.service.search.query.AuthorizationQuery;
import io.camunda.service.search.query.DecisionDefinitionQuery;
import io.camunda.service.search.query.DecisionInstanceQuery;
import io.camunda.service.search.query.DecisionRequirementsQuery;
import io.camunda.service.search.query.FlowNodeInstanceQuery;
import io.camunda.service.search.query.FormQuery;
import io.camunda.service.search.query.IncidentQuery;
import io.camunda.service.search.query.SearchQueryResult;
import io.camunda.service.search.query.UserQuery;
import io.camunda.service.search.query.UserTaskQuery;
import io.camunda.service.search.query.VariableQuery;
import io.camunda.service.security.auth.Authentication;
import io.camunda.zeebe.util.Either;
import java.io.IOException;

public class ElasticsearchSearchClient implements DocumentCamundaSearchClient,
    AuthorizationSearchClient,
    DecisionDefinitionSearchClient,
    DecisionInstanceSearchClient,
    DecisionRequirementSearchClient,
    FlowNodeInstanceSearchClient,
    FormSearchClient,
    IncidentSearchClient,
    UserTaskSearchClient,
    UserSearchClient,
    VariableSearchClient {

  protected final ElasticsearchClient client;
  protected final ElasticsearchTransformers transformers;

  public ElasticsearchSearchClient(final ElasticsearchClient client) {
    this(client, new ElasticsearchTransformers());
  }

  public ElasticsearchSearchClient(
      final ElasticsearchClient client, final ElasticsearchTransformers transformers) {
    this.client = client;
    this.transformers = transformers;
  }

  @Override
  public <T> Either<Exception, SearchQueryResponse<T>> search(
      final SearchQueryRequest searchRequest, final Class<T> documentClass) {
    try {
      final var requestTransformer = getSearchRequestTransformer();
      final var request = requestTransformer.apply(searchRequest);
      final SearchResponse<T> rawSearchResponse = client.search(request, documentClass);
      final SearchResponseTransformer<T> searchResponseTransformer = getSearchResponseTransformer();
      final SearchQueryResponse<T> response = searchResponseTransformer.apply(rawSearchResponse);
      return Either.right(response);
    } catch (final IOException | ElasticsearchException ioe) {
      return Either.left(ioe);
    }
  }

  @Override
  public Either<Exception, SearchQueryResult<AuthorizationEntity>> searchAuthorizations(
      final AuthorizationQuery filter, final Authentication authentication) {
    final var executor = new SearchClientBasedQueryExecutor(this, ServiceTransformers.newInstance(),
        authentication);
    return executor.search(filter, AuthorizationEntity.class);
  }

  @Override
  public Either<Exception, SearchQueryResult<DecisionDefinitionEntity>> searchDecisionDefinitions(
      final DecisionDefinitionQuery filter, final Authentication authentication) {
    final var executor = new SearchClientBasedQueryExecutor(this, ServiceTransformers.newInstance(),
        authentication);
    return executor.search(filter, DecisionDefinitionEntity.class);
  }

  @Override
  public Either<Exception, SearchQueryResult<DecisionInstanceEntity>> searchDecisionInstances(
      final DecisionInstanceQuery filter, final Authentication authentication) {
    final var executor = new SearchClientBasedQueryExecutor(this, ServiceTransformers.newInstance(),
        authentication);
    return executor.search(filter, DecisionInstanceEntity.class);
  }

  @Override
  public Either<Exception, SearchQueryResult<DecisionRequirementsEntity>> searchDecisionRequirements(
      final DecisionRequirementsQuery filter, final Authentication authentication) {
    final var executor = new SearchClientBasedQueryExecutor(this, ServiceTransformers.newInstance(),
        authentication);
    return executor.search(filter, DecisionRequirementsEntity.class);
  }

  @Override
  public Either<Exception, SearchQueryResult<FlowNodeInstanceEntity>> searchFlowNodeInstances(
      final FlowNodeInstanceQuery filter, final Authentication authentication) {
    final var executor = new SearchClientBasedQueryExecutor(this, ServiceTransformers.newInstance(),
        authentication);
    return executor.search(filter, FlowNodeInstanceEntity.class);
  }

  @Override
  public Either<Exception, SearchQueryResult<FormEntity>> searchForms(final FormQuery filter,
      final Authentication authentication) {
    final var executor = new SearchClientBasedQueryExecutor(this, ServiceTransformers.newInstance(),
        authentication);
    return executor.search(filter, FormEntity.class);
  }

  @Override
  public Either<Exception, SearchQueryResult<IncidentEntity>> searchIncidents(
      final IncidentQuery filter, final Authentication authentication) {
    final var executor = new SearchClientBasedQueryExecutor(this, ServiceTransformers.newInstance(),
        authentication);
    return executor.search(filter, IncidentEntity.class);
  }

  @Override
  public Either<Exception, SearchQueryResult<UserEntity>> searchUsers(final UserQuery filter,
      final Authentication authentication) {
    final var executor = new SearchClientBasedQueryExecutor(this, ServiceTransformers.newInstance(),
        authentication);
    return executor.search(filter, UserEntity.class);
  }

  @Override
  public Either<Exception, SearchQueryResult<UserTaskEntity>> searchUserTasks(
      final UserTaskQuery filter, final Authentication authentication) {
    final var executor = new SearchClientBasedQueryExecutor(this, ServiceTransformers.newInstance(),
        authentication);
    return executor.search(filter, UserTaskEntity.class);
  }

  @Override
  public Either<Exception, SearchQueryResult<VariableEntity>> searchVariables(
      final VariableQuery filter, final Authentication authentication) {
    final var executor = new SearchClientBasedQueryExecutor(this, ServiceTransformers.newInstance(),
        authentication);
    return executor.search(filter, VariableEntity.class);
  }

  protected SearchTransfomer<SearchQueryRequest, SearchRequest> getSearchRequestTransformer() {
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
