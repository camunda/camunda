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
import io.camunda.zeebe.qa.util.cluster.TestStandaloneCluster;
import io.camunda.zeebe.qa.util.cluster.junit.ManageTestNodes;
import io.camunda.zeebe.qa.util.cluster.junit.ManageTestNodes.TestCluster;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

@ManageTestNodes
final class GatewayIntegrationTest {
  @TestCluster
  private final TestStandaloneCluster cluster =
      TestStandaloneCluster.builder()
          .withBrokersCount(1)
          .withGatewaysCount(1)
          .withEmbeddedGateway(false)
          .build();

  @Test
  void shouldReturnRejectionWithCorrectTypeAndReason() throws InterruptedException {
    // given
    final var latch = new CountDownLatch(1);
    final AtomicReference<Throwable> errorResponse = new AtomicReference<>();
    //noinspection resource
    final var gateway = cluster.gateways().values().stream().findFirst().orElseThrow();
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
