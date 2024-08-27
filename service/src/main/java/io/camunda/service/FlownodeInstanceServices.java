/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.search.clients.CamundaSearchClient;
import io.camunda.service.entities.FlownodeInstanceEntity;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.search.query.FlownodeInstanceQuery;
import io.camunda.service.search.query.SearchQueryResult;
import io.camunda.service.security.auth.Authentication;
import io.camunda.service.transformers.ServiceTransformers;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import java.util.List;

public class FlownodeInstanceServices
    extends SearchQueryService<
        FlownodeInstanceServices, FlownodeInstanceQuery, FlownodeInstanceEntity> {

  public FlownodeInstanceServices(
      final BrokerClient brokerClient,
      final CamundaSearchClient searchClient,
      final ServiceTransformers transformers,
      final Authentication authentication) {
    super(brokerClient, searchClient, transformers, authentication);
  }

  @Override
  public SearchQueryResult<FlownodeInstanceEntity> search(final FlownodeInstanceQuery query) {
    return new SearchQueryResult<>(1, List.of(new FlownodeInstanceEntity()), new Object[] {"v"});
  }

  @Override
  public FlownodeInstanceServices withAuthentication(final Authentication authentication) {
    return new FlownodeInstanceServices(brokerClient, searchClient, transformers, authentication);
  }
}
