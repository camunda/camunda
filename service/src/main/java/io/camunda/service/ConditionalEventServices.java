/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

// TODO: Change to SearchQueryService when search is added
public class ConditionalEventServices extends ApiServices<ConditionalEventServices> {

  public ConditionalEventServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final CamundaAuthentication authentication,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    super(
        brokerClient,
        securityContextProvider,
        authentication,
        executorProvider,
        brokerRequestAuthorizationConverter);
  }

  @Override
  public ConditionalEventServices withAuthentication(final CamundaAuthentication authentication) {
    return new ConditionalEventServices(
        brokerClient,
        securityContextProvider,
        authentication,
        executorProvider,
        brokerRequestAuthorizationConverter);
  }

  public CompletableFuture<Object> triggerConditionalEvent(final Object request) {
    return CompletableFuture.failedFuture(new UnsupportedOperationException("Not implemented yet"));
  }

  public record ConditionalEventCreateRequest(
      String tenantId, Long processDefinitionKey, Map<String, Object> variables) {}
}
