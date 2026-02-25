/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.service.authorization.Authorizations.GLOBAL_TASK_LISTENER_READ_AUTHORIZATION;

import io.camunda.search.clients.GlobalListenerSearchClient;
import io.camunda.search.entities.GlobalListenerEntity;
import io.camunda.search.entities.GlobalListenerType;
import io.camunda.search.query.GlobalListenerQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCreateGlobalListenerRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerDeleteGlobalListenerRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerUpdateGlobalListenerRequest;
import io.camunda.zeebe.protocol.impl.record.value.globallistener.GlobalListenerRecord;
import java.util.concurrent.CompletableFuture;

public final class GlobalListenerServices
    extends SearchQueryService<GlobalListenerServices, GlobalListenerQuery, GlobalListenerEntity> {

  private final GlobalListenerSearchClient globalListenerSearchClient;

  public GlobalListenerServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final GlobalListenerSearchClient globalListenerSearchClient,
      final CamundaAuthentication authentication,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    super(
        brokerClient,
        securityContextProvider,
        authentication,
        executorProvider,
        brokerRequestAuthorizationConverter);
    this.globalListenerSearchClient = globalListenerSearchClient;
  }

  @Override
  public GlobalListenerServices withAuthentication(final CamundaAuthentication authentication) {
    return new GlobalListenerServices(
        brokerClient,
        securityContextProvider,
        globalListenerSearchClient,
        authentication,
        executorProvider,
        brokerRequestAuthorizationConverter);
  }

  public CompletableFuture<GlobalListenerRecord> createGlobalListener(
      final GlobalListenerRecord request) {
    return sendBrokerRequest(new BrokerCreateGlobalListenerRequest(request));
  }

  public GlobalListenerEntity getGlobalTaskListener(final GlobalListenerRecord request) {
    return executeSearchRequest(
        () ->
            globalListenerSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication, GLOBAL_TASK_LISTENER_READ_AUTHORIZATION))
                .getGlobalListener(
                    request.getId(), GlobalListenerType.valueOf(request.getListenerType().name())));
  }

  public CompletableFuture<GlobalListenerRecord> updateGlobalListener(
      final GlobalListenerRecord request) {
    return sendBrokerRequest(new BrokerUpdateGlobalListenerRequest(request));
  }

  public CompletableFuture<GlobalListenerRecord> deleteGlobalListener(
      final GlobalListenerRecord request) {
    return sendBrokerRequest(new BrokerDeleteGlobalListenerRequest(request));
  }

  @Override
  public SearchQueryResult<GlobalListenerEntity> search(final GlobalListenerQuery query) {
    return executeSearchRequest(
        () ->
            globalListenerSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication, GLOBAL_TASK_LISTENER_READ_AUTHORIZATION))
                .searchGlobalListeners(query));
  }
}
