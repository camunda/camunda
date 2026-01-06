/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.engine.processing;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.zeebe.broker.system.configuration.engine.GlobalListenerCfg;
import io.camunda.zeebe.broker.system.configuration.engine.GlobalListenersCfg;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ClockIntent;
import io.camunda.zeebe.protocol.record.intent.GlobalListenerBatchIntent;
import io.camunda.zeebe.protocol.record.value.GlobalListenerBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.GlobalListenerRecordValue;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
final class GlobalListenersInitializerIT {

  private static final int PARTITIONS_COUNT = 3;
  private static final List<Integer> PARTITION_IDS =
      IntStream.range(1, PARTITIONS_COUNT + 1).boxed().toList();

  @TestZeebe(autoStart = false, awaitCompleteTopology = false)
  private static final TestStandaloneBroker ZEEBE =
      new TestStandaloneBroker()
          .withRecordingExporter(true)
          .withUnauthenticatedAccess()
          .withClusterConfig(cluster -> cluster.setPartitionCount(PARTITIONS_COUNT));

  @AutoClose private CamundaClient client;

  @BeforeEach
  void init() {
    client =
        ZEEBE
            .newClientBuilder()
            .useDefaultRetryPolicy(true) // needed to avoid errors after broker restarts
            .build();
  }

  @Test
  void shouldInitializeGlobalListenersWhenConfigurationChanges() {
    // Given a broker with a given global listeners configuration
    ZEEBE.unifiedConfig().getCluster().setGlobalListeners(createGlobalListenersCfg("global1"));
    restartBroker();

    // When the configuration is changed and the broker restarted
    ZEEBE
        .unifiedConfig()
        .getCluster()
        .setGlobalListeners(createGlobalListenersCfg("global1", "global2"));
    restartBroker();

    // Note: we send a clock reset command so we have a record we can limit our RecordingExporter on
    client.newResetClockCommand().send();

    // Then a configuration command is created for each partition with the new data
    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getIntent().equals(ClockIntent.RESET))
                .globalListenerBatchRecords()
                .withIntent(GlobalListenerBatchIntent.CONFIGURE))
        .hasSize(PARTITIONS_COUNT)
        .allMatch(record -> containsTypes(record, "global1", "global2"))
        .extracting(Record::getPartitionId)
        .containsAll(PARTITION_IDS);
  }

  @Test
  void shouldInitializeGlobalListenersWhenConfigurationIsRemoved() {
    // Given a broker with a given global listeners configuration
    ZEEBE.unifiedConfig().getCluster().setGlobalListeners(createGlobalListenersCfg("global"));
    restartBroker();

    // When an empty configuration is set and the broker is restarted
    ZEEBE.unifiedConfig().getCluster().setGlobalListeners(createGlobalListenersCfg());
    restartBroker();

    // Note: we send a clock reset command so we have a record we can limit our RecordingExporter on
    client.newResetClockCommand().send();

    // Then a configuration command is created for each partition with the new data
    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getIntent().equals(ClockIntent.RESET))
                .globalListenerBatchRecords()
                .withIntent(GlobalListenerBatchIntent.CONFIGURE))
        .hasSize(PARTITIONS_COUNT)
        .allMatch(record -> record.getValue().getTaskListeners().isEmpty())
        .extracting(Record::getPartitionId)
        .containsAll(PARTITION_IDS);
  }

  @Test
  void shouldNotInitializeGlobalListenersWhenConfigurationIsNotChanged() {
    // Given a broker with a given global listeners configuration
    ZEEBE.unifiedConfig().getCluster().setGlobalListeners(createGlobalListenersCfg("global1"));
    restartBroker();

    // When the configuration is not changed and the broker is restarted
    ZEEBE.unifiedConfig().getCluster().setGlobalListeners(createGlobalListenersCfg("global1"));
    restartBroker();

    // Note: we send a clock reset command so we have a record we can limit our RecordingExporter on
    client.newResetClockCommand().send();

    // Then no configuration command is created
    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getIntent().equals(ClockIntent.RESET))
                .globalListenerBatchRecords()
                .withIntent(GlobalListenerBatchIntent.CONFIGURE))
        .hasSize(0);
  }

  private GlobalListenersCfg createGlobalListenersCfg(final String... types) {
    final GlobalListenersCfg globalListenersCfg = new GlobalListenersCfg();
    globalListenersCfg.setUserTask(
        Arrays.stream(types)
            .map(
                type -> {
                  final GlobalListenerCfg listenerCfg = new GlobalListenerCfg();
                  listenerCfg.setType(type);
                  listenerCfg.setEventTypes(List.of("all"));
                  return listenerCfg;
                })
            .toList());
    return globalListenersCfg;
  }

  private void restartBroker() {
    if (ZEEBE.isStarted()) {
      ZEEBE.stop();
      RecordingExporter.reset();
    }
    ZEEBE.start();
    ZEEBE.awaitCompleteTopology(1, PARTITIONS_COUNT, 1, Duration.ofSeconds(30));
  }

  private boolean containsTypes(
      final Record<GlobalListenerBatchRecordValue> record, final String... types) {
    final var recordTypes =
        record.getValue().getTaskListeners().stream()
            .map(GlobalListenerRecordValue::getType)
            .toList();
    return List.of(types).equals(recordTypes);
  }
}
