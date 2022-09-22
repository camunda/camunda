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

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
final class ExportingEndpointIT {
  private static final CopyOnWriteArrayList<Record<?>> EXPORTED_RECORDS =
      new CopyOnWriteArrayList<>();

  private static final DebugReceiver DEBUG_RECEIVER =
      new DebugReceiver(EXPORTED_RECORDS::add).start();
  private static ZeebeClient client;

  @Container
  private static final ZeebeCluster CLUSTER =
      ZeebeCluster.builder()
          .withImage(ZeebeTestContainerDefaults.defaultTestImage())
          .withEmbeddedGateway(true)
          .withBrokerConfig(
              zeebeBrokerNode ->
                  zeebeBrokerNode.withDebugExporter(DEBUG_RECEIVER.serverAddress().getPort()))
          .withBrokersCount(2)
          .withPartitionsCount(2)
          .withReplicationFactor(1)
          .build();

  @BeforeEach
  void resetExportedRecords() {
    EXPORTED_RECORDS.clear();
  }

  @BeforeAll
  static void setupClient() {
    client = CLUSTER.newClientBuilder().build();
  }

  @AfterAll
  static void closeClient() {
    client.close();
  }

  @Test
  void shouldPauseExporting() {

    deployProcess(client);
    startProcess(client);

    final var recordsBeforePause =
        Awaitility.await()
            .atMost(Duration.ofSeconds(30))
            .during(Duration.ofSeconds(5))
            .until(EXPORTED_RECORDS::size, hasStableValue());

    // when
    ExportingActuator.of(CLUSTER.getAvailableGateway()).pause();
    startProcess(client);

    // then
    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .during(Duration.ofSeconds(10))
        .failFast(() -> assertThat(EXPORTED_RECORDS).hasSize(recordsBeforePause));

    Awaitility.await().untilAsserted(this::allPartitionsPausedExporting);
  }

  @Test
  void shouldResumeExporting() {
    // given
    final var actuator = ExportingActuator.of(CLUSTER.getAvailableGateway());
    actuator.pause();

    deployProcess(client);
    startProcess(client);

    final var recordsBeforePause =
        Awaitility.await()
            .atMost(Duration.ofSeconds(30))
            .during(Duration.ofSeconds(5))
            .until(EXPORTED_RECORDS::size, hasStableValue());

    // when
    ExportingActuator.of(CLUSTER.getAvailableGateway()).resume();
    startProcess(client);

    // then
    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .during(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(EXPORTED_RECORDS).hasSizeGreaterThan(recordsBeforePause));

    Awaitility.await().untilAsserted(this::allPartitionsExporting);
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

  private void allPartitionsPausedExporting() {
    for (final var broker : ExportingEndpointIT.CLUSTER.getBrokers().values()) {
      assertThat(PartitionsActuator.of(broker).query().values())
          .allMatch(
              status -> status.exporterPhase() == null || status.exporterPhase().equals("PAUSED"),
              "All exporters should be paused");
    }
  }

  private void allPartitionsExporting() {
    for (final var broker : ExportingEndpointIT.CLUSTER.getBrokers().values()) {
      assertThat(PartitionsActuator.of(broker).query().values())
          .allMatch(
              status ->
                  status.exporterPhase() == null || status.exporterPhase().equals("EXPORTING"),
              "All exporters should be running");
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
