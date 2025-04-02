/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;

import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.value.CommandDistributionRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class DeleteGroupMultiPartitionTest {
  private static final int PARTITION_COUNT = 3;

  @Rule public final EngineRule engine = EngineRule.multiplePartition(PARTITION_COUNT);

  @Rule public final TestWatcher testWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldDistributeGroupDeleteCommand() {
    // when
    final var name = UUID.randomUUID().toString();
    final var groupId = "123";
    final var groupKey = Long.parseLong(groupId);
    engine.group().newGroup(name).withGroupId(groupId).create();
    engine.group().deleteGroup(groupKey).delete();

    assertThat(
            RecordingExporter.records()
                .withPartitionId(1)
                .limitByCount(
                    record -> record.getIntent().equals(CommandDistributionIntent.FINISHED), 2)
                .filter(
                    record ->
                        record.getValueType() == ValueType.GROUP
                            || (record.getValueType() == ValueType.COMMAND_DISTRIBUTION
                                && ((CommandDistributionRecordValue) record.getValue()).getIntent()
                                    == GroupIntent.DELETE)))
        .extracting(
            io.camunda.zeebe.protocol.record.Record::getIntent,
            io.camunda.zeebe.protocol.record.Record::getRecordType,
            r ->
                // We want to verify the partition id where the creation was distributing to and
                // where it was completed. Since only the CommandDistribution records have a
                // value that contains the partition id, we use the partition id the record was
                // written on for the other records.
                r.getValue() instanceof CommandDistributionRecordValue
                    ? ((CommandDistributionRecordValue) r.getValue()).getPartitionId()
                    : r.getPartitionId())
        .containsSubsequence(
            tuple(GroupIntent.DELETE, RecordType.COMMAND, 1),
            tuple(GroupIntent.DELETED, RecordType.EVENT, 1),
            tuple(CommandDistributionIntent.STARTED, RecordType.EVENT, 1))
        .containsSubsequence(
            tuple(CommandDistributionIntent.DISTRIBUTING, RecordType.EVENT, 2),
            tuple(CommandDistributionIntent.ACKNOWLEDGE, RecordType.COMMAND, 2),
            tuple(CommandDistributionIntent.ACKNOWLEDGED, RecordType.EVENT, 2))
        .containsSubsequence(
            tuple(CommandDistributionIntent.DISTRIBUTING, RecordType.EVENT, 3),
            tuple(CommandDistributionIntent.ACKNOWLEDGE, RecordType.COMMAND, 3),
            tuple(CommandDistributionIntent.ACKNOWLEDGED, RecordType.EVENT, 3))
        .endsWith(tuple(CommandDistributionIntent.FINISHED, RecordType.EVENT, 1));
    for (int partitionId = 2; partitionId < PARTITION_COUNT; partitionId++) {
      assertThat(
              RecordingExporter.groupRecords()
                  .withPartitionId(partitionId)
                  .limit(record -> record.getIntent().equals(GroupIntent.DELETED))
                  .collect(Collectors.toList()))
          .extracting(Record::getIntent)
          .containsSubsequence(GroupIntent.DELETE, GroupIntent.DELETED);
    }
  }

  @Test
  public void shouldDistributeInIdentityQueue() {
    // when
    final var name = UUID.randomUUID().toString();
    final var groupId = "123";
    final var groupKey = Long.parseLong(groupId);
    engine.group().newGroup(name).withGroupId(groupId).create();
    engine.group().deleteGroup(groupKey).delete();

    // then
    assertThat(
            RecordingExporter.commandDistributionRecords()
                .limitByCount(r -> r.getIntent().equals(CommandDistributionIntent.FINISHED), 2)
                .withIntent(CommandDistributionIntent.ENQUEUED))
        .extracting(r -> r.getValue().getQueueId())
        .containsOnly(DistributionQueue.IDENTITY.getQueueId());
  }

  @Test
  public void distributionShouldNotOvertakeOtherCommandsInSameQueue() {
    // when
    final var name = UUID.randomUUID().toString();
    for (int partitionId = 2; partitionId <= PARTITION_COUNT; partitionId++) {
      interceptGroupCreateForPartition(partitionId);
    }
    final var groupId = "123";
    final var groupKey = Long.parseLong(groupId);
    engine.group().newGroup(name).withGroupId(groupId).create();
    engine.group().deleteGroup(groupKey).delete();

    // Increase time to trigger a redistribution
    engine.increaseTime(Duration.ofMinutes(1));

    // then
    assertThat(
            RecordingExporter.commandDistributionRecords(CommandDistributionIntent.FINISHED)
                .limit(2))
        .extracting(r -> r.getValue().getValueType(), r -> r.getValue().getIntent())
        .containsExactly(
            tuple(ValueType.GROUP, GroupIntent.CREATE), tuple(ValueType.GROUP, GroupIntent.DELETE));
  }

  private void interceptGroupCreateForPartition(final int partitionId) {
    final var hasInterceptedPartition = new AtomicBoolean(false);
    engine.interceptInterPartitionCommands(
        (receiverPartitionId, valueType, intent, recordKey, command) -> {
          if (hasInterceptedPartition.get()) {
            return true;
          }
          hasInterceptedPartition.set(true);
          return !(receiverPartitionId == partitionId && intent == GroupIntent.CREATE);
        });
  }
}
