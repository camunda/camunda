/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.Broker;
import io.camunda.zeebe.broker.test.EmbeddedBrokerRule;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class EmbeddedGatewayWithOneCpuThreadIT {

  @Rule
  public EmbeddedBrokerRule brokerRule =
      new EmbeddedBrokerRule(brokerCfg -> brokerCfg.getThreads().setCpuThreadCount(1));

  private Broker broker;
  private ZeebeClient client;

  @Before
  public void setup() {
    broker = brokerRule.getBroker();
    client = createClient();
  }

  private ZeebeClient createClient() {
    final var config = broker.getConfig();
    final var gtwConfig = config.getGateway();
    final var port = gtwConfig.getNetwork().getPort();

    return ZeebeClient.newClientBuilder()
        .usePlaintext()
        .gatewayAddress("localhost:" + port)
        .build();
  }

  @After
  public void tearDown() {
    client.close();
  }

  @Test
  public void shouldStartEmbeddedGateway() {
    // given
    final var context = broker.getBrokerContext();
    final var embeddedGatewayService = context.getEmbeddedGatewayService();

    // when
    // already started by EmbeddedBrokerRule

    // then
    assertThat(embeddedGatewayService).isNotNull();
    assertThat(embeddedGatewayService.get()).isNotNull();
  }

  @Test
  public void shouldDeployProcess() throws InterruptedException {
    // given
    final var process = Bpmn.createExecutableProcess().startEvent().endEvent().done();

    // when
    final var result = client.newDeployCommand().addProcessModel(process, "foo.bpmn").send().join();

    // then
    assertThat(result).isNotNull();
    assertThat(result.getProcesses()).hasSize(1);
  }
}
