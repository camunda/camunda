/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.search.security.auth.Authentication;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerBroadcastSignalRequest;
import io.camunda.zeebe.protocol.impl.record.value.signal.SignalRecord;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class SignalServices extends ApiServices<SignalServices> {

  public SignalServices(final BrokerClient brokerClient, final Authentication authentication) {
    super(brokerClient, authentication);
  }

  @Override
  public SignalServices withAuthentication(final Authentication authentication) {
    return new SignalServices(brokerClient, authentication);
  }

  public CompletableFuture<BrokerResponse<SignalRecord>> broadcastSignal(
      final String signalName, final Map<String, Object> variables, final String tenantId) {
    return sendBrokerRequestWithFullResponse(
        new BrokerBroadcastSignalRequest(signalName)
            .setVariables(getDocumentOrEmpty(variables))
            .setTenantId(tenantId));
  }
}
