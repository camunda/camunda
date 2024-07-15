/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.search.clients.CamundaSearchClient;
import io.camunda.service.security.auth.Authentication;
import io.camunda.service.transformers.ServiceTransformers;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.job.ActivateJobsHandler;

public final class CamundaServices extends ApiServices<CamundaServices> {

  public CamundaServices(final BrokerClient brokerClient, final CamundaSearchClient searchClient) {
    this(brokerClient, searchClient, null, null);
  }

  public CamundaServices(
      final BrokerClient brokerClient,
      final CamundaSearchClient searchClient,
      final ServiceTransformers transformers,
      final Authentication authentication) {
    super(brokerClient, searchClient, transformers, authentication);
  }

  public <T> JobServices<T> jobServices(final ActivateJobsHandler<T> activateJobsHandler) {
    return new JobServices<>(
        brokerClient, activateJobsHandler, searchClient, transformers, authentication);
  }

  public ProcessInstanceServices processInstanceServices() {
    return new ProcessInstanceServices(brokerClient, searchClient, transformers, authentication);
  }

  public UserTaskServices userTaskServices() {
    return new UserTaskServices(brokerClient, searchClient, transformers, authentication);
  }

  public VariableServices variableServices() {
    return new VariableServices(brokerClient, searchClient, transformers, authentication);
  }

  public ManagementService managementService() {
    return new ManagementService();
  }

  public <T> IdentityServices<T> identityServices() {
    return new IdentityServices<>(brokerClient, searchClient, transformers, authentication);
  }

  @Override
  public CamundaServices withAuthentication(final Authentication authentication) {
    return new CamundaServices(brokerClient, searchClient, transformers, authentication);
  }
}
