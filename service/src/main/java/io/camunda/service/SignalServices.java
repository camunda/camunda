/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
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
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    super(
        brokerClient,
        securityContextProvider,
        executorProvider,
        brokerRequestAuthorizationConverter);
  }

  public CompletableFuture<BrokerResponse<SignalRecord>> broadcastSignal(
      final String signalName,
      final Map<String, Object> variables,
      final String tenantId,
      final CamundaAuthentication authentication) {
    return sendBrokerRequestWithFullResponse(
        new BrokerBroadcastSignalRequest(signalName)
            .setVariables(getDocumentOrEmpty(variables))
            .setTenantId(tenantId),
        authentication);
  }
}
