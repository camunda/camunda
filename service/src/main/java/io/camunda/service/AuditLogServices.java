/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.search.clients.AuditLogSearchClient;
import io.camunda.search.entities.AuditLogEntity;
import io.camunda.search.query.AuditLogQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;

public class AuditLogServices
    extends SearchQueryService<AuditLogServices, AuditLogQuery, AuditLogEntity> {

  private final AuditLogSearchClient auditLogSearchClient;

  public AuditLogServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final AuditLogSearchClient auditLogSearchClient,
      final CamundaAuthentication authentication,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    super(
        brokerClient,
        securityContextProvider,
        authentication,
        executorProvider,
        brokerRequestAuthorizationConverter);
    this.auditLogSearchClient = auditLogSearchClient;
  }

  @Override
  public SearchQueryResult<AuditLogEntity> search(final AuditLogQuery query) {
    return executeSearchRequest(
        () ->
            auditLogSearchClient
                .withSecurityContext(securityContextProvider.provideSecurityContext(authentication))
                .searchAuditLogs(query));
  }

  @Override
  public AuditLogServices withAuthentication(final CamundaAuthentication authentication) {
    return new AuditLogServices(
        brokerClient,
        securityContextProvider,
        auditLogSearchClient,
        authentication,
        executorProvider,
        brokerRequestAuthorizationConverter);
  }

  public AuditLogEntity getAuditLog(final String operationKey) {
    return executeSearchRequest(
        () ->
            auditLogSearchClient
                .withSecurityContext(securityContextProvider.provideSecurityContext(authentication))
                .getAuditLog(operationKey));
  }
}
