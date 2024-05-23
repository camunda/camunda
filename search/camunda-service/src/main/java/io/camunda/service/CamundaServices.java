/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.data.clients.DataStoreClient;
import io.camunda.service.auth.Authentication;

public final class CamundaServices extends ApiServices<CamundaServices> {

  public CamundaServices(final DataStoreClient dataStoreClient) {
    this(dataStoreClient, null);
  }

  public CamundaServices(
      final DataStoreClient dataStoreClient, final Authentication authentication) {
    super(dataStoreClient, authentication);
  }

  public ProcessInstanceServices processInstanceServices() {
    return new ProcessInstanceServices(dataStoreClient, authentication);
  }

  @Override
  public CamundaServices withAuthentication(final Authentication authentication) {
    return new CamundaServices(dataStoreClient, authentication);
  }
}
