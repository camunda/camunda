/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.clustering.dynamic;

import static org.assertj.core.api.Assertions.*;

import feign.FeignException;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.util.collection.Tuple;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@Timeout(2 * 60) // 2 minutes
@ZeebeIntegration
final class ClusterEndpointErrorResponseIT {
  @Nested
  class InvalidRequests {
    @TestZeebe(awaitCompleteTopology = false, awaitReady = false)
    static TestCluster cluster =
        TestCluster.builder()
            .withEmbeddedGateway(true)
            .withBrokersCount(1)
            .withPartitionsCount(1)
            .withReplicationFactor(1)
            .withBrokerConfig(
                broker ->
                    broker
                        .brokerConfig()
                        .getExperimental()
                        .getFeatures()
                        .setEnableDynamicClusterTopology(true))
            .build();

    @Test
    void shouldRejectWith409WhenOngoingOperation() {
      // given
      final var actuator = ClusterActuator.of(cluster.anyGateway());
      actuator.scaleBrokers(List.of(0, 1));
      // operation cannot complete because broker 1 is not up

      // when
      assertThatThrownBy(() -> actuator.scaleBrokers(List.of(0, 1)))
          .asInstanceOf(InstanceOfAssertFactories.type(FeignException.class))
          .extracting(FeignException::status)
          .isEqualTo(409);
    }

    @ParameterizedTest
    @MethodSource("provideInvalidRequests")
    void shouldRejectWith400WhenInvalidRequest(final Consumer<ClusterActuator> operation) {
      // given
      final var actuator = ClusterActuator.of(cluster.anyGateway());

      // when
      assertThatThrownBy(() -> operation.accept(actuator))
          .asInstanceOf(InstanceOfAssertFactories.type(FeignException.class))
          .extracting(FeignException::status)
          .isEqualTo(400);
    }

    static Stream<Arguments> provideInvalidRequests() {
      final List<Tuple<String, Consumer<ClusterActuator>>> operations =
          List.of(
              Tuple.of("Empty scale request", a -> a.scaleBrokers(List.of())),
              Tuple.of(
                  "Scale request with invalid brokerId",
                  a -> a.scaleBrokersInvalidType(List.of("a", "b", "c"))),
              Tuple.of(
                  "Add broker request with invalid brokerId", a -> a.addBrokerInvalidType("a")));
      return operations.stream().map(c -> Arguments.of(Named.of(c.getLeft(), c.getRight())));
    }
  }

  @Nested
  class CoordinatorNotKnown {
    @TestZeebe(awaitCompleteTopology = false, awaitReady = false)
    static TestCluster cluster =
        TestCluster.builder()
            .withGatewaysCount(1)
            .withEmbeddedGateway(false)
            .withBrokersCount(0)
            .build();

    @ParameterizedTest
    @MethodSource("provideRequests")
    void shouldRejectWith502WhenBrokerNotReachable(final Consumer<ClusterActuator> operation) {
      // given
      final var actuator = ClusterActuator.of(cluster.anyGateway());
      cluster.brokers().forEach((id, broker) -> broker.stop());

      // when
      assertThatThrownBy(() -> operation.accept(actuator))
          .asInstanceOf(InstanceOfAssertFactories.type(FeignException.class))
          .extracting(FeignException::status)
          .isEqualTo(502);
    }

    static Stream<Arguments> provideRequests() {
      final List<Consumer<ClusterActuator>> operations =
          List.of(ClusterActuator::getTopology, a -> a.scaleBrokers(List.of(0, 1)));
      return operations.stream().map(Arguments::of);
    }
  }
}
