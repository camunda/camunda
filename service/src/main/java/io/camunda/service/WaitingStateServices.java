/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.service.authorization.Authorizations.PROCESS_INSTANCE_READ_AUTHORIZATION;

import io.camunda.search.clients.WaitingStateSearchClient;
import io.camunda.search.entities.WaitingStateEntity;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.WaitingStateQuery;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;

public final class WaitingStateServices
    extends SearchQueryService<WaitingStateServices, WaitingStateQuery, WaitingStateEntity> {

  private final WaitingStateSearchClient waitingStateSearchClient;

  public WaitingStateServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final WaitingStateSearchClient waitingStateSearchClient,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    super(
        brokerClient,
        securityContextProvider,
        executorProvider,
        brokerRequestAuthorizationConverter);
    this.waitingStateSearchClient = waitingStateSearchClient;
  }

  @Override
  public SearchQueryResult<WaitingStateEntity> search(
      final WaitingStateQuery query, final CamundaAuthentication authentication) {
    return executeSearchRequest(
        () ->
            waitingStateSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication, PROCESS_INSTANCE_READ_AUTHORIZATION))
                .searchWaitingStates(query));
  }
}
