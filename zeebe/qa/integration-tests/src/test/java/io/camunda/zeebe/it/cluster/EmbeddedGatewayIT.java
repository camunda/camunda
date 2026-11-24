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

import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.cluster.TestZeebePort;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorScheduler;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
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

  @ParameterizedTest(name = "preferRestOverGrpc={0}")
  @CsvSource({
    "false, Expected to execute command on partition 999",
    "true, request could not be delivered"
  })
  void shouldServeRequestsWhileTheBrokerActorThreadsAreBlocked(
      final boolean preferRest, final String expectedError) throws InterruptedException {
    // given - the only actor thread of the broker is blocked
    broker.withUnifiedConfig(cfg -> cfg.getSystem().setCpuThreadCount(1)).start();
    final var release = new CountDownLatch(1);
    final var blocked = new CountDownLatch(1);
    broker
        .bean(ActorScheduler.class)
        .submitActor(
            Actor.newActor()
                .name("BlockBrokerActorThread")
                .actorStartedHandler(control -> blockUntilReleased(blocked, release))
                .build());
    blocked.await();

    try (final var client = broker.newClientBuilder().preferRestOverGrpc(preferRest).build()) {
      // when - the broker client rejects the request itself, without a partition
      final var response =
          client
              .newCompleteCommand(Protocol.encodePartitionId(999, 1))
              .send()
              .toCompletableFuture();

      // then
      assertThat(response)
          .failsWithin(Duration.ofSeconds(30))
          .withThrowableThat()
          .withMessageContaining(expectedError);
    } finally {
      release.countDown();
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

  private static void blockUntilReleased(
      final CountDownLatch blocked, final CountDownLatch release) {
    blocked.countDown();
    try {
      release.await();
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    }
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
