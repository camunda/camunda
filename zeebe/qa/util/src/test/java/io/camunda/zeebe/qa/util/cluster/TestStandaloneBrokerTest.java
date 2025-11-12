/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.qa.util.cluster;

import static io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker.RECORDING_EXPORTER_ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.Exporter;
import io.camunda.configuration.beans.BrokerBasedProperties;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

class TestStandaloneBrokerTest {

  private final TestStandaloneBroker broker = new TestStandaloneBroker();
  private BrokerBasedProperties brokerCfg;

  @BeforeEach
  void setUp() {
    broker.start();
    brokerCfg = broker.brokerConfig();
  }

  @Test
  void shouldInitTestStandaloneBrokerWithDefaultProperties() {
    assertThat(brokerCfg.getNetwork().getCommandApi().getPort())
        .isEqualTo(
            broker.property("camunda.cluster.network.command-api.port", Integer.class, null));
    assertThat(brokerCfg.getNetwork().getInternalApi().getPort())
        .isEqualTo(
            broker.property("camunda.cluster.network.internal-api.port", Integer.class, null));
    assertThat(brokerCfg.getGateway().getNetwork().getPort())
        .isEqualTo(broker.property("camunda.api.grpc.port", Integer.class, null));
    assertThat(brokerCfg.getData().getLogSegmentSize())
        .isEqualTo(
            broker.property(
                "camunda.data.primary-storage.log-stream.log-segment-size", DataSize.class, null));
    assertThat(brokerCfg.getData().getDisk().getFreeSpace().getProcessing())
        .isEqualTo(
            broker.property(
                "camunda.data.primary-storage.disk.free-space.processing", DataSize.class, null));
    assertThat(brokerCfg.getData().getDisk().getFreeSpace().getReplication())
        .isEqualTo(
            broker.property(
                "camunda.data.primary-storage.disk.free-space.replication", DataSize.class, null));
    assertThat(brokerCfg.getExperimental().getRaft().isPreallocateSegmentFiles())
        .isEqualTo(
            broker.property("camunda.cluster.raft.preallocate-segment-files", Boolean.class, null));
    assertThat(brokerCfg.getCluster().getRaft().getFlush().enabled())
        .isEqualTo(broker.property("camunda.cluster.raft.flush-enabled", Boolean.class, null));
    assertThat(brokerCfg.getCluster().getRaft().getFlush().delayTime())
        .isEqualTo(broker.property("camunda.cluster.raft.flush-delay", Duration.class, null));
    assertThat(brokerCfg.getCluster().getMembership().getFailureTimeout())
        .isEqualTo(
            broker.property("camunda.cluster.membership.failure-timeout", Duration.class, null));
    assertThat(brokerCfg.getCluster().getMembership().getProbeInterval())
        .isEqualTo(
            broker.property("camunda.cluster.membership.probe-interval", Duration.class, null));
    assertThat(brokerCfg.getCluster().getMembership().getSyncInterval())
        .isEqualTo(
            broker.property("camunda.cluster.membership.sync-interval", Duration.class, null));
    assertThat(brokerCfg.getExperimental().getConsistencyChecks().isEnableForeignKeyChecks())
        .isEqualTo(
            broker.property("camunda.processing.enable-foreign-key-checks", Boolean.class, null));
    assertThat(brokerCfg.getCluster().getNodeId())
        .isEqualTo(broker.property("camunda.cluster.node-id", Integer.class, null));
    assertThat(brokerCfg.getNetwork().getHost())
        .isEqualTo(broker.property("camunda.cluster.network.host", String.class, null));
    assertThat(brokerCfg.getGateway().isEnable())
        .isEqualTo(broker.property("zeebe.broker.gateway.enable", Boolean.class, null));
  }

  @Test
  void shouldGetMappedPort() {
    assertThat(broker.mappedPort(TestZeebePort.COMMAND))
        .isEqualTo(brokerCfg.getNetwork().getCommandApi().getPort());
    assertThat(broker.mappedPort(TestZeebePort.GATEWAY))
        .isEqualTo(brokerCfg.getGateway().getNetwork().getPort());
    assertThat(broker.mappedPort(TestZeebePort.CLUSTER))
        .isEqualTo(brokerCfg.getNetwork().getInternalApi().getPort());
  }

  @Test
  void shouldGetNodeId() {
    assertThat(broker.nodeId().id()).isEqualTo(String.valueOf(brokerCfg.getCluster().getNodeId()));
  }

  @Test
  void shouldGetHost() {
    assertThat(broker.host()).isEqualTo(brokerCfg.getNetwork().getInternalApi().getHost());
  }

  @Test
  void shouldGetGateway() {
    assertThat(broker.isGateway()).isEqualTo(brokerCfg.getGateway().isEnable());
  }

  @Test
  void shouldSetExporter() {
    // given
    final var exporter = new Exporter();
    exporter.setClassName("className");
    exporter.setJarPath("jarPath");
    exporter.setArgs(Map.of("arg1", "value1", "arg2", "value2"));

    // when
    final TestStandaloneBroker broker =
        new TestStandaloneBroker().withExporter("myExporter", exporter);

    // then
    assertThat(
        broker.property(
            "camunda.data.exporters.myExporter.className=className", String.class, null));
    assertThat(
        broker.property("camunda.data.exporters.myExporter.jarPath=jarPath", String.class, null));
    assertThat(
        broker.property("camunda.data.exporters.myExporter.args.arg1=value1", String.class, null));
    assertThat(
        broker.property("camunda.data.exporters.myExporter.args.arg2=value2", String.class, null));
  }

  @Test
  void shouldSetRecordingExporter() {
    // given
    final TestStandaloneBroker broker =
        new TestStandaloneBroker()
            .withRecordingExporter(true)
            .withProperty("camunda.data.secondary-storage.autoconfigure-camunda-exporter", false);

    // when
    broker.start();

    // then
    final BrokerBasedProperties brokerCfg = broker.brokerConfig();

    final var expectedExporter = new ExporterCfg();
    expectedExporter.setClassName(RecordingExporter.class.getName());

    assertThat(brokerCfg.getExporters())
        .containsExactlyInAnyOrderEntriesOf(Map.of(RECORDING_EXPORTER_ID, expectedExporter));
  }

  @Test
  void shouldRemoveRecordingExporter() {
    // given
    final TestStandaloneBroker broker =
        new TestStandaloneBroker()
            .withRecordingExporter(true)
            .withProperty("camunda.data.secondary-storage.autoconfigure-camunda-exporter", false);

    // when
    broker.withRecordingExporter(false);

    // then
    broker.start();
    assertThat(broker.brokerConfig().getExporters()).isEmpty();
  }
}
