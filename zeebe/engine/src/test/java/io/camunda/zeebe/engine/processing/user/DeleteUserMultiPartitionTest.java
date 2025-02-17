/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;

import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.protocol.record.value.CommandDistributionRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.assertj.core.api.Assertions;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class DeleteUserMultiPartitionTest {
  private static final int PARTITION_COUNT = 3;

  @Rule public final EngineRule engine = EngineRule.multiplePartition(PARTITION_COUNT);

  @Rule public final TestWatcher testWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldDistributeUserDeleteCommand() {
    final var username = UUID.randomUUID().toString();

    final var userRecord =
        engine
            .user()
            .newUser(username)
            .withName("Foo Bar")
            .withEmail("foo@bar.com")
            .withPassword("password")
            .create();

    engine
        .user()
        .deleteUser(username)
        .withName("Bar Foo")
        .withEmail("bar@foo.com")
        .withPassword("password")
        .delete();

    RecordingExporter.commandDistributionRecords(CommandDistributionIntent.FINISHED)
        .withDistributionIntent(UserIntent.CREATE)
        .await();

    assertThat(
            RecordingExporter.records()
                .withPartitionId(1)
                .limitByCount(
                    record -> record.getIntent().equals(CommandDistributionIntent.FINISHED), 3)
                .filter(
                    record ->
                        record.getValueType() == ValueType.USER
                            || (record.getValueType() == ValueType.COMMAND_DISTRIBUTION
                                && ((CommandDistributionRecordValue) record.getValue()).getIntent()
                                    == UserIntent.DELETE)))
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
        .containsSubsequence(
            tuple(UserIntent.DELETE, RecordType.COMMAND, 1),
            tuple(UserIntent.DELETED, RecordType.EVENT, 1),
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
              RecordingExporter.records()
                  .withPartitionId(partitionId)
                  .limit(record -> record.getIntent().equals(UserIntent.DELETED))
                  .collect(Collectors.toList()))
          .extracting(Record::getIntent)
          .containsSubsequence(UserIntent.DELETE, UserIntent.DELETED);
    }
  }

  @Test
  public void shouldDistributeInIdentityQueue() {
    final var username = UUID.randomUUID().toString();
    // when
    final var userRecord =
        engine
            .user()
            .newUser(username)
            .withName("Foo Bar")
            .withEmail("foo@bar.com")
            .withPassword("password")
            .create();

    engine
        .user()
        .deleteUser(username)
        .withName("Bar Foo")
        .withEmail("bar@foo.com")
        .withPassword("password")
        .delete();

    // then
    assertThat(
            RecordingExporter.commandDistributionRecords()
                .limitByCount(r -> r.getIntent().equals(CommandDistributionIntent.FINISHED), 3)
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
    final var userRecord =
        engine
            .user()
            .newUser("foobar")
            .withName("Foo Bar")
            .withEmail("foo@bar.com")
            .withPassword("password")
            .create();

    // when
    engine
        .user()
        .deleteUser("foobar")
        .withName("Bar Foo")
        .withEmail("bar@foo.com")
        .withPassword("password")
        .delete();

    // Increase time to trigger a redistribution
    engine.increaseTime(Duration.ofMinutes(1));

    // then
    assertThat(
            RecordingExporter.commandDistributionRecords(CommandDistributionIntent.FINISHED)
                .limit(3))
        .extracting(r -> r.getValue().getValueType(), r -> r.getValue().getIntent())
        .containsExactly(
            Assertions.tuple(ValueType.USER, UserIntent.CREATE),
            tuple(ValueType.AUTHORIZATION, AuthorizationIntent.CREATE),
            Assertions.tuple(ValueType.USER, UserIntent.DELETE));
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
