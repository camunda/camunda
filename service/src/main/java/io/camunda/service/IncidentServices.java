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
import io.camunda.search.entities.IncidentEntity.IncidentState;
import io.camunda.search.entities.IncidentProcessInstanceStatisticsByDefinitionEntity;
import io.camunda.search.entities.IncidentProcessInstanceStatisticsByErrorEntity;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.query.IncidentProcessInstanceStatisticsByDefinitionQuery;
import io.camunda.search.query.IncidentProcessInstanceStatisticsByErrorQuery;
import io.camunda.search.query.IncidentQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
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
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    super(
        brokerClient,
        securityContextProvider,
        executorProvider,
        brokerRequestAuthorizationConverter);
    this.incidentSearchClient = incidentSearchClient;
  }

  public SearchQueryResult<IncidentEntity> search(
      final Function<IncidentQuery.Builder, ObjectBuilder<IncidentQuery>> fn,
      final CamundaAuthentication authentication) {
    return search(incidentSearchQuery(fn), authentication);
  }

  @Override
  public SearchQueryResult<IncidentEntity> search(
      final IncidentQuery query, final CamundaAuthentication authentication) {
    return executeSearchRequest(
        () ->
            incidentSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication, INCIDENT_READ_AUTHORIZATION))
                .searchIncidents(query));
  }

  public IncidentEntity getByKey(final Long key, final CamundaAuthentication authentication) {
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
      final long incidentKey,
      final Long operationReference,
      final CamundaAuthentication authentication) {
    final var brokerRequest = new BrokerResolveIncidentRequest(incidentKey);
    if (operationReference != null) {
      brokerRequest.setOperationReference(operationReference);
    }
    return sendBrokerRequest(brokerRequest, authentication);
  }

  public SearchQueryResult<IncidentProcessInstanceStatisticsByErrorEntity>
      incidentProcessInstanceStatisticsByError(
          final IncidentProcessInstanceStatisticsByErrorQuery query,
          final CamundaAuthentication authentication) {
    final var sanitizedQuery =
        IncidentProcessInstanceStatisticsByErrorQuery.of(
            b ->
                b.page(query.page())
                    .sort(query.sort())
                    .filter(FilterBuilders.incident(f -> f.states(IncidentState.ACTIVE.name()))));
    return executeSearchRequest(
        () ->
            incidentSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication, INCIDENT_READ_AUTHORIZATION))
                .incidentProcessInstanceStatisticsByError(sanitizedQuery));
  }

  public SearchQueryResult<IncidentProcessInstanceStatisticsByDefinitionEntity>
      searchIncidentProcessInstanceStatisticsByDefinition(
          final IncidentProcessInstanceStatisticsByDefinitionQuery query,
          final CamundaAuthentication authentication) {
    return executeSearchRequest(
        () ->
            incidentSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication, INCIDENT_READ_AUTHORIZATION))
                .searchIncidentProcessInstanceStatisticsByDefinition(query));
  }
}
