/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the client-side load balancing option can be enabled without breaking gRPC or HTTP
 * communication. With a single-node setup the round-robin behaviour is transparent, so we simply
 * assert that requests succeed over both protocols.
 */
@ZeebeIntegration
class ClientSideLoadBalancingIT {

  @TestZeebe
  private final TestStandaloneBroker broker =
      new TestStandaloneBroker().withUnauthenticatedAccess();

  @AutoClose private CamundaClient camundaClient;

  @BeforeEach
  void beforeEach() {
    camundaClient = broker.newClientBuilder().useClientSideLoadBalancing(true).build();
  }

  @Test
  void shouldSucceedWithGrpcWhenClientSideLoadBalancingEnabled() {
    // when
    final var topology = camundaClient.newTopologyRequest().useGrpc().send().join();

    // then
    assertThat(topology.getBrokers()).hasSize(1);
  }

  @Test
  void shouldSucceedWithRestWhenClientSideLoadBalancingEnabled() {
    // when
    final var topology = camundaClient.newTopologyRequest().useRest().send().join();

    // then
    assertThat(topology.getBrokers()).hasSize(1);
  }
}
