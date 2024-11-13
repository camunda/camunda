/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.search.query.SearchQueryBuilders.flownodeInstanceSearchQuery;

import io.camunda.search.clients.FlowNodeInstanceSearchClient;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.query.FlowNodeInstanceQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.Authentication;
import io.camunda.security.auth.Authorization;
import io.camunda.service.exception.ForbiddenException;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.util.ObjectBuilder;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import java.util.function.Function;

public final class FlowNodeInstanceServices
    extends SearchQueryService<
        FlowNodeInstanceServices, FlowNodeInstanceQuery, FlowNodeInstanceEntity> {

  private final FlowNodeInstanceSearchClient flowNodeInstanceSearchClient;

  public FlowNodeInstanceServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final FlowNodeInstanceSearchClient flowNodeInstanceSearchClient,
      final Authentication authentication) {
    super(brokerClient, securityContextProvider, authentication);
    this.flowNodeInstanceSearchClient = flowNodeInstanceSearchClient;
  }

  @Override
  public FlowNodeInstanceServices withAuthentication(final Authentication authentication) {
    return new FlowNodeInstanceServices(
        brokerClient, securityContextProvider, flowNodeInstanceSearchClient, authentication);
  }

  @Override
  public SearchQueryResult<FlowNodeInstanceEntity> search(final FlowNodeInstanceQuery query) {
    return flowNodeInstanceSearchClient
        .withSecurityContext(
            securityContextProvider.provideSecurityContext(
                authentication, Authorization.of(a -> a.processDefinition().readInstance())))
        .searchFlowNodeInstances(query);
  }

  public SearchQueryResult<FlowNodeInstanceEntity> search(
      final Function<FlowNodeInstanceQuery.Builder, ObjectBuilder<FlowNodeInstanceQuery>> fn) {
    return search(flownodeInstanceSearchQuery(fn));
  }

  public FlowNodeInstanceEntity getByKey(final Long key) {
    final var result =
        flowNodeInstanceSearchClient
            .withSecurityContext(securityContextProvider.provideSecurityContext(authentication))
            .searchFlowNodeInstances(
                flownodeInstanceSearchQuery(q -> q.filter(f -> f.flowNodeInstanceKeys(key))));
    final var flowNodeInstance = getSingleResultOrThrow(result, key, "Flow node instance");
    final var authorization = Authorization.of(a -> a.processDefinition().readInstance());
    if (!securityContextProvider.isAuthorized(
        flowNodeInstance.bpmnProcessId(), authentication, authorization)) {
      throw new ForbiddenException(authorization);
    }
    return flowNodeInstance;
  }
}
