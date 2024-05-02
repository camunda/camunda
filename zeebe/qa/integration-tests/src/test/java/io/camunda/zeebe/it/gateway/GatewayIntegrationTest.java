/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerRejectionException;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCreateProcessInstanceRequest;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
final class GatewayIntegrationTest {
  @TestZeebe
  private final TestCluster cluster =
      TestCluster.builder()
          .withEmbeddedGateway(false)
          .withGatewaysCount(1)
          .withBrokersCount(1)
          .build();

  @Test
  void shouldReturnRejectionWithCorrectTypeAndReason() throws InterruptedException {
    // given
    final var gateway = cluster.availableGateway();
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
