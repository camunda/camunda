/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.group;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.GroupRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class SetEntitiesGroupMultiPartitionTest {
  private static final int PARTITION_COUNT = 3;

  @Rule public final EngineRule engine = EngineRule.multiplePartition(PARTITION_COUNT);
  @Rule public final TestWatcher testWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldConvergeOnAllPartitions() {
    // given
    final var groupId = Strings.newRandomValidIdentityId();
    final var kept = createUser();
    final var removed = createUser();
    final var added = createUser();
    engine.group().newGroup(groupId).withName(groupId).create();
    engine.group().addEntity(groupId).withEntityId(kept).withEntityType(EntityType.USER).add();
    engine.group().addEntity(groupId).withEntityId(removed).withEntityType(EntityType.USER).add();
    awaitDistribution(GroupIntent.ADD_ENTITY, 2);

    // when
    engine
        .group()
        .setEntities(groupId)
        .withEntityType(EntityType.USER)
        .withEntityIds(List.of(kept, added))
        .set();

    // then
    awaitDistribution(GroupIntent.SET_ENTITIES, 1);
    for (int partitionId = 1; partitionId <= PARTITION_COUNT; partitionId++) {
      final var events =
          RecordingExporter.groupRecords()
              .withPartitionId(partitionId)
              .onlyEvents()
              .skipUntil(r -> r.getIntent() == GroupIntent.ENTITY_REMOVED)
              .limit(r -> r.getIntent() == GroupIntent.ENTITIES_SET)
              .toList();
      assertThat(events)
          .describedAs("partition %d", partitionId)
          .extracting(Record::getIntent)
          .containsExactly(
              GroupIntent.ENTITY_REMOVED, GroupIntent.ENTITY_ADDED, GroupIntent.ENTITIES_SET);
      assertThat(events.stream().map(Record::getValue).map(GroupRecordValue::getEntityId))
          .containsExactly(removed, added, "");
    }
  }

  @Test
  public void shouldDistributeInIdentityQueue() {
    // given
    final var groupId = Strings.newRandomValidIdentityId();
    engine.group().newGroup(groupId).withName(groupId).create();

    // when
    engine
        .group()
        .setEntities(groupId)
        .withEntityType(EntityType.USER)
        .withEntityIds(List.of())
        .set();

    // then
    awaitDistribution(GroupIntent.SET_ENTITIES, 1);
    assertThat(
            RecordingExporter.commandDistributionRecords()
                .limitByCount(r -> r.getIntent() == CommandDistributionIntent.FINISHED, 2)
                .withIntent(CommandDistributionIntent.ENQUEUED))
        .extracting(r -> r.getValue().getQueueId())
        .containsOnly(DistributionQueue.IDENTITY.getQueueId());
  }

  private String createUser() {
    final var username = Strings.newRandomValidIdentityId();
    return engine
        .user()
        .newUser(username)
        .withName(username)
        .withEmail(username + "@example.com")
        .withPassword("password")
        .create()
        .getValue()
        .getUsername();
  }

  private void awaitDistribution(final GroupIntent intent, final int occurrence) {
    assertThat(
            RecordingExporter.commandDistributionRecords(CommandDistributionIntent.FINISHED)
                .filter(r -> r.getValue().getIntent() == intent)
                .limit(occurrence)
                .count())
        .isEqualTo(occurrence);
  }
}
