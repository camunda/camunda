/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;

import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.MappingIntent;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
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

public class CreateMappingMultiPartitionTest {

  private static final int PARTITION_COUNT = 3;

  @Rule public final EngineRule engine = EngineRule.multiplePartition(PARTITION_COUNT);
  @Rule public final TestWatcher testWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldDistributeMappingCreateCommand() {
    // when
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    engine.mapping().newMapping(claimName).withClaimValue(claimValue).create();

    assertThat(
            RecordingExporter.records()
                .withPartitionId(1)
                .limit(record -> record.getIntent().equals(CommandDistributionIntent.FINISHED)))
        .extracting(
            Record::getIntent,
            Record::getRecordType,
            r ->
                // We want to verify the partition id where the creation was distributing to and
                // where it was completed. Since only the CommandDistribution records have a
                // value that contains the partition id, we use the partition id the record was
                // written on for the other records.
                r.getValue() instanceof CommandDistributionRecordValue
                    ? ((CommandDistributionRecordValue) r.getValue()).getPartitionId()
                    : r.getPartitionId())
        .startsWith(
            tuple(MappingIntent.CREATE, RecordType.COMMAND, 1),
            tuple(MappingIntent.CREATED, RecordType.EVENT, 1),
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
              RecordingExporter.mappingRecords()
                  .withPartitionId(partitionId)
                  .limit(record -> record.getIntent().equals(MappingIntent.CREATED))
                  .collect(Collectors.toList()))
          .extracting(Record::getIntent)
          .containsExactly(MappingIntent.CREATE, MappingIntent.CREATED);
    }
  }

  @Test
  public void shouldDistributeInIdentityQueue() {
    // when
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    engine.mapping().newMapping(claimName).withClaimValue(claimValue).create();

    // then
    assertThat(
            RecordingExporter.commandDistributionRecords()
                .limitByCount(r -> r.getIntent().equals(CommandDistributionIntent.FINISHED), 1)
                .withIntent(CommandDistributionIntent.ENQUEUED))
        .extracting(r -> r.getValue().getQueueId())
        .containsOnly(DistributionQueue.IDENTITY.getQueueId());
  }

  @Test
  public void distributionShouldNotOvertakeOtherCommandsInSameQueue() {
    // given the role creation distribution is intercepted
    for (int partitionId = 2; partitionId <= PARTITION_COUNT; partitionId++) {
      interceptRoleCreateForPartition(partitionId);
    }
    final var roleName = UUID.randomUUID().toString();
    engine.role().newRole(roleName).create();
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    engine.mapping().newMapping(claimName).withClaimValue(claimValue).create();

    // Increase time to trigger a redistribution
    engine.increaseTime(Duration.ofMinutes(1));

    // then
    assertThat(
            RecordingExporter.commandDistributionRecords(CommandDistributionIntent.FINISHED)
                .limit(2))
        .extracting(r -> r.getValue().getValueType(), r -> r.getValue().getIntent())
        .containsExactly(
            tuple(ValueType.ROLE, RoleIntent.CREATE),
            tuple(ValueType.MAPPING, MappingIntent.CREATE));
  }

  private void interceptRoleCreateForPartition(final int partitionId) {
    final var hasInterceptedPartition = new AtomicBoolean(false);
    engine.interceptInterPartitionCommands(
        (receiverPartitionId, valueType, intent, recordKey, command) -> {
          if (hasInterceptedPartition.get()) {
            return true;
          }
          hasInterceptedPartition.set(true);
          return !(receiverPartitionId == partitionId && intent == RoleIntent.CREATE);
        });
  }
}