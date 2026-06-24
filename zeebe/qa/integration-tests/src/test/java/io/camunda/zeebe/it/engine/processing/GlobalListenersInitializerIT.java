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
import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;
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
import org.apache.commons.lang3.RandomStringUtils;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

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
  void shouldInitializeGlobalListenersWhenConfigurationChanges(final TestInfo testInfo) {
    // Given a broker with a given global listeners configuration
    final String initialListener = uniqueListenerName(testInfo);
    ZEEBE
        .unifiedConfig()
        .getCluster()
        .setGlobalListeners(createGlobalListenersCfg(initialListener));
    restartBroker();

    // Wait for configuration to be distributed to all partitions and keep track of the last
    // position for each partition, this way we can filter records for the assertions to only
    // include records actually generated after the restart.
    waitForConfigurationDistributionComplete(initialListener);
    final var lastPositionByPartition =
        RecordingExporter.globalListenerBatchRecords(GlobalListenerBatchIntent.CONFIGURED)
            .filter(
                r ->
                    r.getValue().getListeners().stream()
                        .map(GlobalListenerRecordValue::getId)
                        .anyMatch(initialListener::equals))
            .limit(PARTITIONS_COUNT)
            .collect(Collectors.toMap(Record::getPartitionId, Record::getPosition));

    // When the configuration is changed and the broker restarted
    ZEEBE
        .unifiedConfig()
        .getCluster()
        .setGlobalListeners(createGlobalListenersCfg(initialListener, "newListener"));
    restartBroker();

    // Then multiple listener mutation events and one configuration event are created for each
    // partition with the required changes

    // Collect listener records for each partition and group them by partition id
    final var listenerRecordsByPartition =
        RecordingExporter.records()
            // skip records re-exported after restart but generated before it
            .filter(r -> lastPositionByPartition.get(r.getPartitionId()) < r.getPosition())
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
        .as("Expected events to be generated in all partitions")
        .containsExactlyInAnyOrderElementsOf(PARTITION_IDS);
    // Check that for each partition, we have the expected events with the expected types
    for (final var records : listenerRecordsByPartition.values()) {
      assertThat(records)
          .as(
              "Expected 3 events for each partition: an UPDATE for the old listener, a CREATE for the new listener and a CONFIGURED")
          .hasSize(3)
          .anyMatch(
              record ->
                  record.getIntent() == GlobalListenerIntent.UPDATED
                      && ((GlobalListenerRecordValue) record.getValue())
                          .getId()
                          .equals(initialListener),
              "Expected an UPDATE event for the old listener")
          .anyMatch(
              record ->
                  record.getIntent() == GlobalListenerIntent.CREATED
                      && ((GlobalListenerRecordValue) record.getValue())
                          .getId()
                          .equals("newListener"),
              "Expected a CREATE event for the new listener")
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
    Assertions.assertThat(configurationKeys)
        .as("Expected the same configuration key to be used in all partitions")
        .hasSize(1);
  }

  @Test
  void shouldInitializeGlobalListenersWhenConfigurationIsRemoved(final TestInfo testInfo) {
    // Given a broker with a given global listeners configuration
    final String initialListener = uniqueListenerName(testInfo);
    ZEEBE
        .unifiedConfig()
        .getCluster()
        .setGlobalListeners(createGlobalListenersCfg(initialListener));
    restartBroker();

    // Wait for configuration to be distributed to all partitions and keep track of the last
    // position for each partition, this way we can filter records for the assertions to only
    // include records actually generated after the restart.
    waitForConfigurationDistributionComplete(initialListener);
    final var lastPositionByPartition =
        RecordingExporter.globalListenerBatchRecords(GlobalListenerBatchIntent.CONFIGURED)
            .filter(
                r ->
                    r.getValue().getListeners().stream()
                        .map(GlobalListenerRecordValue::getId)
                        .anyMatch(initialListener::equals))
            .limit(PARTITIONS_COUNT)
            .collect(Collectors.toMap(Record::getPartitionId, Record::getPosition));

    // When an empty configuration is set and the broker is restarted
    ZEEBE.unifiedConfig().getCluster().setGlobalListeners(createGlobalListenersCfg());
    restartBroker();

    // Then multiple listener mutation events and one configuration event are created for each
    // partition with the required changes

    // Collect listener records for each partition and group them by partition id
    final var listenerRecordsByPartition =
        RecordingExporter.records()
            // skip records re-exported after restart but generated before it
            .filter(r -> lastPositionByPartition.get(r.getPartitionId()) < r.getPosition())
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
        .as("Expected events to be generated in all partitions")
        .containsExactlyInAnyOrderElementsOf(PARTITION_IDS);
    // Check that for each partition, we have the expected events with the expected types
    for (final var records : listenerRecordsByPartition.values()) {
      assertThat(records)
          .as(
              "Expected 2 events for each partition: a DELETE for the old listener and a CONFIGURED")
          .hasSize(2)
          .anyMatch(
              record ->
                  record.getIntent() == GlobalListenerIntent.DELETED
                      && ((GlobalListenerRecordValue) record.getValue())
                          .getId()
                          .equals(initialListener),
              "Expected a DELETE event for the old listener")
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
    Assertions.assertThat(configurationKeys)
        .as("Expected the same configuration key to be used in all partitions")
        .hasSize(1);
  }

  @Test
  void shouldNotInitializeGlobalListenersWhenConfigurationIsNotChanged(final TestInfo testInfo) {
    // Given a broker with a given global listeners configuration
    final String initialListener = uniqueListenerName(testInfo);
    ZEEBE
        .unifiedConfig()
        .getCluster()
        .setGlobalListeners(createGlobalListenersCfg(initialListener));
    restartBroker();

    // Wait for configuration to be distributed to all partitions and keep track of the last
    // position for each partition, this way we can filter records for the assertions to only
    // include records actually generated after the restart.
    waitForConfigurationDistributionComplete(initialListener);
    final var lastPositionByPartition =
        RecordingExporter.globalListenerBatchRecords(GlobalListenerBatchIntent.CONFIGURED)
            .filter(
                r ->
                    r.getValue().getListeners().stream()
                        .map(GlobalListenerRecordValue::getId)
                        .anyMatch(initialListener::equals))
            .limit(PARTITIONS_COUNT)
            .collect(Collectors.toMap(Record::getPartitionId, Record::getPosition));

    // When the configuration is not changed and the broker is restarted
    ZEEBE
        .unifiedConfig()
        .getCluster()
        .setGlobalListeners(createGlobalListenersCfg(initialListener));
    restartBroker();

    // Note: we send a clock reset command so we have a record we can limit our RecordingExporter on
    client.newResetClockCommand().send();

    // Then no configuration event is created
    assertThat(
            RecordingExporter.records()
                // skip records re-exported after restart but generated before it
                .filter(r -> lastPositionByPartition.get(r.getPartitionId()) < r.getPosition())
                .limit(r -> r.getIntent().equals(ClockIntent.RESET))
                .globalListenerBatchRecords()
                .withIntent(GlobalListenerBatchIntent.CONFIGURED))
        .as("Expected no CONFIGURED event to be generated when the configuration is not changed")
        .hasSize(0);
  }

  private GlobalListenersCfg createGlobalListenersCfg(final String... listenerIds) {
    final GlobalListenersCfg globalListenersCfg = new GlobalListenersCfg();
    globalListenersCfg.setUserTask(
        Arrays.stream(listenerIds)
            .map(
                id -> {
                  final GlobalListenerCfg listenerCfg = new GlobalListenerCfg();
                  listenerCfg.setId(id);
                  listenerCfg.setType(id);
                  listenerCfg.setEventTypes(List.of("all"));
                  return listenerCfg;
                })
            .toList());
    return globalListenersCfg;
  }

  private void restartBroker() {
    if (ZEEBE.isStarted()) {
      ZEEBE.stop();
    }
    ZEEBE.start();
    ZEEBE.awaitCompleteTopology(1, PARTITIONS_COUNT, 1, Duration.ofSeconds(30));
  }

  private void waitForConfigurationDistributionComplete(final String expectedListenerId) {
    RecordingExporter.await(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              // Wait for configuration to be applied on all partitions
              final var configuredEvents =
                  RecordingExporter.globalListenerBatchRecords(GlobalListenerBatchIntent.CONFIGURED)
                      .filter(
                          r ->
                              r.getValue().getListeners().stream()
                                  .map(GlobalListenerRecordValue::getId)
                                  .anyMatch(expectedListenerId::equals))
                      .limit(PARTITIONS_COUNT)
                      .toList();
              assertThat(configuredEvents.stream().map(Record::getPartitionId).toList())
                  .containsExactlyInAnyOrderElementsOf(PARTITION_IDS);

              final var distributionKey = configuredEvents.getFirst().getKey();
              // Wait for main partition to complete the distribution on all partitions
              // Waiting only for the events to be exported in each partition is not enough because
              // the main partitions could not have received the acknowledgment of the command
              // distribution yet and in that case we risk having the commands distributed again
              // after the restart.
              assertThat(
                      RecordingExporter.commandDistributionRecords(
                              CommandDistributionIntent.FINISHED)
                          .withDistributionIntent(GlobalListenerBatchIntent.CONFIGURE)
                          .withRecordKey(distributionKey)
                          .limit(PARTITIONS_COUNT - 1))
                  .hasSize(PARTITIONS_COUNT - 1);
            });
  }

  // to produce something like: `shouldInitializeGlobalListenersWhenConfigurationChanges-aB3xZ`
  private static String uniqueListenerName(final TestInfo testInfo) {
    return testInfo.getTestMethod().orElseThrow().getName()
        + "-"
        + RandomStringUtils.insecure().nextAlphanumeric(5);
  }
}
