/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.AdHocSubProcessActivityServices.AdHocSubProcessActivateActivitiesRequest.AdHocSubProcessActivateActivityReference;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerActivateAdHocSubProcessActivityRequest;
import io.camunda.zeebe.protocol.impl.record.value.adhocsubprocess.AdHocSubProcessInstructionRecord;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class AdHocSubProcessActivityServices extends ApiServices<AdHocSubProcessActivityServices> {

  public AdHocSubProcessActivityServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final CamundaAuthentication authentication) {
    super(brokerClient, securityContextProvider, authentication);
  }

  @Override
  public AdHocSubProcessActivityServices withAuthentication(
      final CamundaAuthentication authentication) {
    return new AdHocSubProcessActivityServices(
        brokerClient, securityContextProvider, authentication);
  }

  public CompletableFuture<AdHocSubProcessInstructionRecord> activateActivities(
      final AdHocSubProcessActivateActivitiesRequest request) {
    final var brokerRequest =
        new BrokerActivateAdHocSubProcessActivityRequest()
            .setAdHocSubProcessInstanceKey(request.adHocSubProcessInstanceKey());

    request
        .elements()
        .forEach(
            element ->
                brokerRequest.addElement(
                    element.elementId(), getDocumentOrEmpty(element.variables())));

    brokerRequest.cancelRemainingInstances(request.cancelRemainingInstances());

    return sendBrokerRequest(brokerRequest);
  }

  public record AdHocSubProcessActivateActivitiesRequest(
      long adHocSubProcessInstanceKey,
      List<AdHocSubProcessActivateActivityReference> elements,
      boolean cancelRemainingInstances) {
    public record AdHocSubProcessActivateActivityReference(
        String elementId, Map<String, Object> variables) {}
  }
}
