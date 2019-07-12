/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.it.shutdown;

import io.zeebe.broker.Broker;
import io.zeebe.broker.it.GrpcClientRule;
import io.zeebe.broker.system.configuration.NetworkCfg;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.broker.transport.TransportServiceNames;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.transport.SocketAddress;
import io.zeebe.transport.impl.ServerSocketBinding;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.net.InetSocketAddress;
import java.time.Duration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

public class BrokerShutdownTest {

  private static final ServiceName<Void> BLOCK_BROKER_SERVICE_NAME =
      ServiceName.newServiceName("blockService", Void.class);

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public GrpcClientRule clientRule = new GrpcClientRule(brokerRule);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

  @Rule public Timeout timeout = Timeout.seconds(60);

  @Test
  public void shouldReleaseSockets() {
    // given
    brokerRule.installService(
        serviceContainer ->
            serviceContainer
                .createService(BLOCK_BROKER_SERVICE_NAME, new BlockingService())
                .dependency(
                    TransportServiceNames.serverTransport(
                        TransportServiceNames.COMMAND_API_SERVER_NAME)));

    final Broker broker = brokerRule.getBroker();
    broker.getBrokerContext().setCloseTimeout(Duration.ofSeconds(1));

    // when
    broker.close();

    // then
    final NetworkCfg networkCfg = broker.getBrokerContext().getBrokerConfiguration().getNetwork();

    tryToBindSocketAddress(networkCfg.getCommandApi().toSocketAddress());
  }

  private void tryToBindSocketAddress(SocketAddress socketAddress) {
    final InetSocketAddress socket = socketAddress.toInetSocketAddress();
    final ServerSocketBinding binding = new ServerSocketBinding(socket);
    binding.doBind();
    binding.close();
  }

  private class BlockingService implements Service<Void> {
    @Override
    public void start(ServiceStartContext startContext) {}

    @Override
    public void stop(ServiceStopContext stopContext) {
      final CompletableActorFuture<Void> neverCompletingFuture = new CompletableActorFuture<>();
      stopContext.async(neverCompletingFuture);
    }

    @Override
    public Void get() {
      return null;
    }
  }
}
