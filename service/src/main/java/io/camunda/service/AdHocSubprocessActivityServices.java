/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.search.query.SearchQueryBuilders.adHocSubprocessActivityQuery;

import io.camunda.search.clients.AdHocSubprocessActivitySearchClient;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.query.AdHocSubprocessActivityQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.Authentication;
import io.camunda.security.auth.Authorization;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.util.ObjectBuilder;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import java.util.function.Function;

public final class AdHocSubprocessActivityServices
    extends SearchQueryService<
        AdHocSubprocessActivityServices, AdHocSubprocessActivityQuery, FlowNodeInstanceEntity> {

  private final AdHocSubprocessActivitySearchClient searchClient;

  public AdHocSubprocessActivityServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final AdHocSubprocessActivitySearchClient searchClient,
      final Authentication authentication) {
    super(brokerClient, securityContextProvider, authentication);
    this.searchClient = searchClient;
  }

  @Override
  public AdHocSubprocessActivityServices withAuthentication(final Authentication authentication) {
    return new AdHocSubprocessActivityServices(
        brokerClient, securityContextProvider, searchClient, authentication);
  }

  @Override
  public SearchQueryResult<FlowNodeInstanceEntity> search(
      final AdHocSubprocessActivityQuery query) {
    return searchClient
        .withSecurityContext(
            securityContextProvider.provideSecurityContext(
                authentication, Authorization.of(a -> a.processDefinition().readProcessInstance())))
        .searchAdHocSubprocessActivities(query);
  }

  public SearchQueryResult<FlowNodeInstanceEntity> search(
      final Function<
              AdHocSubprocessActivityQuery.Builder, ObjectBuilder<AdHocSubprocessActivityQuery>>
          fn) {
    return search(adHocSubprocessActivityQuery(fn));
  }
}
