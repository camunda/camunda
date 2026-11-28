/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.security.core.auth.RequiredAuthorization.withRequiredAuthorization;
import static io.camunda.service.authorization.Authorizations.VARIABLE_HISTORY_READ_AUTHORIZATION;

import io.camunda.search.clients.VariableHistorySearchClient;
import io.camunda.search.entities.VariableHistoryItemEntity;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.VariableHistoryItemQuery;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;

public class VariableHistoryServices
    extends SearchQueryService<
        VariableHistoryServices, VariableHistoryItemQuery, VariableHistoryItemEntity> {

  private final VariableHistorySearchClient variableHistorySearchClient;

  public VariableHistoryServices(
      final String physicalTenantId,
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final VariableHistorySearchClient variableHistorySearchClient,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    super(
        physicalTenantId,
        brokerClient,
        securityContextProvider,
        executorProvider,
        brokerRequestAuthorizationConverter);
    this.variableHistorySearchClient = variableHistorySearchClient;
  }

  @Override
  public SearchQueryResult<VariableHistoryItemEntity> search(
      final VariableHistoryItemQuery query, final CamundaAuthentication authentication) {
    return executeSearchRequest(
        () ->
            variableHistorySearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication, VARIABLE_HISTORY_READ_AUTHORIZATION))
                .searchVariableHistoryItems(query));
  }

  public VariableHistoryItemEntity getByKey(
      final long key, final CamundaAuthentication authentication) {
    return executeSearchRequest(
        () ->
            variableHistorySearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication,
                        withRequiredAuthorization(
                            VARIABLE_HISTORY_READ_AUTHORIZATION,
                            VariableHistoryItemEntity::processDefinitionId)))
                .getVariableHistoryItem(key));
  }
}
