/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.util;

import static org.camunda.operate.util.ConversionUtils.toHostAndPortAsString;

import io.zeebe.client.api.command.ClientException;
import io.zeebe.client.api.response.Topology;
import java.net.InetSocketAddress;
import java.util.function.Consumer;
import org.junit.rules.ExternalResource;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.ZeebeClientBuilder;
import io.zeebe.test.EmbeddedBrokerRule;

public class ZeebeClientRule extends ExternalResource {

  private final Consumer<ZeebeClientBuilder> configurator;

  protected ZeebeClient client;

  public ZeebeClientRule(final EmbeddedBrokerRule brokerRule) {
    this(brokerRule, ZeebeClientBuilder::usePlaintext);
  }

  public ZeebeClientRule(
    final EmbeddedBrokerRule brokerRule, final Consumer<ZeebeClientBuilder> configurator) {
    this(
      config -> {
        config.gatewayAddress(toHostAndPortAsString(brokerRule.getGatewayAddress()));
        configurator.accept(config);
      });
  }

  private ZeebeClientRule(final Consumer<ZeebeClientBuilder> configurator) {
    this.configurator = configurator;
  }

  @Override
  public void before() {
    final ZeebeClientBuilder builder = ZeebeClient.newClientBuilder();
    configurator.accept(builder);
    client = builder.build();

    //get topology to check that cluster is available and ready for work
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
