/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.gateway.protocol.GatewayGrpc;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneGateway;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.grpc.ManagedChannel;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthCheckResponse.ServingStatus;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.protobuf.services.HealthStatusManager;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@ZeebeIntegration
class GatewayGrpcHealthIT {

  // can never be ready nor await the complete topology without a broker
  @TestZeebe(awaitReady = false, awaitCompleteTopology = false)
  private final TestStandaloneGateway gateway = new TestStandaloneGateway();

  @ParameterizedTest
  @ValueSource(strings = {GatewayGrpc.SERVICE_NAME, HealthStatusManager.SERVICE_NAME_ALL_SERVICES})
  void shouldReturnServingStatusWhenGatewayIsStarted(final String serviceName) {
    // given
    final var address = gateway.grpcAddress();
    final ManagedChannel channel =
        NettyChannelBuilder.forAddress(address.getHost(), address.getPort()).usePlaintext().build();
    final HealthCheckResponse response;

    // when
    try {
      final var client = HealthGrpc.newBlockingStub(channel);
      response = client.check(HealthCheckRequest.newBuilder().setService(serviceName).build());
    } finally {
      channel.shutdownNow();
    }

    // then
    assertThat(response.getStatus()).isEqualTo(ServingStatus.SERVING);
  }
}
