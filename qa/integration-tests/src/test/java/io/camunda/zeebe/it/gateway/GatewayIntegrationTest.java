/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.gateway.cmd.BrokerRejectionException;
import io.camunda.zeebe.gateway.impl.broker.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCreateProcessInstanceRequest;
import io.camunda.zeebe.gateway.impl.broker.response.BrokerRejection;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.qa.util.cluster.TestHealthProbe;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneGateway;
import io.camunda.zeebe.qa.util.cluster.TestZeebePort;
import io.camunda.zeebe.test.util.junit.AutoCloseResources;
import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@AutoCloseResources
final class GatewayIntegrationTest {
  @AutoCloseResource
  private final TestStandaloneBroker broker =
      new TestStandaloneBroker().withBrokerConfig(config -> config.getGateway().setEnable(false));

  @AutoCloseResource
  private final TestStandaloneGateway gateway =
      new TestStandaloneGateway()
          .withGatewayConfig(
              config ->
                  config
                      .getCluster()
                      .setInitialContactPoints(List.of(broker.address(TestZeebePort.CLUSTER))));

  @BeforeEach
  void beforeEach() {
    broker.start().await(TestHealthProbe.READY);
    gateway.start().awaitCompleteTopology();
  }

  @Test
  void shouldReturnRejectionWithCorrectTypeAndReason() throws InterruptedException {
    // given
    final var latch = new CountDownLatch(1);
    final AtomicReference<Throwable> errorResponse = new AtomicReference<>();
    final var client = gateway.bean(BrokerClient.class);

    // when
    client.sendRequestWithRetry(
        new BrokerCreateProcessInstanceRequest(),
        (k, r) -> {},
        error -> {
          errorResponse.set(error);
          latch.countDown();
        });

    // then
    latch.await();
    final var error = errorResponse.get();
    assertThat(error).isInstanceOf(BrokerRejectionException.class);
    final BrokerRejection rejection = ((BrokerRejectionException) error).getRejection();
    assertThat(rejection.type()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.reason())
        .isEqualTo("Expected at least a bpmnProcessId or a key greater than -1, but none given");
  }
}
