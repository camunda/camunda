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
    // given
    final var name = UUID.randomUUID().toString();
    final var groupId = UUID.randomUUID().toString();
    engine.group().newGroup(name).withGroupId(groupId).create();

    // when
    final var updatedName = name + "-updated";
    final var updatedGroupRecord =
        engine.group().updateGroup(groupId).withName(updatedName).update();

    final var updatedGroup = updatedGroupRecord.getValue();
    assertThat(updatedGroup).hasName(updatedName);
  }

  @Test
  public void shouldRejectUpdatedIfNoGroupExists() {
    // when
    final var groupId = UUID.randomUUID().toString();
    final var updatedName = "yolo";
    final var updatedGroupRecord =
        engine.group().updateGroup(groupId).withName(updatedName).expectRejection().update();

    // then
    assertThat(updatedGroupRecord)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to update group with ID '%s', but a group with this ID does not exist."
                .formatted(groupId));
  }

  @Test
  public void shouldAddEntityToGroup() {
    // given
    // TODO: refactor this with https://github.com/camunda/camunda/issues/30476
    final var groupId = "123";
    final var createdUser =
        engine
            .user()
            .newUser("foo")
            .withEmail("foo@bar")
            .withName("Foo Bar")
            .withPassword("zabraboof")
            .create();
    final var name = UUID.randomUUID().toString();
    engine.group().newGroup(name).withGroupId(groupId).create().getValue().getGroupKey();

    // when
    final var updatedGroup =
        engine
            .group()
            .addEntity(groupId)
            .withEntityId(createdUser.getValue().getUsername())
            .withEntityType(EntityType.USER)
            .add()
            .getValue();

    // then
    assertThat(updatedGroup)
        .hasEntityId(createdUser.getValue().getUsername())
        .hasEntityType(EntityType.USER);
  }

  @Test
  public void shouldRejectIfGroupIsNotPresentWhileAddingEntity() {
    // when
    final var notPresentGroupId = Strings.newRandomValidIdentityId();
    final var notPresentUpdateRecord =
        engine.group().addEntity(notPresentGroupId).expectRejection().add();

    // then
    assertThat(notPresentUpdateRecord)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to update group with ID '%s', but a group with this ID does not exist."
                .formatted(notPresentGroupId));
  }

  @Test
  public void shouldRejectIfEntityIsNotPresent() {
    // given
    final var groupId = Strings.newRandomValidIdentityId();
    final var name = UUID.randomUUID().toString();
    final var groupRecord = engine.group().newGroup(name).withGroupId(groupId).create();

    // when
    final var createdGroup = groupRecord.getValue();
    final var notPresentUpdateRecord =
        engine
            .group()
            .addEntity(groupId)
            .withEntityId("entityId")
            .withEntityType(EntityType.USER)
            .expectRejection()
            .add();

    // then
    assertThat(createdGroup).hasName(name);
    assertThat(notPresentUpdateRecord)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to add an entity with ID '%s' and type '%s' to group with ID '%s', but the entity does not exist."
                .formatted("entityId", EntityType.USER, groupId));
  }

  @Test
  public void shouldRejectIfEntityIsAlreadyAssigned() {
    // given
    // TODO: refactor this with https://github.com/camunda/camunda/issues/30476
    final var groupId = "123";
    final var name = UUID.randomUUID().toString();
    engine.group().newGroup(name).withGroupId(groupId).create();
    final var createdUser =
        engine
            .user()
            .newUser("foo")
            .withEmail("foo@bar")
            .withName("Foo Bar")
            .withPassword("zabraboof")
            .create();
    engine
        .group()
        .addEntity(groupId)
        .withEntityId(createdUser.getValue().getUsername())
        .withEntityType(EntityType.USER)
        .add();

    // when
    final var notPresentUpdateRecord =
        engine
            .group()
            .addEntity(groupId)
            .withEntityId(createdUser.getValue().getUsername())
            .withEntityType(EntityType.USER)
            .expectRejection()
            .add();

    // then
    assertThat(notPresentUpdateRecord)
        .hasRejectionType(RejectionType.ALREADY_EXISTS)
        .hasRejectionReason(
            "Expected to add entity with ID '%s' to group with ID '%s', but the entity is already assigned to this group."
                .formatted(createdUser.getValue().getUsername(), groupId));
  }

  @Test
  public void shouldRemoveEntityToGroup() {
    // TODO: refactor the test with https://github.com/camunda/camunda/issues/30029
    // given
    final var groupId = "123";
    final var groupKey = Long.parseLong(groupId);
    final var user =
        engine
            .user()
            .newUser("foo")
            .withEmail("foo@bar")
            .withName("Foo Bar")
            .withPassword("zabraboof")
            .create();
    final var name = UUID.randomUUID().toString();
    engine.group().newGroup(name).withGroupId(groupId).create();
    engine
        .group()
        .addEntity(groupId)
        .withEntityId(user.getValue().getUsername())
        .withEntityType(EntityType.USER)
        .add();

    // when
    final var groupWithRemovedEntity =
        engine
            .group()
            .removeEntity(groupKey)
            .withEntityId(user.getValue().getUsername())
            .withEntityType(EntityType.USER)
            .remove()
            .getValue();

    // then
    assertThat(groupWithRemovedEntity)
        .hasGroupKey(groupKey)
        .hasEntityId(user.getValue().getUsername())
        .hasEntityType(EntityType.USER);
  }

  @Test
  public void shouldRejectIfGroupIsNotPresentEntityRemoval() {
    // TODO: refactor the test with https://github.com/camunda/camunda/issues/30029
    // when
    final var notPresentGroupKey = 1L;
    final var notPresentUpdateRecord =
        engine.group().removeEntity(notPresentGroupKey).expectRejection().remove();

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
            .withEntityId("entityId")
            .withEntityType(EntityType.USER)
            .expectRejection()
            .remove();

    // then
    assertThat(createdGroup).hasName(name);
    assertThat(notPresentUpdateRecord)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to remove an entity with ID '%s' and type '%s' from group with key '%s', but the entity does not exist."
                .formatted("entityId", EntityType.USER, groupKey));
  }

  @Test
  public void shouldDeleteGroup() {
    // given
    final var groupId = UUID.randomUUID().toString();
    final var name = UUID.randomUUID().toString();
    engine.group().newGroup(name).withGroupId(groupId).create();

    // when
    final var deletedGroup = engine.group().deleteGroup(groupId).delete().getValue();

    // then
    assertThat(deletedGroup).hasGroupId(groupId);
  }

  @Test
  public void shouldDeleteGroupWithAssignedEntities() {
    // given
    final var groupId = "123";
    final var groupKey = Long.parseLong(groupId);
    final var user =
        engine
            .user()
            .newUser("foo")
            .withEmail("foo@bar")
            .withName("Foo Bar")
            .withPassword("zabraboof")
            .create();
    final var name = UUID.randomUUID().toString();
    engine.group().newGroup(name).withGroupId(groupId).create().getValue().getGroupKey();
    engine
        .group()
        .addEntity(groupId)
        .withEntityId(user.getValue().getUsername())
        .withEntityType(EntityType.USER)
        .add();

    // when
    final var deletedGroup =
        engine.group().deleteGroup(groupId).withGroupKey(groupKey).delete().getValue();

    // then
    final var groupRecords =
        RecordingExporter.groupRecords()
            .withIntents(GroupIntent.ENTITY_REMOVED, GroupIntent.DELETED)
            .withGroupKey(groupKey)
            .asList();
    assertThat(deletedGroup).hasGroupId(groupId);
    assertThat(groupRecords).hasSize(2);
    assertThat(groupRecords)
        .extracting(Record::getIntent)
        .containsExactly(GroupIntent.ENTITY_REMOVED, GroupIntent.DELETED);
  }

  @Test
  public void shouldRejectIfGroupIsNotPresentOnDeletion() {
    // when
    final var notPresentGroupId = UUID.randomUUID().toString();
    final var notPresentUpdateRecord =
        engine.group().deleteGroup(notPresentGroupId).expectRejection().delete();

    // then
    assertThat(notPresentUpdateRecord)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to delete group with ID '%s', but a group with this ID does not exist."
                .formatted(notPresentGroupId));
  }
}
