/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.multidb;

import io.camunda.client.CamundaClient;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.TestMapping;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.zeebe.qa.util.cluster.TestGateway;

public interface CamundaClientTestFactory extends AutoCloseable {

  /**
   * Returns a client for the default user. The default user can be setup using the security
   * configuration.
   */
  CamundaClient getAdminCamundaClient();

  /** Returns a client for the given username */
  CamundaClient getCamundaClient(final String username);

  /** Returns a Camunda client for the given gateway and authenticated user */
  CamundaClient getCamundaClient(final TestGateway<?> gateway, final Authenticated authenticated);

  /** Creates a Camunda client for the given user. Only implemented for basic auth! */
  default void createClientForUser(final TestGateway<?> gateway, final TestUser user) {}

  /** Creates a Camunda client for the given mapping. Only implemented for OIDC! */
  default void createClientForMapping(
      final TestGateway<?> gateway, final TestMapping mappingName) {}
}
