/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.security.auth.Authorization.with;
import static io.camunda.security.auth.Authorization.withResourceId;
import static io.camunda.service.authorization.Authorizations.INCIDENT_READ_AUTHORIZATION;

import io.camunda.search.clients.IncidentSearchClient;
import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.query.IncidentQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerResolveIncidentRequest;
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import java.util.concurrent.CompletableFuture;

public class IncidentServices
    extends SearchQueryService<IncidentServices, IncidentQuery, IncidentEntity> {

  private final IncidentSearchClient incidentSearchClient;

  public IncidentServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final IncidentSearchClient incidentSearchClient,
      final CamundaAuthentication authentication) {
    super(brokerClient, securityContextProvider, authentication);
    this.incidentSearchClient = incidentSearchClient;
  }

  @Override
  public SearchQueryResult<IncidentEntity> search(final IncidentQuery query) {
    return incidentSearchClient
        .withSecurityContext(
            securityContextProvider.provideSecurityContext(
                authentication, with(INCIDENT_READ_AUTHORIZATION)))
        .searchIncidents(query);
  }

  @Override
  public IncidentServices withAuthentication(final CamundaAuthentication authentication) {
    return new IncidentServices(
        brokerClient, securityContextProvider, incidentSearchClient, authentication);
  }

  public IncidentEntity getByKey(final Long key) {
    return incidentSearchClient
        .withSecurityContext(
            securityContextProvider.provideSecurityContext(
                authentication,
                withResourceId(INCIDENT_READ_AUTHORIZATION, IncidentEntity::processDefinitionId)))
        .getIncidentByKey(key);
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
