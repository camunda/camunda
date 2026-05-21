/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.security.auth.Authorization.withAuthorization;
import static io.camunda.service.authorization.Authorizations.GLOBAL_TASK_LISTENER_READ_AUTHORIZATION;
import static io.camunda.zeebe.protocol.record.value.AuthorizationScope.WILDCARD_CHAR;

import io.camunda.search.clients.GlobalListenerSearchClient;
import io.camunda.search.entities.GlobalListenerEntity;
import io.camunda.search.entities.GlobalListenerType;
import io.camunda.search.query.GlobalListenerQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
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
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    super(
        brokerClient,
        securityContextProvider,
        executorProvider,
        brokerRequestAuthorizationConverter);
    this.globalListenerSearchClient = globalListenerSearchClient;
  }

  public CompletableFuture<GlobalListenerRecord> createGlobalListener(
      final GlobalListenerRecord request, final CamundaAuthentication authentication) {
    return sendBrokerRequest(new BrokerCreateGlobalListenerRequest(request), authentication);
  }

  public GlobalListenerEntity getGlobalTaskListener(
      final GlobalListenerRecord request, final CamundaAuthentication authentication) {
    return executeSearchRequest(
        () ->
            globalListenerSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication,
                        withAuthorization(GLOBAL_TASK_LISTENER_READ_AUTHORIZATION, WILDCARD_CHAR)))
                .getGlobalListener(
                    request.getId(), GlobalListenerType.valueOf(request.getListenerType().name())));
  }

  public CompletableFuture<GlobalListenerRecord> updateGlobalListener(
      final GlobalListenerRecord request, final CamundaAuthentication authentication) {
    return sendBrokerRequest(new BrokerUpdateGlobalListenerRequest(request), authentication);
  }

  public CompletableFuture<GlobalListenerRecord> deleteGlobalListener(
      final GlobalListenerRecord request, final CamundaAuthentication authentication) {
    return sendBrokerRequest(new BrokerDeleteGlobalListenerRequest(request), authentication);
  }

  @Override
  public SearchQueryResult<GlobalListenerEntity> search(
      final GlobalListenerQuery query, final CamundaAuthentication authentication) {
    return executeSearchRequest(
        () ->
            globalListenerSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication, GLOBAL_TASK_LISTENER_READ_AUTHORIZATION))
                .searchGlobalListeners(query));
  }
}
