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
import io.camunda.zeebe.protocol.impl.record.value.globallistener.GlobalListenerBatchRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ClockIntent;
import io.camunda.zeebe.protocol.record.intent.GlobalListenerBatchIntent;
import io.camunda.zeebe.protocol.record.intent.GlobalListenerIntent;
import io.camunda.zeebe.protocol.record.value.GlobalListenerRecordValue;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
final class GlobalListenersInitializerIT {

  private static final int PARTITIONS_COUNT = 2;
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

    // Then multiple listener mutation events and one configuration event are created for each
    // partition with the required changes

    // Collect listener records for each partition and group them by partition id
    final var listenerRecordsByPartition =
        RecordingExporter.records()
            // one CONFIGURED event for each partition mark the end of the initialization
            .limitByCount(
                r -> r.getIntent().equals(GlobalListenerBatchIntent.CONFIGURED), PARTITIONS_COUNT)
            .onlyEvents()
            .filter(
                record ->
                    record.getValueType() == ValueType.GLOBAL_LISTENER
                        || record.getValueType() == ValueType.GLOBAL_LISTENER_BATCH)
            .collect(Collectors.groupingBy(Record::getPartitionId));
    // Check that events are distributed to all partitions
    Assertions.assertThat(listenerRecordsByPartition.keySet())
        .containsExactlyInAnyOrderElementsOf(PARTITION_IDS);
    // Check that for each partition, we have the expected events with the expected types
    for (final var records : listenerRecordsByPartition.values()) {
      assertThat(records)
          .hasSize(3)
          .anyMatch(
              record ->
                  record.getIntent() == GlobalListenerIntent.UPDATED
                      && ((GlobalListenerRecordValue) record.getValue())
                          .getId()
                          .equals("GlobalListener_global1"),
              "Expected an UPDATE event for GlobalListener_global1")
          .anyMatch(
              record ->
                  record.getIntent() == GlobalListenerIntent.CREATED
                      && ((GlobalListenerRecordValue) record.getValue())
                          .getId()
                          .equals("GlobalListener_global2"),
              "Expected a CREATE event for GlobalListener_global2")
          .anyMatch(
              record -> record.getIntent() == GlobalListenerBatchIntent.CONFIGURED,
              "Expected a CONFIGURED event");
    }
    // Check that the same configuration key is used in all partitions
    final var configurationKeys =
        listenerRecordsByPartition.values().stream()
            .flatMap(Collection::stream)
            .filter(record -> record.getIntent() == GlobalListenerBatchIntent.CONFIGURED)
            .map(Record::getValue)
            .map(GlobalListenerBatchRecord.class::cast)
            .map(GlobalListenerBatchRecord::getGlobalListenerBatchKey)
            .collect(Collectors.toSet());
    Assertions.assertThat(configurationKeys).hasSize(1);
  }

  @Test
  void shouldInitializeGlobalListenersWhenConfigurationIsRemoved() {
    // Given a broker with a given global listeners configuration
    ZEEBE.unifiedConfig().getCluster().setGlobalListeners(createGlobalListenersCfg("global"));
    restartBroker();

    // When an empty configuration is set and the broker is restarted
    ZEEBE.unifiedConfig().getCluster().setGlobalListeners(createGlobalListenersCfg());
    restartBroker();

    // Then multiple listener mutation events and one configuration event are created for each
    // partition with the required changes

    // Collect listener records for each partition and group them by partition id
    final var listenerRecordsByPartition =
        RecordingExporter.records()
            // one CONFIGURED event for each partition mark the end of the initialization
            .limitByCount(
                r -> r.getIntent().equals(GlobalListenerBatchIntent.CONFIGURED), PARTITIONS_COUNT)
            .onlyEvents()
            .filter(
                record ->
                    record.getValueType() == ValueType.GLOBAL_LISTENER
                        || record.getValueType() == ValueType.GLOBAL_LISTENER_BATCH)
            .collect(Collectors.groupingBy(Record::getPartitionId));
    // Check that events are distributed to all partitions
    Assertions.assertThat(listenerRecordsByPartition.keySet())
        .containsExactlyInAnyOrderElementsOf(PARTITION_IDS);
    // Check that for each partition, we have the expected events with the expected types
    for (final var records : listenerRecordsByPartition.values()) {
      assertThat(records)
          .hasSize(2)
          .anyMatch(
              record ->
                  record.getIntent() == GlobalListenerIntent.DELETED
                      && ((GlobalListenerRecordValue) record.getValue())
                          .getId()
                          .equals("GlobalListener_global"),
              "Expected a DELETE event for GlobalListener_global")
          .anyMatch(
              record -> record.getIntent() == GlobalListenerBatchIntent.CONFIGURED,
              "Expected a CONFIGURED event");
    }
    // Check that the same configuration key is used in all partitions
    final var configurationKeys =
        listenerRecordsByPartition.values().stream()
            .flatMap(Collection::stream)
            .filter(record -> record.getIntent() == GlobalListenerBatchIntent.CONFIGURED)
            .map(Record::getValue)
            .map(GlobalListenerBatchRecord.class::cast)
            .map(GlobalListenerBatchRecord::getGlobalListenerBatchKey)
            .collect(Collectors.toSet());
    Assertions.assertThat(configurationKeys).hasSize(1);
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

    // Then no configuration event is created
    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getIntent().equals(ClockIntent.RESET))
                .globalListenerBatchRecords()
                .withIntent(GlobalListenerBatchIntent.CONFIGURED))
        .hasSize(0);
  }

  private GlobalListenersCfg createGlobalListenersCfg(final String... types) {
    final GlobalListenersCfg globalListenersCfg = new GlobalListenersCfg();
    globalListenersCfg.setUserTask(
        Arrays.stream(types)
            .map(
                type -> {
                  final GlobalListenerCfg listenerCfg = new GlobalListenerCfg();
                  listenerCfg.setId("GlobalListener_" + type);
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
}
