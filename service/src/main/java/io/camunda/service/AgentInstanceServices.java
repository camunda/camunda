/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.security.core.auth.RequiredAuthorization.withRequiredAuthorization;
import static io.camunda.service.authorization.Authorizations.AGENT_INSTANCE_READ_AUTHORIZATION;

import io.camunda.search.clients.AgentInstanceSearchClient;
import io.camunda.search.entities.AgentInstanceEntity;
import io.camunda.search.query.AgentInstanceQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCreateAgentInstanceRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerUpdateAgentInstanceRequest;
import io.camunda.zeebe.protocol.impl.record.value.agentinstance.AgentInstanceRecord;
import java.util.concurrent.CompletableFuture;

public final class AgentInstanceServices
    extends SearchQueryService<AgentInstanceServices, AgentInstanceQuery, AgentInstanceEntity> {

  private final AgentInstanceSearchClient agentInstanceSearchClient;

  public AgentInstanceServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final AgentInstanceSearchClient agentInstanceSearchClient,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    super(
        brokerClient,
        securityContextProvider,
        executorProvider,
        brokerRequestAuthorizationConverter);
    this.agentInstanceSearchClient = agentInstanceSearchClient;
  }

  public CompletableFuture<AgentInstanceRecord> createAgentInstance(
      final AgentInstanceRecord record, final CamundaAuthentication authentication) {
    return sendBrokerRequest(new BrokerCreateAgentInstanceRequest(record), authentication);
  }

  public CompletableFuture<AgentInstanceRecord> updateAgentInstance(
      final AgentInstanceRecord record, final CamundaAuthentication authentication) {
    return sendBrokerRequest(new BrokerUpdateAgentInstanceRequest(record), authentication);
  }

  @Override
  public SearchQueryResult<AgentInstanceEntity> search(
      final AgentInstanceQuery query, final CamundaAuthentication authentication) {
    return executeSearchRequest(
        () ->
            agentInstanceSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication, AGENT_INSTANCE_READ_AUTHORIZATION))
                .searchAgentInstances(query));
  }

  public AgentInstanceEntity getByKey(
      final long agentInstanceKey, final CamundaAuthentication authentication) {
    return executeSearchRequest(
        () ->
            agentInstanceSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication,
                        withRequiredAuthorization(
                            AGENT_INSTANCE_READ_AUTHORIZATION,
                            AgentInstanceEntity::processDefinitionId)))
                .getAgentInstance(agentInstanceKey));
  }
}
