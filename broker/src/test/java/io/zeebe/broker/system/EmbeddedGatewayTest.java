/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.broker.system;

import static org.assertj.core.api.AssertionsForClassTypes.fail;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import java.net.InetSocketAddress;
import java.net.Socket;
import org.junit.Rule;
import org.junit.Test;

public final class EmbeddedGatewayTest {

  @Rule
  public final EmbeddedBrokerRule brokerWithEnabledGateway =
      new EmbeddedBrokerRule(cfg -> cfg.getGateway().setEnable(true));

  @Rule
  public final EmbeddedBrokerRule brokerWithDisabledGateway =
      new EmbeddedBrokerRule(cfg -> cfg.getGateway().setEnable(false));

  @Test
  public void shouldConfigureGateway() {
    InetSocketAddress address = brokerWithEnabledGateway.getGatewayAddress();
    try (final Socket socket = new Socket(address.getHostName(), address.getPort())) {
      // expect no error
    } catch (final Exception e) {
      fail("Failed to connect to gateway with address: " + address, e);
    }

    address = brokerWithDisabledGateway.getGatewayAddress();
    try (final Socket socket = new Socket(address.getHostName(), address.getPort())) {
      fail("Unexpected to be able to connect to gateway with address: " + address);
    } catch (final Exception e) {
      // expect error
    }
  }
}
