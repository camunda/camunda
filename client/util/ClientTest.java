/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.client.util;

import io.camunda.zeebe.client.ZeebeClient;
import org.junit.Before;
import org.junit.Rule;

public abstract class ClientTest {

  @Rule public final TestEnvironmentRule rule = new TestEnvironmentRule();

  public RecordingGatewayService gatewayService;
  public ZeebeClient client;

  @Before
  public void setUp() {
    gatewayService = rule.getGatewayService();
    client = rule.getClient();
  }
}
