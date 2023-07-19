/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.management;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.qa.util.actuator.BanningActuator;
import io.camunda.zeebe.qa.util.testcontainers.ZeebeTestContainerDefaults;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.zeebe.containers.cluster.ZeebeCluster;
import io.zeebe.containers.exporter.DebugReceiver;
import java.util.concurrent.CopyOnWriteArrayList;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class BanningEndpointIT {

  private static final CopyOnWriteArrayList<Record<?>> EXPORTED_RECORDS =
      new CopyOnWriteArrayList<>();
  private static final DebugReceiver DEBUG_RECEIVER =
      new DebugReceiver(EXPORTED_RECORDS::add, SocketUtil.getNextAddress()).start();

  @Container
  private static final ZeebeCluster CLUSTER =
      ZeebeCluster.builder()
          .withImage(ZeebeTestContainerDefaults.defaultTestImage())
          .withBrokerConfig(
              broker -> broker.withDebugExporter(DEBUG_RECEIVER.serverAddress().getPort()))
          .withEmbeddedGateway(true)
          .withBrokersCount(2)
          .withPartitionsCount(1)
          .withReplicationFactor(2)
          .build();

  @BeforeAll
  public static void setup() {
    DEBUG_RECEIVER.start();
  }

  @AfterAll
  public static void teardown() {
    DEBUG_RECEIVER.stop();
  }

  @BeforeEach
  public void cleanup() {
    EXPORTED_RECORDS.clear();
  }

  @Test
  void shouldBanInstance() {
    // given - a process instance
    final var actuator = BanningActuator.of(CLUSTER.getBrokers().get(0));
    final long processInstanceKey;
    try (final var client = CLUSTER.newClientBuilder().build()) {
      final var process = Bpmn.createExecutableProcess("processId").startEvent().endEvent().done();
      client.newDeployResourceCommand().addProcessModel(process, "process.bpmn").send().join();

      final var result =
          client
              .newCreateInstanceCommand()
              .bpmnProcessId("processId")
              .latestVersion()
              .send()
              .join();
      processInstanceKey = result.getProcessInstanceKey();
    }

    // when & then - process instance can be banned
    Assertions.assertThatCode(() -> actuator.ban(processInstanceKey)).doesNotThrowAnyException();
  }

  @Test
  void shouldWriteErrorEventWhenBanningInstance() {
    // given
    final var actuator = BanningActuator.of(CLUSTER.getBrokers().get(0));
    final long processInstanceKey;
    try (final var client = CLUSTER.newClientBuilder().build()) {
      final var process = Bpmn.createExecutableProcess("processId").startEvent().endEvent().done();
      client.newDeployResourceCommand().addProcessModel(process, "process.bpmn").send().join();

      final var result =
          client
              .newCreateInstanceCommand()
              .bpmnProcessId("processId")
              .latestVersion()
              .send()
              .join();
      processInstanceKey = result.getProcessInstanceKey();
    }

    // when
    actuator.ban(processInstanceKey);

    // then
    Awaitility.await()
        .untilAsserted(
            () -> {
              final var errorEvents =
                  EXPORTED_RECORDS.stream()
                      .filter(record -> record.getRecordType() == RecordType.EVENT)
                      .filter(record -> record.getValueType() == ValueType.ERROR)
                      .filter(record -> record.getKey() == processInstanceKey)
                      .toList();
              Assertions.assertThat(errorEvents).isNotEmpty();
            });
  }
}
