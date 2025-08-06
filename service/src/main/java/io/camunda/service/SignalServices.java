/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerBroadcastSignalRequest;
import io.camunda.zeebe.protocol.impl.record.value.signal.SignalRecord;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class SignalServices extends ApiServices<SignalServices> {

  public SignalServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final CamundaAuthentication authentication,
      final ApiServicesExecutorProvider executorProvider) {
    super(brokerClient, securityContextProvider, authentication, executorProvider);
  }

  @Override
  public SignalServices withAuthentication(final CamundaAuthentication authentication) {
    return new SignalServices(
        brokerClient, securityContextProvider, authentication, executorProvider);
  }

  public CompletableFuture<BrokerResponse<SignalRecord>> broadcastSignal(
      final String signalName, final Map<String, Object> variables, final String tenantId) {
    return sendBrokerRequestWithFullResponse(
        new BrokerBroadcastSignalRequest(signalName)
            .setVariables(getDocumentOrEmpty(variables))
            .setTenantId(tenantId));
  }
}
