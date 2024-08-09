/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.search.clients.CamundaSearchClient;
import io.camunda.service.entities.IncidentEntity;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.search.query.IncidentQuery;
import io.camunda.service.search.query.IncidentQuery.Builder;
import io.camunda.service.search.query.SearchQueryBuilders;
import io.camunda.service.search.query.SearchQueryResult;
import io.camunda.service.security.auth.Authentication;
import io.camunda.service.transformers.ServiceTransformers;
import io.camunda.util.ObjectBuilder;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerResolveIncidentRequest;
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class IncidentServices
    extends SearchQueryService<IncidentServices, IncidentQuery, IncidentEntity> {

  public IncidentServices(
      final BrokerClient brokerClient,
      final CamundaSearchClient searchClient,
      final ServiceTransformers transformers,
      final Authentication authentication) {
    super(brokerClient, searchClient, transformers, authentication);
  }

  public SearchQueryResult<IncidentEntity> search(
      final Function<Builder, ObjectBuilder<IncidentQuery>> fn) {
    return search(SearchQueryBuilders.incidentSearchQuery(fn));
  }

  @Override
  public SearchQueryResult<IncidentEntity> search(final IncidentQuery query) {
    return executor.search(query, IncidentEntity.class);
  }

  @Override
  public IncidentServices withAuthentication(final Authentication authentication) {
    return new IncidentServices(brokerClient, searchClient, transformers, authentication);
  }

  public CompletableFuture<IncidentRecord> resolveIncident(final long incidentKey) {
    return sendBrokerRequest(new BrokerResolveIncidentRequest(incidentKey));
  }
}
