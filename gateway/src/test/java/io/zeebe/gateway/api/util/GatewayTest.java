/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.api.util;

import io.zeebe.gateway.impl.broker.BrokerClient;
import io.zeebe.gateway.protocol.GatewayGrpc.GatewayBlockingStub;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;

public class GatewayTest {

  @Rule public StubbedGatewayRule gatewayRule = new StubbedGatewayRule();

  protected StubbedGateway gateway;
  protected GatewayBlockingStub client;
  protected BrokerClient brokerClient;

  @Before
  public void setUp() throws IOException {
    gateway = gatewayRule.getGateway();
    client = gatewayRule.getClient();
    brokerClient = gateway.buildBrokerClient();
  }
}
