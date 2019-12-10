/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.system.configuration.NetworkCfg;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.transport.SocketAddress;
import io.zeebe.transport.impl.ServerSocketBinding;
import java.net.InetSocketAddress;
import org.junit.Rule;
import org.junit.Test;

public class BrokerTest {

  @Rule public final EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();

  @Test
  public void shouldStartAndStopBroker() {
    // given broker started
    final Broker broker = brokerRule.getBroker();
    assertThat(broker).isNotNull();

    // when
    brokerRule.stopBroker();

    // then - no error
    assertThat(brokerRule.getBroker()).isNull();
  }

  @Test
  public void shouldReleaseSockets() {
    // given
    final Broker broker = brokerRule.getBroker();

    // when
    broker.close();

    // then
    final NetworkCfg networkCfg = broker.getBrokerContext().getBrokerConfiguration().getNetwork();

    tryToBindSocketAddress(networkCfg.getCommandApi().getAddress());
  }

  private void tryToBindSocketAddress(SocketAddress socketAddress) {
    final InetSocketAddress socket = socketAddress.toInetSocketAddress();
    final ServerSocketBinding binding = new ServerSocketBinding(socket);
    binding.doBind();
    binding.close();
  }
}
