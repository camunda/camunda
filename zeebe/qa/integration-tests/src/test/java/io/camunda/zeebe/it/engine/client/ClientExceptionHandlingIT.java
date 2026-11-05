/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.engine.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientException;
import io.camunda.client.impl.util.AddressUtil;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.grpc.StatusRuntimeException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;

/** No broker is started: the client is pointed at an address nobody listens on. */
final class ClientExceptionHandlingIT {

  private final InetSocketAddress invalidGatewayAddress = SocketUtil.getNextAddress();

  @Test
  void shouldContainRootCauses() {
    // given
    try (final var client =
        CamundaClient.newClientBuilder()
            .preferRestOverGrpc(false)
            .defaultRequestTimeout(Duration.ofSeconds(10))
            .grpcAddress(
                AddressUtil.composeGrpcAddress(
                    invalidGatewayAddress.getHostName() + ":" + invalidGatewayAddress.getPort(),
                    true))
            .build()) {

      // when
      final Throwable throwable = catchThrowable(() -> client.newTopologyRequest().send().join());

      // then
      assertThat(throwable)
          .isInstanceOf(ClientException.class)
          .hasMessageContaining("io exception");

      final Throwable firstCause = throwable.getCause();
      assertThat(firstCause)
          .isInstanceOf(ExecutionException.class)
          .hasMessageContaining("io.grpc.StatusRuntimeException: UNAVAILABLE: io exception");

      final Throwable secondCause = firstCause.getCause();
      assertThat(secondCause)
          .isInstanceOf(StatusRuntimeException.class)
          .hasMessageContaining("UNAVAILABLE: io exception");

      final Throwable thirdCause = secondCause.getCause();
      assertThat(thirdCause)
          .hasCauseInstanceOf(ConnectException.class)
          .hasMessageContaining("Connection refused:")
          .hasMessageContaining(invalidGatewayAddress.getHostName())
          .hasMessageContaining(":" + invalidGatewayAddress.getPort());
    }
  }
}
