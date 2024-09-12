/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.util;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.DocumentCamundaSearchClient;
import io.camunda.search.clients.CamundaSearchClient;
import io.camunda.search.clients.ProcessInstanceSearchClient;
import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.core.SearchQueryResponse;
import io.camunda.service.entities.AuthorizationEntity;
import io.camunda.service.entities.DecisionDefinitionEntity;
import io.camunda.service.entities.DecisionInstanceEntity;
import io.camunda.service.entities.DecisionRequirementsEntity;
import io.camunda.service.entities.FlowNodeInstanceEntity;
import io.camunda.service.entities.IncidentEntity;
import io.camunda.service.entities.ProcessInstanceEntity;
import io.camunda.service.entities.UserEntity;
import io.camunda.service.entities.UserTaskEntity;
import io.camunda.service.entities.VariableEntity;
import io.camunda.service.search.query.AuthorizationQuery;
import io.camunda.service.search.query.DecisionDefinitionQuery;
import io.camunda.service.search.query.DecisionInstanceQuery;
import io.camunda.service.search.query.DecisionRequirementsQuery;
import io.camunda.service.search.query.FlowNodeInstanceQuery;
import io.camunda.service.search.query.IncidentQuery;
import io.camunda.service.search.query.ProcessInstanceQuery;
import io.camunda.service.search.query.SearchQueryResult;
import io.camunda.service.search.query.UserQuery;
import io.camunda.service.search.query.UserTaskQuery;
import io.camunda.service.search.query.VariableQuery;
import io.camunda.service.security.auth.Authentication;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StubbedCamundaSearchClient implements DocumentCamundaSearchClient,
    CamundaSearchClient, ProcessInstanceSearchClient {

  private final Map<Class<?>, SearchRequestHandler<?>> searchRequestHandlerMap = new HashMap<>();
  private final List<SearchQueryRequest> searchRequests = new ArrayList<>();

  public StubbedCamundaSearchClient() {
  }

  @Override
  public <T> Either<Exception, SearchQueryResponse<T>> search(
      final SearchQueryRequest searchRequest, final Class<T> documentClass) {
    searchRequests.add(searchRequest);

    try {
      final SearchQueryResponse response =
          searchRequestHandlerMap.get(documentClass).handle(searchRequest);
      return Either.right(response);
    } catch (final Exception e) {
      return Either.left(e);
    }
  }

  public SearchQueryRequest getSingleSearchRequest() {
    assertThat(searchRequests).hasSize(1);
    return searchRequests.get(0);
  }

  public List<SearchQueryRequest> getSearchRequests() {
    return searchRequests;
  }

  public <DocumentT> void registerHandler(
      final SearchRequestHandler<DocumentT> searchRequestHandler,
      final Class<DocumentT> documentClass) {
    searchRequestHandlerMap.put(documentClass, searchRequestHandler);
  }

  @Override
  public void close() throws Exception {
    // noop
  }

  @Override
  public Either<Exception, SearchQueryResult<AuthorizationEntity>> searchAuthorizations(
      final AuthorizationQuery filter, final Authentication authentication) {
    return null;
  }

  @Override
  public Either<Exception, SearchQueryResult<DecisionDefinitionEntity>> searchDecisionDefinitions(
      final DecisionDefinitionQuery filter, final Authentication authentication) {
    return null;
  }

  @Override
  public Either<Exception, SearchQueryResult<DecisionInstanceEntity>> searchDecisionInstances(
      final DecisionInstanceQuery filter, final Authentication authentication) {
    return null;
  }

  @Override
  public Either<Exception, SearchQueryResult<DecisionRequirementsEntity>> searchDecisionRequirements(final DecisionRequirementsQuery filter, final Authentication authentication) {
    return null;
  }

  @Override
  public Either<Exception, SearchQueryResult<FlowNodeInstanceEntity>> searchFlowNodeInstances(
      final FlowNodeInstanceQuery filter, final Authentication authentication) {
    return null;
  }

  @Override
  public Either<Exception, SearchQueryResult<IncidentEntity>> searchIncidents(final IncidentQuery filter, final Authentication authentication) {
    return null;
  }

  @Override
  public Either<Exception, SearchQueryResult<UserEntity>> searchUsers(final UserQuery filter,
      final Authentication authentication) {
    return null;
  }

  @Override
  public Either<Exception, SearchQueryResult<UserTaskEntity>> searchUserTasks(
      final UserTaskQuery filter, final Authentication authentication) {
    return null;
  }

  @Override
  public Either<Exception, SearchQueryResult<VariableEntity>> searchVariables(
      final VariableQuery filter, final Authentication authentication) {
    return null;
  }

  @Override
  public Either<Exception, SearchQueryResult<ProcessInstanceEntity>> searchProcessInstances(
      final ProcessInstanceQuery filter, final Authentication authentication) {
    return null;
  }

  public interface RequestStub<DocumentT> extends SearchRequestHandler<DocumentT> {

    void registerWith(final StubbedCamundaSearchClient client);
  }

  @FunctionalInterface
  public interface SearchRequestHandler<DocumentT> {

    SearchQueryResponse<DocumentT> handle(final SearchQueryRequest request) throws Exception;
  }
}
