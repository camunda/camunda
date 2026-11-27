/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.service.authorization.Authorizations.CASE_READ_AUTHORIZATION;

import io.camunda.search.clients.CaseSearchClient;
import io.camunda.search.entities.CaseEntity;
import io.camunda.search.query.CaseQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;

public class CaseServices extends SearchQueryService<CaseServices, CaseQuery, CaseEntity> {

  private final CaseSearchClient caseSearchClient;

  public CaseServices(
      final String physicalTenantId,
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final CaseSearchClient caseSearchClient,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    super(
        physicalTenantId,
        brokerClient,
        securityContextProvider,
        executorProvider,
        brokerRequestAuthorizationConverter);
    this.caseSearchClient = caseSearchClient;
  }

  @Override
  public SearchQueryResult<CaseEntity> search(
      final CaseQuery query, final CamundaAuthentication authentication) {
    return executeSearchRequest(
        () ->
            caseSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication, CASE_READ_AUTHORIZATION))
                .searchCases(query));
  }
}
