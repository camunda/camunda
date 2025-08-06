/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.search.query.SearchQueryBuilders.incidentSearchQuery;
import static io.camunda.security.auth.Authorization.withAuthorization;
import static io.camunda.service.authorization.Authorizations.INCIDENT_READ_AUTHORIZATION;

import io.camunda.search.clients.IncidentSearchClient;
import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.query.IncidentQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.util.ObjectBuilder;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerResolveIncidentRequest;
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class IncidentServices
    extends SearchQueryService<IncidentServices, IncidentQuery, IncidentEntity> {

  private final IncidentSearchClient incidentSearchClient;

  public IncidentServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final IncidentSearchClient incidentSearchClient,
      final CamundaAuthentication authentication,
      final ApiServicesExecutorProvider executorProvider) {
    super(brokerClient, securityContextProvider, authentication, executorProvider);
    this.incidentSearchClient = incidentSearchClient;
  }

  public SearchQueryResult<IncidentEntity> search(
      final Function<IncidentQuery.Builder, ObjectBuilder<IncidentQuery>> fn) {
    return search(incidentSearchQuery(fn));
  }

  @Override
  public SearchQueryResult<IncidentEntity> search(final IncidentQuery query) {
    return executeSearchRequest(
        () ->
            incidentSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication, INCIDENT_READ_AUTHORIZATION))
                .searchIncidents(query));
  }

  @Override
  public IncidentServices withAuthentication(final CamundaAuthentication authentication) {
    return new IncidentServices(
        brokerClient,
        securityContextProvider,
        incidentSearchClient,
        authentication,
        executorProvider);
  }

  public IncidentEntity getByKey(final Long key) {
    return executeSearchRequest(
        () ->
            incidentSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication,
                        withAuthorization(
                            INCIDENT_READ_AUTHORIZATION, IncidentEntity::processDefinitionId)))
                .getIncident(key));
  }

  public CompletableFuture<IncidentRecord> resolveIncident(
      final long incidentKey, final Long operationReference) {
    final var brokerRequest = new BrokerResolveIncidentRequest(incidentKey);
    if (operationReference != null) {
      brokerRequest.setOperationReference(operationReference);
    }
    return sendBrokerRequest(brokerRequest);
  }
}
