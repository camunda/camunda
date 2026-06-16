/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.service.authorization.Authorizations.AGENT_HISTORY_READ_AUTHORIZATION;

import io.camunda.search.clients.AgentHistorySearchClient;
import io.camunda.search.entities.AgentInstanceHistoryEntity;
import io.camunda.search.query.AgentInstanceHistoryQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCreateAgentHistoryRequest;
import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryRecord;
import java.util.concurrent.CompletableFuture;

public final class AgentHistoryServices
    extends SearchQueryService<
        AgentHistoryServices, AgentInstanceHistoryQuery, AgentInstanceHistoryEntity> {

  private final AgentHistorySearchClient agentHistorySearchClient;

  public AgentHistoryServices(
      final String physicalTenantId,
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final AgentHistorySearchClient agentHistorySearchClient,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    super(
        physicalTenantId,
        brokerClient,
        securityContextProvider,
        executorProvider,
        brokerRequestAuthorizationConverter);
    this.agentHistorySearchClient = agentHistorySearchClient;
  }

  public CompletableFuture<AgentHistoryRecord> createAgentHistoryItem(
      final AgentHistoryRecord record, final CamundaAuthentication authentication) {
    return sendBrokerRequest(new BrokerCreateAgentHistoryRequest(record), authentication);
  }

  @Override
  public SearchQueryResult<AgentInstanceHistoryEntity> search(
      final AgentInstanceHistoryQuery query, final CamundaAuthentication authentication) {
    return executeSearchRequest(
        () ->
            agentHistorySearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication, AGENT_HISTORY_READ_AUTHORIZATION))
                .searchAgentHistoryItems(query));
  }
}
