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

public final class CamundaServices extends ApiServices<CamundaServices> {

  public CamundaServices(final CamundaSearchClient searchClient) {
    this(searchClient, null, null);
  }

  public CamundaServices(
      final CamundaSearchClient searchClient,
      final ServiceTransformers transformers,
      final Authentication authentication) {
    super(searchClient, transformers, authentication);
  }

  public ProcessInstanceServices processInstanceServices() {
    return new ProcessInstanceServices(searchClient, transformers, authentication);
  }

  public UserTaskServices userTaskServices() {
    return new UserTaskServices(searchClient, transformers, authentication);
  }

  public VariableServices variableServices() {
    return new VariableServices(searchClient, transformers, authentication);
  }

  @Override
  public CamundaServices withAuthentication(final Authentication authentication) {
    return new CamundaServices(searchClient, transformers, authentication);
  }
}
