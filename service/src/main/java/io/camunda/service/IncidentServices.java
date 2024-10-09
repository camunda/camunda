/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.search.clients.IncidentSearchClient;
import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.exception.NotFoundException;
import io.camunda.search.query.IncidentQuery;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.security.auth.Authentication;
import io.camunda.service.search.core.SearchQueryService;
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
      final IncidentSearchClient incidentSearchClient,
      final Authentication authentication) {
    super(brokerClient, authentication);
    this.incidentSearchClient = incidentSearchClient;
  }

  public SearchQueryResult<IncidentEntity> search(
      final Function<IncidentQuery.Builder, ObjectBuilder<IncidentQuery>> fn) {
    return search(SearchQueryBuilders.incidentSearchQuery(fn));
  }

  @Override
  public SearchQueryResult<IncidentEntity> search(final IncidentQuery query) {
    return incidentSearchClient.searchIncidents(query, authentication);
  }

  @Override
  public IncidentServices withAuthentication(final Authentication authentication) {
    return new IncidentServices(brokerClient, incidentSearchClient, authentication);
  }

  public IncidentEntity getByKey(final Long key) {
    final SearchQueryResult<IncidentEntity> result =
        search(SearchQueryBuilders.incidentSearchQuery().filter(f -> f.incidentKeys(key)).build());
    if (result.total() < 1) {
      throw new NotFoundException(String.format("Incident with key %d not found", key));
    } else if (result.total() > 1) {
      throw new CamundaSearchException(
          String.format("Found Incident with key %d more than once", key));
    } else {
      return result.items().stream().findFirst().orElseThrow();
    }
  }

  public CompletableFuture<IncidentRecord> resolveIncident(final long incidentKey) {
    return sendBrokerRequest(new BrokerResolveIncidentRequest(incidentKey));
  }
}
