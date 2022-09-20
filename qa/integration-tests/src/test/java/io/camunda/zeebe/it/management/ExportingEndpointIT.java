/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.management;

import static io.camunda.zeebe.it.management.ExportingEndpointIT.StableValuePredicate.hasStableValue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import feign.FeignException;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.actuator.ExportingActuator;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.testcontainers.ZeebeTestContainerDefaults;
import io.zeebe.containers.cluster.ZeebeCluster;
import io.zeebe.containers.exporter.DebugReceiver;
import java.time.Duration;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Network;

final class ExportingEndpointIT {

  @Test
  void shouldPauseExporting() {
    final var exportedRecords = new CopyOnWriteArrayList<>();
    try (final var receiver = new DebugReceiver(exportedRecords::add).start()) {
      try (final var cluster =
          ZeebeCluster.builder()
              .withImage(ZeebeTestContainerDefaults.defaultTestImage())
              .withEmbeddedGateway(true)
              .withBrokerConfig(
                  zeebeBrokerNode ->
                      zeebeBrokerNode.withDebugExporter(receiver.serverAddress().getPort()))
              .withBrokersCount(3)
              .withPartitionsCount(3)
              .withReplicationFactor(3)
              .build()) {
        cluster.start();

        try (final var client = cluster.newClientBuilder().build()) {
          deployProcess(client);
          startProcess(client);

          final var recordsBeforePause =
              Awaitility.await()
                  .atMost(Duration.ofSeconds(30))
                  .during(Duration.ofSeconds(5))
                  .until(exportedRecords::size, hasStableValue());

          // when
          ExportingActuator.of(cluster.getAvailableGateway()).pause();
          startProcess(client);

          // then
          Awaitility.await()
              .atMost(Duration.ofSeconds(30))
              .during(Duration.ofSeconds(10))
              .failFast(() -> assertThat(exportedRecords).hasSize(recordsBeforePause));

          Awaitility.await().untilAsserted(() -> allPartitionsPaused(cluster));
        }
      }
    }
  }

  @Test
  void failsIfMemberIsShutdown() {
    try (final var cluster =
        ZeebeCluster.builder()
            .withImage(ZeebeTestContainerDefaults.defaultTestImage())
            .withEmbeddedGateway(true)
            .withBrokersCount(3)
            .withPartitionsCount(1)
            .withReplicationFactor(3)
            .build()) {
      // given
      cluster.start();

      // when
      //noinspection resource
      cluster.getBrokers().values().stream().findAny().orElseThrow().stop();

      // then
      final var actuator = ExportingActuator.of(cluster.getAvailableGateway());
      assertThatExceptionOfType(FeignException.class)
          .isThrownBy(actuator::pause)
          .returns(500, FeignException::status);
    }
  }

  @Test
  void succeedsWithoutConfiguredExporters() {
    try (final var cluster =
        ZeebeCluster.builder()
            .withImage(ZeebeTestContainerDefaults.defaultTestImage())
            .withEmbeddedGateway(true)
            .withBrokersCount(1)
            .withPartitionsCount(1)
            .withReplicationFactor(1)
            .withNetwork(Network.newNetwork())
            .build()) {
      // given
      cluster.start();

      // then
      assertThatCode(() -> ExportingActuator.of(cluster.getAvailableGateway()).pause())
          .doesNotThrowAnyException();
    }
  }

  private static void startProcess(final ZeebeClient client) {
    client
        .newCreateInstanceCommand()
        .bpmnProcessId("processId")
        .latestVersion()
        .withResult()
        .send()
        .join();
  }

  private static void deployProcess(final ZeebeClient client) {
    client
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess("processId").startEvent().endEvent().done(),
            "process.bpmn")
        .send()
        .join();
  }

  private void allPartitionsPaused(final ZeebeCluster cluster) {
    for (final var broker : cluster.getBrokers().values()) {
      assertThat(PartitionsActuator.of(broker).query().values())
          .allMatch(
              status -> status.exporterPhase() == null || status.exporterPhase().equals("PAUSED"),
              "All exporters should be paused");
    }
  }

  static final class StableValuePredicate<T> implements Predicate<T> {

    final AtomicReference<T> lastSeen = new AtomicReference<>();

    /**
     * Used in combination with {@link Awaitility}'s {@link
     * org.awaitility.core.ConditionFactory#during(Duration)} to ensure that an expression maintains
     * an arbitrary value over time.
     *
     * @return a predicate that accepts a value if it is the same value that was checked in the
     *     previous call to this predicate.
     */
    static <T> StableValuePredicate<T> hasStableValue() {
      return new StableValuePredicate<>();
    }

    @Override
    public boolean test(final T t) {
      return t == lastSeen.getAndSet(t);
    }
  }
}
