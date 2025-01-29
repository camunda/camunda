/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.search.clients.AlertDefinitionClient;
import io.camunda.search.entities.AlertDefinitionEntity;
import io.camunda.security.auth.Authentication;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import java.util.List;

public class AlertDefinitionServices extends ApiServices<AlertDefinitionServices> {

  private final AlertDefinitionClient alertDefinitionClient;

  public AlertDefinitionServices(
      final AlertDefinitionClient alertDefinitionClient,
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final Authentication authentication) {
    super(brokerClient, securityContextProvider, authentication);
    this.alertDefinitionClient = alertDefinitionClient;
  }

  @Override
  public AlertDefinitionServices withAuthentication(final Authentication authentication) {
    return new AlertDefinitionServices(
        alertDefinitionClient, brokerClient, securityContextProvider, authentication);
  }

  public void store(final AlertDefinitionEntity alertDefinition) {
    alertDefinitionClient.store(alertDefinition);
  }

  public List<AlertDefinitionEntity> query() {
    return alertDefinitionClient.query();
  }
}
