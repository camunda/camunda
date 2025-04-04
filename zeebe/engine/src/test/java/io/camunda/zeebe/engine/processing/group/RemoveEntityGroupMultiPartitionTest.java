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
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.protocol.record.value.CommandDistributionRecordValue;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class RemoveEntityGroupMultiPartitionTest {
  private static final int PARTITION_COUNT = 3;
  @Rule public final EngineRule engine = EngineRule.multiplePartition(PARTITION_COUNT);
  @Rule public final TestWatcher testWatcher = new RecordingExporterTestWatcher();

  @Ignore("https://github.com/camunda/camunda/issues/30029")
  @Test
  public void shouldDistributeGroupRemoveEntityCommand() {
    // when
    final var user =
        engine
            .user()
            .newUser("foo")
            .withEmail("foo@bar")
            .withName("Foo Bar")
            .withPassword("zabraboof")
            .create();
    final var name = UUID.randomUUID().toString();
    final var groupId = "123";
    final var groupKey = Long.parseLong(groupId);
    engine.group().newGroup(name).withGroupId(groupId).create();
    engine
        .group()
        .addEntity(groupId)
        .withEntityId(user.getValue().getUsername())
        .withEntityType(EntityType.USER)
        .add();
    engine
        .group()
        .removeEntity(groupKey)
        .withEntityId(user.getValue().getUsername())
        .withEntityType(EntityType.USER)
        .remove();

    assertThat(
            RecordingExporter.records()
                .withPartitionId(1)
                .limitByCount(
                    record -> record.getIntent().equals(CommandDistributionIntent.FINISHED), 5)
                .filter(
                    record ->
                        record.getValueType() == ValueType.GROUP
                            || (record.getValueType() == ValueType.COMMAND_DISTRIBUTION
                                && ((CommandDistributionRecordValue) record.getValue()).getIntent()
                                    == GroupIntent.REMOVE_ENTITY)))
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
            tuple(GroupIntent.REMOVE_ENTITY, RecordType.COMMAND, 1),
            tuple(GroupIntent.ENTITY_REMOVED, RecordType.EVENT, 1),
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
                  .limit(record -> record.getIntent().equals(GroupIntent.ENTITY_REMOVED))
                  .collect(Collectors.toList()))
          .extracting(Record::getIntent)
          .containsSubsequence(GroupIntent.REMOVE_ENTITY, GroupIntent.ENTITY_REMOVED);
    }
  }

  @Test
  public void shouldDistributeInIdentityQueue() {
    // when
    final var user =
        engine
            .user()
            .newUser("foo")
            .withEmail("foo@bar")
            .withName("Foo Bar")
            .withPassword("zabraboof")
            .create();
    final var name = UUID.randomUUID().toString();
    final var groupId = "123";
    final var groupKey = Long.parseLong(groupId);
    engine.group().newGroup(name).withGroupId(groupId).create();
    engine
        .group()
        .addEntity(groupId)
        .withEntityId(user.getValue().getUsername())
        .withEntityType(EntityType.USER)
        .add();
    engine
        .group()
        .removeEntity(groupKey)
        .withEntityId(user.getValue().getUsername())
        .withEntityType(EntityType.USER)
        .remove();

    // then
    assertThat(
            RecordingExporter.commandDistributionRecords()
                .limitByCount(r -> r.getIntent().equals(CommandDistributionIntent.FINISHED), 5)
                .withIntent(CommandDistributionIntent.ENQUEUED))
        .extracting(r -> r.getValue().getQueueId())
        .containsOnly(DistributionQueue.IDENTITY.getQueueId());
  }

  @Test
  public void distributionShouldNotOvertakeOtherCommandsInSameQueue() {
    // given the user creation distribution is intercepted
    for (int partitionId = 2; partitionId <= PARTITION_COUNT; partitionId++) {
      interceptUserCreateForPartition(partitionId);
    }
    final var user =
        engine
            .user()
            .newUser("foo")
            .withEmail("foo@bar")
            .withName("Foo Bar")
            .withPassword("zabraboof")
            .create();

    // when
    final var name = UUID.randomUUID().toString();
    final var groupId = "123";
    final var groupKey = Long.parseLong(groupId);
    engine.group().newGroup(name).withGroupId(groupId).create();
    engine
        .group()
        .addEntity(groupId)
        .withEntityId(user.getValue().getUsername())
        .withEntityType(EntityType.USER)
        .add();
    engine
        .group()
        .removeEntity(groupKey)
        .withEntityId(user.getValue().getUsername())
        .withEntityType(EntityType.USER)
        .remove();

    // Increase time to trigger a redistribution
    engine.increaseTime(Duration.ofMinutes(1));

    // then
    assertThat(
            RecordingExporter.commandDistributionRecords(CommandDistributionIntent.FINISHED)
                .limit(5))
        .extracting(r -> r.getValue().getValueType(), r -> r.getValue().getIntent())
        .containsExactly(
            tuple(ValueType.USER, UserIntent.CREATE),
            tuple(ValueType.AUTHORIZATION, AuthorizationIntent.CREATE),
            tuple(ValueType.GROUP, GroupIntent.CREATE),
            tuple(ValueType.GROUP, GroupIntent.ADD_ENTITY),
            tuple(ValueType.GROUP, GroupIntent.REMOVE_ENTITY));
  }

  private void interceptUserCreateForPartition(final int partitionId) {
    final var hasInterceptedPartition = new AtomicBoolean(false);
    engine.interceptInterPartitionCommands(
        (receiverPartitionId, valueType, intent, recordKey, command) -> {
          if (hasInterceptedPartition.get()) {
            return true;
          }
          hasInterceptedPartition.set(true);
          return !(receiverPartitionId == partitionId && intent == UserIntent.CREATE);
        });
  }
}
