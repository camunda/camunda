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
import io.camunda.zeebe.gateway.impl.broker.request.BrokerSetVariablesRequest;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableDocumentRecord;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ElementInstanceServices extends ApiServices<ElementInstanceServices> {

  public ElementInstanceServices(
      final BrokerClient brokerClient, final Authentication authentication) {
    super(brokerClient, authentication);
  }

  @Override
  public ElementInstanceServices withAuthentication(final Authentication authentication) {
    return new ElementInstanceServices(brokerClient, authentication);
  }

  public CompletableFuture<VariableDocumentRecord> setVariables(final SetVariablesRequest request) {
    final var brokerRequest =
        new BrokerSetVariablesRequest()
            .setElementInstanceKey(request.elementInstanceKey())
            .setVariables(getDocumentOrEmpty(request.variables()))
            .setLocal(request.local());

    if (request.operationReference() != null) {
      brokerRequest.setOperationReference(request.operationReference());
    }
    return sendBrokerRequest(brokerRequest);
  }

  public record SetVariablesRequest(
      long elementInstanceKey,
      Map<String, Object> variables,
      Boolean local,
      Long operationReference) {}
}
