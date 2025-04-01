/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.group;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class GroupTest {
  @Rule public final EngineRule engine = EngineRule.singlePartition();
  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldCreateGroup() {
    final var name = UUID.randomUUID().toString();
    final var groupId = Strings.newRandomValidIdentityId();
    final var groupRecord = engine.group().newGroup(name).withGroupId(groupId).create();

    final var createdGroup = groupRecord.getValue();
    assertThat(createdGroup).hasName(name);
    assertThat(createdGroup).hasGroupId(groupId);
  }

  @Test
  public void shouldNotDuplicate() {
    // given
    final var name = UUID.randomUUID().toString();
    final var groupId = Strings.newRandomValidIdentityId();
    final var groupRecord = engine.group().newGroup(name).withGroupId(groupId).create();

    // when
    final var duplicatedGroupRecord =
        engine.group().newGroup(name).withGroupId(groupId).expectRejection().create();

    final var createdGroup = groupRecord.getValue();
    Assertions.assertThat(createdGroup).isNotNull().hasFieldOrPropertyWithValue("name", name);
    Assertions.assertThat(createdGroup).isNotNull().hasFieldOrPropertyWithValue("groupId", groupId);

    assertThat(duplicatedGroupRecord)
        .hasRejectionType(RejectionType.ALREADY_EXISTS)
        .hasRejectionReason(
            "Expected to create group with ID '%s', but a group with this ID already exists."
                .formatted(groupId));
  }

  @Test
  public void shouldUpdateGroup() {
    // TODO: refactor the test with https://github.com/camunda/camunda/issues/30024
    // given
    final var name = UUID.randomUUID().toString();
    final var groupId = "123";
    final var groupKey = Long.parseLong(groupId);
    engine.group().newGroup(name).withGroupId(groupId).create();

    // when
    final var updatedName = name + "-updated";
    final var updatedGroupRecord =
        engine.group().updateGroup(groupKey).withName(updatedName).update();

    final var updatedGroup = updatedGroupRecord.getValue();
    assertThat(updatedGroup).hasName(updatedName);
  }

  @Test
  public void shouldRejectUpdatedIfNoGroupExists() {
    // TODO: refactor the test with https://github.com/camunda/camunda/issues/30024
    // when
    final var groupKey = 1L;
    final var updatedName = "yolo";
    final var updatedGroupRecord =
        engine.group().updateGroup(groupKey).withName(updatedName).expectRejection().update();

    // then
    assertThat(updatedGroupRecord)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to update group with key '%d', but a group with this key does not exist."
                .formatted(groupKey));
  }

  @Test
  public void shouldAddEntityToGroup() {
    // TODO: refactor the test with https://github.com/camunda/camunda/issues/30028
    // given
    final var groupId = "123";
    final var groupKey = Long.parseLong(groupId);
    final var userKey =
        engine
            .user()
            .newUser("foo")
            .withEmail("foo@bar")
            .withName("Foo Bar")
            .withPassword("zabraboof")
            .create()
            .getKey();
    final var name = UUID.randomUUID().toString();
    engine.group().newGroup(name).withGroupId(groupId).create().getValue().getGroupKey();

    // when
    final var updatedGroup =
        engine
            .group()
            .addEntity(groupKey)
            .withEntityKey(userKey)
            .withEntityType(EntityType.USER)
            .add()
            .getValue();

    // then
    assertThat(updatedGroup).hasEntityKey(userKey).hasEntityType(EntityType.USER);
  }

  @Test
  public void shouldRejectIfGroupIsNotPresentWhileAddingEntity() {
    // TODO: refactor the test with https://github.com/camunda/camunda/issues/30028
    // when
    final var notPresentGroupKey = 1L;
    final var notPresentUpdateRecord =
        engine.group().addEntity(notPresentGroupKey).expectRejection().add();

    // then
    assertThat(notPresentUpdateRecord)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to update group with key '%d', but a group with this key does not exist."
                .formatted(notPresentGroupKey));
  }

  @Test
  public void shouldRejectIfEntityIsNotPresent() {
    // TODO: refactor the test with https://github.com/camunda/camunda/issues/30028
    // given
    final var groupId = "123";
    final var groupKey = Long.parseLong(groupId);
    final var name = UUID.randomUUID().toString();
    final var groupRecord = engine.group().newGroup(name).withGroupId(groupId).create();

    // when
    final var createdGroup = groupRecord.getValue();
    final var notPresentUpdateRecord =
        engine
            .group()
            .addEntity(groupKey)
            .withEntityKey(1L)
            .withEntityType(EntityType.USER)
            .expectRejection()
            .add();

    // then
    assertThat(createdGroup).hasName(name);
    assertThat(notPresentUpdateRecord)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to add an entity with key '%s' and type '%s' to group with key '%s', but the entity does not exist."
                .formatted(1L, EntityType.USER, groupKey));
  }

  @Test
  public void shouldRejectIfEntityIsAlreadyAssigned() {
    // TODO: refactor the test with https://github.com/camunda/camunda/issues/30028
    // given
    final var groupId = "123";
    final var groupKey = Long.parseLong(groupId);
    final var name = UUID.randomUUID().toString();
    engine.group().newGroup(name).withGroupId(groupId).create();
    final var userKey =
        engine
            .user()
            .newUser("foo")
            .withEmail("foo@bar")
            .withName("Foo Bar")
            .withPassword("zabraboof")
            .create()
            .getKey();
    engine.group().addEntity(groupKey).withEntityKey(userKey).withEntityType(EntityType.USER).add();

    // when
    final var notPresentUpdateRecord =
        engine
            .group()
            .addEntity(groupKey)
            .withEntityKey(userKey)
            .withEntityType(EntityType.USER)
            .expectRejection()
            .add();

    // then
    assertThat(notPresentUpdateRecord)
        .hasRejectionType(RejectionType.ALREADY_EXISTS)
        .hasRejectionReason(
            "Expected to add entity with key '%d' to group with key '%d', but the entity is already assigned to this group."
                .formatted(userKey, groupKey));
  }

  @Test
  public void shouldRemoveEntityToGroup() {
    // TODO: refactor the test with https://github.com/camunda/camunda/issues/30029
    // given
    final var groupId = "123";
    final var groupKey = Long.parseLong(groupId);
    final var userKey =
        engine
            .user()
            .newUser("foo")
            .withEmail("foo@bar")
            .withName("Foo Bar")
            .withPassword("zabraboof")
            .create()
            .getKey();
    final var name = UUID.randomUUID().toString();
    engine.group().newGroup(name).withGroupId(groupId).create();
    engine.group().addEntity(groupKey).withEntityKey(userKey).withEntityType(EntityType.USER).add();

    // when
    final var groupWithRemovedEntity =
        engine
            .group()
            .removeEntity(groupKey)
            .withEntityKey(userKey)
            .withEntityType(EntityType.USER)
            .remove()
            .getValue();

    // then
    assertThat(groupWithRemovedEntity)
        .hasGroupKey(groupKey)
        .hasEntityKey(userKey)
        .hasEntityType(EntityType.USER);
  }

  @Test
  public void shouldRejectIfGroupIsNotPresentEntityRemoval() {
    // TODO: refactor the test with https://github.com/camunda/camunda/issues/30029
    // when
    final var notPresentGroupKey = 1L;
    final var notPresentUpdateRecord =
        engine.group().addEntity(notPresentGroupKey).expectRejection().add();

    // then
    assertThat(notPresentUpdateRecord)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to update group with key '%d', but a group with this key does not exist."
                .formatted(notPresentGroupKey));
  }

  @Test
  public void shouldRejectIfEntityIsNotPresentEntityRemoval() {
    // TODO: refactor the test with https://github.com/camunda/camunda/issues/30029
    // given
    final var groupId = "123";
    final var groupKey = Long.parseLong(groupId);
    final var name = UUID.randomUUID().toString();
    final var groupRecord = engine.group().newGroup(name).withGroupId(groupId).create();

    // when
    final var createdGroup = groupRecord.getValue();
    final var notPresentUpdateRecord =
        engine
            .group()
            .removeEntity(groupKey)
            .withEntityKey(1L)
            .withEntityType(EntityType.USER)
            .expectRejection()
            .remove();

    // then
    assertThat(createdGroup).hasName(name);
    assertThat(notPresentUpdateRecord)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to remove an entity with key '%s' and type '%s' from group with key '%s', but the entity does not exist."
                .formatted(1L, EntityType.USER, groupKey));
  }

  @Test
  public void shouldDeleteGroup() {
    // TODO: refactor the test with https://github.com/camunda/camunda/issues/30025
    // given
    final var groupId = "123";
    final var groupKey = Long.parseLong(groupId);
    final var name = UUID.randomUUID().toString();
    engine.group().newGroup(name).withGroupId(groupId).create();

    // when
    final var deletedGroup = engine.group().deleteGroup(groupKey).delete().getValue();

    // then
    assertThat(deletedGroup).hasGroupKey(groupKey);
  }

  @Test
  public void shouldDeleteGroupWithAssignedEntities() {
    // TODO: refactor the test with https://github.com/camunda/camunda/issues/30025
    // given
    final var groupId = "123";
    final var groupKey = Long.parseLong(groupId);
    final var userKey =
        engine
            .user()
            .newUser("foo")
            .withEmail("foo@bar")
            .withName("Foo Bar")
            .withPassword("zabraboof")
            .create()
            .getKey();
    final var name = UUID.randomUUID().toString();
    engine.group().newGroup(name).withGroupId(groupId).create().getValue().getGroupKey();
    engine.group().addEntity(groupKey).withEntityKey(userKey).withEntityType(EntityType.USER).add();

    // when
    final var deletedGroup = engine.group().deleteGroup(groupKey).delete().getValue();

    // then
    final var groupRecords =
        RecordingExporter.groupRecords()
            .withIntents(GroupIntent.ENTITY_REMOVED, GroupIntent.DELETED)
            .withGroupKey(groupKey)
            .asList();
    assertThat(deletedGroup).hasGroupKey(groupKey);
    assertThat(groupRecords).hasSize(2);
    assertThat(groupRecords)
        .extracting(Record::getIntent)
        .containsExactly(GroupIntent.ENTITY_REMOVED, GroupIntent.DELETED);
  }

  @Test
  public void shouldRejectIfGroupIsNotPresentOnDeletion() {
    // TODO: refactor the test with https://github.com/camunda/camunda/issues/30025
    // when
    final var notPresentGroupKey = 1L;
    final var notPresentUpdateRecord =
        engine.group().deleteGroup(notPresentGroupKey).expectRejection().delete();

    // then
    assertThat(notPresentUpdateRecord)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to delete group with key '%s', but a group with this key does not exist."
                .formatted(notPresentGroupKey));
  }
}
