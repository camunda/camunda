/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.search.query.SearchQueryBuilders.incidentSearchQuery;

import io.camunda.search.clients.IncidentSearchClient;
import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.query.IncidentQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.Authentication;
import io.camunda.security.auth.Authorization;
import io.camunda.service.exception.ForbiddenException;
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
      final Authentication authentication) {
    super(brokerClient, securityContextProvider, authentication);
    this.incidentSearchClient = incidentSearchClient;
  }

  public SearchQueryResult<IncidentEntity> search(
      final Function<IncidentQuery.Builder, ObjectBuilder<IncidentQuery>> fn) {
    return search(incidentSearchQuery(fn));
  }

  @Override
  public SearchQueryResult<IncidentEntity> search(final IncidentQuery query) {
    return incidentSearchClient
        .withSecurityContext(
            securityContextProvider.provideSecurityContext(
                authentication, Authorization.of(a -> a.processDefinition().readInstance())))
        .searchIncidents(query);
  }

  @Override
  public IncidentServices withAuthentication(final Authentication authentication) {
    return new IncidentServices(
        brokerClient, securityContextProvider, incidentSearchClient, authentication);
  }

  public IncidentEntity getByKey(final Long key) {
    final var result =
        incidentSearchClient
            .withSecurityContext(securityContextProvider.provideSecurityContext(authentication))
            .searchIncidents(incidentSearchQuery(q -> q.filter(f -> f.incidentKeys(key))));
    final var incidentEntity = getSingleResultOrThrow(result, key, "Incident");
    final var authorization = Authorization.of(a -> a.processDefinition().readInstance());
    if (!securityContextProvider.isAuthorized(
        incidentEntity.bpmnProcessId(), authentication, authorization)) {
      throw new ForbiddenException(authorization);
    }
    return incidentEntity;
  }

  public CompletableFuture<IncidentRecord> resolveIncident(final long incidentKey) {
    return sendBrokerRequest(new BrokerResolveIncidentRequest(incidentKey));
  }
}
