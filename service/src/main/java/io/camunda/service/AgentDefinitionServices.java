/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.security.core.auth.RequiredAuthorization.withRequiredAuthorization;
import static io.camunda.service.authorization.Authorizations.AGENT_DEFINITION_READ_AUTHORIZATION;

import io.camunda.search.clients.AgentDefinitionSearchClient;
import io.camunda.search.entities.AgentDefinitionEntity;
import io.camunda.search.query.AgentDefinitionQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;

public final class AgentDefinitionServices
    extends SearchQueryService<
        AgentDefinitionServices, AgentDefinitionQuery, AgentDefinitionEntity> {

  private final AgentDefinitionSearchClient agentDefinitionSearchClient;

  public AgentDefinitionServices(
      final String physicalTenantId,
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final AgentDefinitionSearchClient agentDefinitionSearchClient,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    super(
        physicalTenantId,
        brokerClient,
        securityContextProvider,
        executorProvider,
        brokerRequestAuthorizationConverter);
    this.agentDefinitionSearchClient = agentDefinitionSearchClient;
  }

  @Override
  public SearchQueryResult<AgentDefinitionEntity> search(
      final AgentDefinitionQuery query, final CamundaAuthentication authentication) {
    return executeSearchRequest(
        () ->
            agentDefinitionSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication, AGENT_DEFINITION_READ_AUTHORIZATION))
                .searchAgentDefinitions(query));
  }

  public AgentDefinitionEntity getByKey(
      final long agentDefinitionKey, final CamundaAuthentication authentication) {
    return executeSearchRequest(
        () ->
            agentDefinitionSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication,
                        withRequiredAuthorization(
                            AGENT_DEFINITION_READ_AUTHORIZATION,
                            AgentDefinitionEntity::processDefinitionId)))
                .getAgentDefinition(agentDefinitionKey));
  }
}
