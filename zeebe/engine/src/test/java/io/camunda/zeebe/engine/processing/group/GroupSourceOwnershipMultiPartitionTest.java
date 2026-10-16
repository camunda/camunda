/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.group;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord.GroupAttribute;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class GroupSourceOwnershipMultiPartitionTest {

  private static final int PARTITION_COUNT = 3;

  @Rule public final EngineRule engine = EngineRule.multiplePartition(PARTITION_COUNT);
  @Rule public final TestWatcher testWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldAdoptGroupOnAllPartitionsWhenAddingMember() {
    // given
    final var groupId = Strings.newRandomValidIdentityId();
    engine.group().newGroup(groupId).withName(groupId).create();
    final var username = Strings.newRandomValidIdentityId();
    engine.user().newUser(username).withName(username).withSource("entra").create();

    // when
    engine
        .group()
        .addEntity(groupId)
        .withSource("entra")
        .withChangedAttributes(GroupAttribute.SOURCE)
        .withEntityId(username)
        .withEntityType(EntityType.USER)
        .add();

    // then
    for (int partitionId = 1; partitionId <= PARTITION_COUNT; partitionId++) {
      assertThat(
              RecordingExporter.groupRecords(GroupIntent.UPDATED)
                  .withPartitionId(partitionId)
                  .withGroupId(groupId)
                  .getFirst()
                  .getValue()
                  .getSource())
          .describedAs("source of the group on partition %d", partitionId)
          .isEqualTo("entra");
    }
  }

  @Test
  public void shouldDistributeSourceOfCreatedGroup() {
    // given
    final var groupId = Strings.newRandomValidIdentityId();

    // when
    engine.group().newGroup(groupId).withName(groupId).withSource("entra").create();

    // then
    for (int partitionId = 2; partitionId <= PARTITION_COUNT; partitionId++) {
      assertThat(
              RecordingExporter.groupRecords(GroupIntent.CREATED)
                  .withPartitionId(partitionId)
                  .withGroupId(groupId)
                  .getFirst()
                  .getValue()
                  .getSource())
          .isEqualTo("entra");
    }
  }
}
