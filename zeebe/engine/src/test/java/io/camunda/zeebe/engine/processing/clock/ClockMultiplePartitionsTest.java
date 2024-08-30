/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.clock;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.client.ClockClient;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ClockIntent;
import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;
import io.camunda.zeebe.protocol.record.value.CommandDistributionRecordValue;
import io.camunda.zeebe.stream.api.StreamClock.ControllableStreamClock.Modification;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.awaitility.Awaitility;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class ClockMultiplePartitionsTest {
  private static final int PARTITION_COUNT = 3;
  @ClassRule public static final EngineRule ENGINE = EngineRule.multiplePartition(PARTITION_COUNT);

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private final ClockClient clockClient = ENGINE.clock();

  @Test
  public void shouldWriteDistributingRecordsForOtherPartitionsOnPin() {
    // given
    final var now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    // when
    final var record = clockClient.pinAt(now);

    // then
    final var key = record.getKey();
    final var commandDistributionRecords =
        RecordingExporter.commandDistributionRecords()
            .withIntent(CommandDistributionIntent.DISTRIBUTING)
            .valueFilter(v -> v.getValueType().equals(ValueType.CLOCK))
            .limit(2)
            .asList();

    assertThat(commandDistributionRecords).extracting(Record::getKey).containsOnly(key);
    assertThat(commandDistributionRecords)
        .extracting(Record::getValue)
        .extracting(CommandDistributionRecordValue::getPartitionId)
        .containsExactly(2, 3);
  }

  @Test
  public void shouldPinClockOnAllPartitions() {
    // given
    final var pinnedNow = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    // when
    final var record = clockClient.pinAt(pinnedNow);

    // then
    for (int i = 1; i <= PARTITION_COUNT; i++) {
      // required to ensure we apply the side effect of the clock
      final var partitionId = i;
      Awaitility.await("until side effect has been applied")
          .until(
              () ->
                  RecordingExporter.clockRecords(ClockIntent.PINNED)
                      .withPartitionId(partitionId)
                      .withRecordKey(record.getKey())
                      .exists());

      // then
      assertThat(ENGINE.getStreamClock(partitionId).instant()).isEqualTo(pinnedNow);
      assertThat(ENGINE.getProcessingState(partitionId).getClockState().getModification())
          .isEqualTo(Modification.pinAt(pinnedNow));
    }
  }

  @Test
  public void shouldWriteDistributingRecordsForOtherPartitionsOnReset() {
    // given
    // when
    final var record = clockClient.reset();

    // then
    final var key = record.getKey();
    final var commandDistributionRecords =
        RecordingExporter.commandDistributionRecords()
            .withIntent(CommandDistributionIntent.DISTRIBUTING)
            .valueFilter(v -> v.getValueType().equals(ValueType.CLOCK))
            .limit(2)
            .asList();

    assertThat(commandDistributionRecords).extracting(Record::getKey).containsOnly(key);
    assertThat(commandDistributionRecords)
        .extracting(Record::getValue)
        .extracting(CommandDistributionRecordValue::getPartitionId)
        .containsExactly(2, 3);
  }

  @Test
  public void shouldResetClockOnAllPartitions() {
    // given
    final var pinnedNow = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    clockClient.pinAt(pinnedNow);

    // when
    final var record = clockClient.reset();

    // then
    for (int i = 1; i <= PARTITION_COUNT; i++) {
      // required to ensure we apply the side effect of the clock
      final var partitionId = i;
      Awaitility.await("until side effect has been applied")
          .until(
              () ->
                  RecordingExporter.clockRecords(ClockIntent.RESETTED)
                      .withPartitionId(partitionId)
                      .withRecordKey(record.getKey())
                      .exists());

      // then
      assertThat(ENGINE.getStreamClock(partitionId).instant()).isAfter(pinnedNow);
      assertThat(ENGINE.getProcessingState(partitionId).getClockState().getModification())
          .isEqualTo(Modification.none());
    }
  }
}
