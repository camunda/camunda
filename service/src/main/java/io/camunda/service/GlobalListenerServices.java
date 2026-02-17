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
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCreateGlobalListenerRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerDeleteGlobalListenerRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerUpdateGlobalListenerRequest;
import io.camunda.zeebe.protocol.impl.record.value.globallistener.GlobalListenerRecord;
import java.util.concurrent.CompletableFuture;

public final class GlobalListenerServices extends ApiServices<GlobalListenerServices> {

  public GlobalListenerServices(
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
  public GlobalListenerServices withAuthentication(final CamundaAuthentication authentication) {
    return new GlobalListenerServices(
        brokerClient,
        securityContextProvider,
        authentication,
        executorProvider,
        brokerRequestAuthorizationConverter);
  }

  public CompletableFuture<GlobalListenerRecord> createGlobalListener(
      final GlobalListenerRecord request) {
    return sendBrokerRequest(new BrokerCreateGlobalListenerRequest(request));
  }

  public CompletableFuture<GlobalListenerRecord> updateGlobalListener(
      final GlobalListenerRecord request) {
    return sendBrokerRequest(new BrokerUpdateGlobalListenerRequest(request));
  }

  public CompletableFuture<GlobalListenerRecord> deleteGlobalListener(
      final GlobalListenerRecord request) {
    return sendBrokerRequest(new BrokerDeleteGlobalListenerRequest(request));
  }
}
