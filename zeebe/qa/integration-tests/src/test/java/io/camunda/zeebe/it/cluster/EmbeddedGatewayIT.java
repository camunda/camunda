/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.cluster.TestZeebePort;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The gateway starts next to the broker only when {@code zeebe.broker.gateway.enable} is set, which
 * is the default. The unified {@code camunda.api.enabled} switches it off as well.
 */
@Timeout(120)
@ZeebeIntegration
final class EmbeddedGatewayIT {

  @TestZeebe(autoStart = false)
  private final TestStandaloneBroker broker = new TestStandaloneBroker();

  @Test
  void shouldServeGrpcRequestsByDefault() {
    // given
    broker.start();

    try (final var client = broker.newClientBuilder().build()) {
      // when
      final var topology = client.newTopologyRequest().send().join();

      // then
      assertThat(topology.getBrokers()).hasSize(1);
    }
  }

  @ParameterizedTest
  @MethodSource("disablingSettings")
  void shouldNotListenOnTheGatewayPortWhenDisabled(final Consumer<TestStandaloneBroker> disable) {
    // given
    disable.accept(broker);
    broker.start();
    final var address =
        new InetSocketAddress("localhost", broker.mappedPort(TestZeebePort.GATEWAY));

    // when - then
    assertThatThrownBy(() -> new Socket().connect(address, 5_000)).isInstanceOf(IOException.class);
  }

  private static Stream<Arguments> disablingSettings() {
    return Stream.of(
        Arguments.of(
            Named.<Consumer<TestStandaloneBroker>>of(
                "zeebe.broker.gateway.enable=false", b -> b.withGatewayEnabled(false))),
        Arguments.of(
            Named.<Consumer<TestStandaloneBroker>>of(
                "camunda.api.enabled=false", b -> b.withProperty("camunda.api.enabled", false))));
  }
}
