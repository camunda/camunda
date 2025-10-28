/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.management;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.qa.util.actuator.BanningActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
public class BanningEndpointIT {

  @TestZeebe(initMethod = "initTestCluster")
  private static TestCluster cluster;

  @SuppressWarnings("unused")
  static void initTestCluster() {
    cluster =
        TestCluster.builder()
            .useRecordingExporter(true)
            .withEmbeddedGateway(true)
            .withBrokersCount(2)
            .withPartitionsCount(1)
            .withReplicationFactor(2)
            .build();
  }

  @Test
  void shouldBanInstance() {
    // given - a process instance
    final var actuator = banningActuator();
    final long processInstanceKey;
    try (final var client = cluster.newClientBuilder().build()) {
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
    final var actuator = banningActuator();
    final long processInstanceKey;
    try (final var client = cluster.newClientBuilder().build()) {
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
                  RecordingExporter.records()
                      .filter(record -> record.getRecordType() == RecordType.EVENT)
                      .filter(record -> record.getValueType() == ValueType.ERROR)
                      .filter(record -> record.getKey() == processInstanceKey)
                      .limit(1)
                      .toList();
              Assertions.assertThat(errorEvents).isNotEmpty();
            });
  }

  private BanningActuator banningActuator() {
    final var broker = cluster.brokers().get(MemberId.from("0"));
    return BanningActuator.of(broker);
  }
}
