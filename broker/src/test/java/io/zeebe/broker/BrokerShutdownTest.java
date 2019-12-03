/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.transport.SocketAddress;
import io.zeebe.transport.impl.ServerSocketBinding;
import java.net.InetSocketAddress;
import org.junit.Rule;

public class BrokerShutdownTest {
  @Rule public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();

  //  @Test
  //  public void shouldReleaseSockets() {
  //    // given
  //    brokerRule.installService(
  //        serviceContainer ->
  //            serviceContainer
  //                .createService(BLOCK_BROKER_SERVICE_NAME, new BlockingService())
  //                .dependency(
  //                    TransportServiceNames.serverTransport(
  //                        TransportServiceNames.COMMAND_API_SERVER_NAME)));
  //
  //    final Broker broker = brokerRule.getBroker();
  //    broker.getBrokerContext().setCloseTimeout(Duration.ofSeconds(1));
  //
  //    // when
  //    broker.close();
  //
  //    // then
  //    final NetworkCfg networkCfg =
  // broker.getBrokerContext().getBrokerConfiguration().getNetwork();
  //
  //    tryToBindSocketAddress(networkCfg.getCommandApi().getAddress());
  //  }

  private void tryToBindSocketAddress(SocketAddress socketAddress) {
    final InetSocketAddress socket = socketAddress.toInetSocketAddress();
    final ServerSocketBinding binding = new ServerSocketBinding(socket);
    binding.doBind();
    binding.close();
  }
}
