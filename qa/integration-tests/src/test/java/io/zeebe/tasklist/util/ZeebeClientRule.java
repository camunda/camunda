/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.util;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.ZeebeClientBuilder;
import io.zeebe.client.api.command.ClientException;
import io.zeebe.client.api.response.Topology;
import io.zeebe.test.EmbeddedBrokerRule;
import java.net.InetSocketAddress;
import java.util.function.Consumer;
import org.junit.rules.ExternalResource;

public class ZeebeClientRule extends ExternalResource {

  protected ZeebeClient client;
  private final Consumer<ZeebeClientBuilder> configurator;

  public ZeebeClientRule(final EmbeddedBrokerRule brokerRule) {
    this(brokerRule, ZeebeClientBuilder::usePlaintext);
  }

  public ZeebeClientRule(
      final EmbeddedBrokerRule brokerRule, final Consumer<ZeebeClientBuilder> configurator) {
    this(
        config -> {
          config.gatewayAddress(toHostAndPortString(brokerRule.getGatewayAddress()));
          configurator.accept(config);
        });
  }

  private ZeebeClientRule(final Consumer<ZeebeClientBuilder> configurator) {
    this.configurator = configurator;
  }

  private static String toHostAndPortString(InetSocketAddress inetSocketAddress) {
    final String host = inetSocketAddress.getHostString();
    final int port = inetSocketAddress.getPort();
    return host + ":" + port;
  }

  @Override
  public void before() {
    final ZeebeClientBuilder builder = ZeebeClient.newClientBuilder();
    configurator.accept(builder);
    client = builder.build();

    // get topology to check that cluster is available and ready for work
    Topology topology = null;
    while (topology == null) {
      try {
        topology = client.newTopologyRequest().send().join();
      } catch (ClientException ex) {
        ex.printStackTrace();
      }
    }
  }

  @Override
  public void after() {
    client.close();
    client = null;
  }

  public ZeebeClient getClient() {
    return client;
  }
}
