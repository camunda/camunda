/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.security.auth.Authorization.withAuthorization;
import static io.camunda.service.authorization.Authorizations.VARIABLE_READ_AUTHORIZATION;

import io.camunda.search.clients.VariableSearchClient;
import io.camunda.search.entities.VariableEntity;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.VariableQuery;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCreateVariableRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerDeleteVariableRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerUpdateVariableRequest;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableRecord;
import java.util.concurrent.CompletableFuture;

public final class VariableServices
    extends SearchQueryService<VariableServices, VariableQuery, VariableEntity> {

  private final VariableSearchClient variableSearchClient;

  public VariableServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final VariableSearchClient variableSearchClient,
      final CamundaAuthentication authentication,
      final ApiServicesExecutorProvider executorProvider) {
    super(brokerClient, securityContextProvider, authentication, executorProvider);
    this.variableSearchClient = variableSearchClient;
  }

  @Override
  public VariableServices withAuthentication(final CamundaAuthentication authentication) {
    return new VariableServices(
        brokerClient,
        securityContextProvider,
        variableSearchClient,
        authentication,
        executorProvider);
  }

  @Override
  public SearchQueryResult<VariableEntity> search(final VariableQuery query) {
    return executeSearchRequest(
        () ->
            variableSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication, VARIABLE_READ_AUTHORIZATION))
                .searchVariables(query));
  }

  public VariableEntity getByKey(final Long key) {
    return executeSearchRequest(
        () ->
            variableSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication,
                        withAuthorization(
                            VARIABLE_READ_AUTHORIZATION, VariableEntity::processDefinitionId)))
                .getVariable(key));
  }

  public CompletableFuture<VariableRecord> createClusterVariable(
      final String name, final Object value) {
    return sendBrokerRequest(
        new BrokerCreateVariableRequest()
            .clusterLevel()
            .setVariable(name, getDocumentOrEmpty(value)));
  }

  public CompletableFuture<VariableRecord> updateVariable(
      final Long variableKey, final Object value) {
    return sendBrokerRequest(
        new BrokerUpdateVariableRequest().setKey(variableKey).setValue(getDocumentOrEmpty(value)));
  }

  public CompletableFuture<VariableRecord> deleteVariable(final Long variableKey) {
    return sendBrokerRequest(new BrokerDeleteVariableRequest().setKey(variableKey));
  }
}
