/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.multidb;

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.qa.util.auth.Authenticated;
import java.net.URI;

public interface CamundaClientTestFactory extends AutoCloseable {

  /**
   * Returns a client for the default user. The default user can be setup using the security
   * configuration.
   */
  CamundaClient getAdminCamundaClient();

  /** Returns a client for the given id */
  CamundaClient getCamundaClient(final String id);

  /** Returns a Camunda client for the given gateway and authenticated user */
  CamundaClient getCamundaClient(
      final CamundaClientBuilder camundaClientBuilder,
      final URI restAddress,
      final Authenticated authenticated);
}
