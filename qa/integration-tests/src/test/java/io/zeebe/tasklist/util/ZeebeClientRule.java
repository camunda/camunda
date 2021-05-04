/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.util;

import static io.zeebe.tasklist.util.ConversionUtils.toHostAndPortAsString;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.ZeebeClientBuilder;
import io.camunda.zeebe.client.api.command.ClientException;
import io.camunda.zeebe.client.api.response.Topology;
import io.camunda.zeebe.test.EmbeddedBrokerRule;
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
