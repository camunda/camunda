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
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
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
    final var groupRecord = engine.group().newGroup(groupId).withName(name).create();

    final var createdGroup = groupRecord.getValue();
    assertThat(createdGroup).hasName(name);
    assertThat(createdGroup).hasGroupId(groupId);
  }

  @Test
  public void shouldNotDuplicate() {
    // given
    final var name = UUID.randomUUID().toString();
    final var groupId = Strings.newRandomValidIdentityId();
    final var groupRecord = engine.group().newGroup(groupId).withName(name).create();

    // when
    final var duplicatedGroupRecord =
        engine.group().newGroup(groupId).withName(name).expectRejection().create();

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
    engine.group().newGroup(groupId).withName(name).create();

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
    final var groupId = Strings.newRandomValidIdentityId();
    final var username =
        engine
            .user()
            .newUser("foo")
            .withEmail("foo@bar")
            .withName("Foo Bar")
            .withPassword("zabraboof")
            .create()
            .getValue()
            .getUsername();
    final var name = UUID.randomUUID().toString();
    engine.group().newGroup(groupId).withName(name).create().getValue().getGroupKey();

    // when
    final var updatedGroup =
        engine
            .group()
            .addEntity(groupId)
            .withEntityId(username)
            .withEntityType(EntityType.USER)
            .add()
            .getValue();

    // then
    assertThat(updatedGroup).hasEntityId(username).hasEntityType(EntityType.USER);
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
  public void shouldNotRejectIfUserIsNotPresent() {
    // given
    final var groupId = Strings.newRandomValidIdentityId();
    final var name = UUID.randomUUID().toString();
    engine.group().newGroup(groupId).withName(name).create();

    // when
    final var notPresentEntityId = Strings.newRandomValidIdentityId();
    final var updatedGroup =
        engine
            .group()
            .addEntity(groupId)
            .withEntityId(notPresentEntityId)
            .withEntityType(EntityType.USER)
            .add()
            .getValue();

    // then
    assertThat(updatedGroup).hasEntityId(notPresentEntityId);
  }

  @Test
  public void shouldRejectIfMappingIsNotPresent() {
    // given
    final var groupId = Strings.newRandomValidIdentityId();
    final var name = UUID.randomUUID().toString();
    final var groupRecord = engine.group().newGroup(groupId).withName(name).create();
    final var notPresentEntityId = Strings.newRandomValidIdentityId();

    // when
    final var createdGroup = groupRecord.getValue();
    final var notPresentUpdateRecord =
        engine
            .group()
            .addEntity(groupId)
            .withEntityId(notPresentEntityId)
            .withEntityType(EntityType.MAPPING_RULE)
            .expectRejection()
            .add();

    // then
    assertThat(createdGroup).hasName(name);
    assertThat(notPresentUpdateRecord)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to add an entity with ID '%s' and type '%s' to group with ID '%s', but the entity does not exist."
                .formatted(notPresentEntityId, EntityType.MAPPING_RULE, groupId));
  }

  @Test
  public void shouldRejectIfEntityIsAlreadyAssigned() {
    // given
    final var groupId = Strings.newRandomValidIdentityId();
    final var name = UUID.randomUUID().toString();
    engine.group().newGroup(groupId).withName(name).create();
    final var username =
        engine
            .user()
            .newUser("foo")
            .withEmail("foo@bar")
            .withName("Foo Bar")
            .withPassword("zabraboof")
            .create()
            .getValue()
            .getUsername();
    engine.group().addEntity(groupId).withEntityId(username).withEntityType(EntityType.USER).add();

    // when
    final var notPresentUpdateRecord =
        engine
            .group()
            .addEntity(groupId)
            .withEntityId(username)
            .withEntityType(EntityType.USER)
            .expectRejection()
            .add();

    // then
    assertThat(notPresentUpdateRecord)
        .hasRejectionType(RejectionType.ALREADY_EXISTS)
        .hasRejectionReason(
            "Expected to add entity with ID '%s' to group with ID '%s', but the entity is already assigned to this group."
                .formatted(username, groupId));
  }

  @Test
  public void shouldRemoveEntityToGroup() {
    // given
    final var groupId = Strings.newRandomValidIdentityId();
    final var username =
        engine
            .user()
            .newUser("foo")
            .withEmail("foo@bar")
            .withName("Foo Bar")
            .withPassword("zabraboof")
            .create()
            .getValue()
            .getUsername();
    final var name = UUID.randomUUID().toString();
    engine.group().newGroup(groupId).withName(name).create();
    engine.group().addEntity(groupId).withEntityId(username).withEntityType(EntityType.USER).add();

    // when
    final var groupWithRemovedEntity =
        engine
            .group()
            .removeEntity(groupId)
            .withEntityId(username)
            .withEntityType(EntityType.USER)
            .remove()
            .getValue();

    // then
    assertThat(groupWithRemovedEntity)
        .hasGroupId(groupId)
        .hasEntityId(username)
        .hasEntityType(EntityType.USER);
  }

  @Test
  public void shouldRejectIfGroupIsNotPresentEntityRemoval() {
    // when
    final var notPresentGroupId = Strings.newRandomValidIdentityId();
    final var notPresentUpdateRecord =
        engine.group().removeEntity(notPresentGroupId).expectRejection().remove();

    // then
    assertThat(notPresentUpdateRecord)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to update group with ID '%s', but a group with this ID does not exist."
                .formatted(notPresentGroupId));
  }

  @Test
  public void shouldRejectIfUserIsNotAssignedEntityRemoval() {
    // given
    final var groupId = Strings.newRandomValidIdentityId();
    final var notPresentEntityId = Strings.newRandomValidIdentityId();
    final var name = UUID.randomUUID().toString();
    engine.group().newGroup(groupId).withName(name).create();

    // when
    final var notAssignedUpdate =
        engine
            .group()
            .removeEntity(groupId)
            .withEntityId(notPresentEntityId)
            .withEntityType(EntityType.USER)
            .expectRejection()
            .remove();

    // then
    assertThat(notAssignedUpdate)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to remove entity with ID '%s' from group with ID '%s', but the entity is not assigned to this group."
                .formatted(notPresentEntityId, groupId));
  }

  @Test
  public void shouldRejectIfMappingIsNotPresentEntityRemoval() {
    // given
    final var groupId = Strings.newRandomValidIdentityId();
    final var notPresentEntityId = Strings.newRandomValidIdentityId();
    final var name = UUID.randomUUID().toString();
    final var groupRecord = engine.group().newGroup(groupId).withName(name).create();

    // when
    final var createdGroup = groupRecord.getValue();
    final var notPresentUpdateRecord =
        engine
            .group()
            .removeEntity(groupId)
            .withEntityId(notPresentEntityId)
            .withEntityType(EntityType.MAPPING_RULE)
            .expectRejection()
            .remove();

    // then
    assertThat(createdGroup).hasName(name);
    assertThat(notPresentUpdateRecord)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to remove an entity with ID '%s' and type '%s' from group with ID '%s', but the entity does not exist."
                .formatted(notPresentEntityId, EntityType.MAPPING_RULE, groupId));
  }

  @Test
  public void shouldDeleteGroup() {
    // given
    final var groupId = UUID.randomUUID().toString();
    final var name = UUID.randomUUID().toString();
    engine.group().newGroup(groupId).withName(name).create();

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
    final var username =
        engine
            .user()
            .newUser("foo")
            .withEmail("foo@bar")
            .withName("Foo Bar")
            .withPassword("zabraboof")
            .create()
            .getValue()
            .getUsername();
    final var name = UUID.randomUUID().toString();
    engine.group().newGroup(groupId).withName(name).create();
    engine.group().addEntity(groupId).withEntityId(username).withEntityType(EntityType.USER).add();

    // when
    final var deletedGroup =
        engine.group().deleteGroup(groupId).withGroupKey(groupKey).delete().getValue();

    // then
    final var groupRecords =
        RecordingExporter.groupRecords()
            .withIntents(GroupIntent.ENTITY_REMOVED, GroupIntent.DELETED)
            .withGroupId(groupId)
            .asList();
    assertThat(deletedGroup).hasGroupId(groupId);
    assertThat(groupRecords).hasSize(2);
    assertThat(groupRecords)
        .extracting(Record::getIntent)
        .containsExactly(GroupIntent.ENTITY_REMOVED, GroupIntent.DELETED);
  }

  @Test
  public void shouldRemoveDeletedGroupFromItsRolesAndTenants() {
    // given a group that is a member of a role and of a tenant
    final var groupId = Strings.newRandomValidIdentityId();
    final var roleId = Strings.newRandomValidIdentityId();
    final var tenantId = Strings.newRandomValidIdentityId();
    engine.group().newGroup(groupId).withName("group").create();
    engine.role().newRole(roleId).create();
    engine.tenant().newTenant().withTenantId(tenantId).create();
    engine.role().addEntity(roleId).withEntityId(groupId).withEntityType(EntityType.GROUP).add();
    engine
        .tenant()
        .addEntity(tenantId)
        .withEntityId(groupId)
        .withEntityType(EntityType.GROUP)
        .add();

    // when
    engine.group().deleteGroup(groupId).delete();

    // then the group is no longer a member of the role or the tenant
    Assertions.assertThat(
            RecordingExporter.roleRecords(RoleIntent.ENTITY_REMOVED)
                .withRoleId(roleId)
                .withEntityId(groupId)
                .exists())
        .isTrue();
    Assertions.assertThat(
            RecordingExporter.tenantRecords(TenantIntent.ENTITY_REMOVED)
                .withTenantId(tenantId)
                .withEntityId(groupId)
                .exists())
        .isTrue();

    // and a group created again with the same ID can be assigned anew, so it inherited nothing
    engine.group().newGroup(groupId).withName("new group").create();
    assertThat(
            engine
                .role()
                .addEntity(roleId)
                .withEntityId(groupId)
                .withEntityType(EntityType.GROUP)
                .add()
                .getIntent())
        .isEqualTo(RoleIntent.ENTITY_ADDED);
    assertThat(
            engine
                .tenant()
                .addEntity(tenantId)
                .withEntityId(groupId)
                .withEntityType(EntityType.GROUP)
                .add()
                .getIntent())
        .isEqualTo(TenantIntent.ENTITY_ADDED);
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

  @Test
  public void shouldAddClientEntityToGroup() {
    // given
    final var groupId = Strings.newRandomValidIdentityId();
    final var clientId = "application-" + UUID.randomUUID();
    final var name = UUID.randomUUID().toString();
    engine.group().newGroup(groupId).withName(name).create();

    // when
    final var updatedGroup =
        engine
            .group()
            .addEntity(groupId)
            .withEntityId(clientId)
            .withEntityType(EntityType.CLIENT)
            .add()
            .getValue();

    // then
    assertThat(updatedGroup).hasEntityId(clientId).hasEntityType(EntityType.CLIENT);
  }

  @Test
  public void shouldRemoveClientEntityFromGroup() {
    // given
    final var groupId = Strings.newRandomValidIdentityId();
    final var clientId = "application-" + UUID.randomUUID();
    final var name = UUID.randomUUID().toString();
    engine.group().newGroup(groupId).withName(name).create();
    engine
        .group()
        .addEntity(groupId)
        .withEntityId(clientId)
        .withEntityType(EntityType.CLIENT)
        .add();

    // when
    final var groupWithRemovedEntity =
        engine
            .group()
            .removeEntity(groupId)
            .withEntityId(clientId)
            .withEntityType(EntityType.CLIENT)
            .remove()
            .getValue();

    // then
    assertThat(groupWithRemovedEntity)
        .hasGroupId(groupId)
        .hasEntityId(clientId)
        .hasEntityType(EntityType.CLIENT);
  }

  @Test
  public void shouldRejectIfClientEntityIsAlreadyAssigned() {
    // given
    final var groupId = Strings.newRandomValidIdentityId();
    final var name = UUID.randomUUID().toString();
    engine.group().newGroup(groupId).withName(name).create();
    final var clientId = "application-" + UUID.randomUUID();
    engine
        .group()
        .addEntity(groupId)
        .withEntityId(clientId)
        .withEntityType(EntityType.CLIENT)
        .add();

    // when
    final var notPresentUpdateRecord =
        engine
            .group()
            .addEntity(groupId)
            .withEntityId(clientId)
            .withEntityType(EntityType.CLIENT)
            .expectRejection()
            .add();

    // then
    assertThat(notPresentUpdateRecord)
        .hasRejectionType(RejectionType.ALREADY_EXISTS)
        .hasRejectionReason(
            "Expected to add entity with ID '%s' to group with ID '%s', but the entity is already assigned to this group."
                .formatted(clientId, groupId));
  }

  @Test
  public void shouldRejectIfApplicationEntityIsNotAssignedEntityRemoval() {
    // given
    final var groupId = Strings.newRandomValidIdentityId();
    final var notPresentEntityId = "application-" + UUID.randomUUID();
    final var name = UUID.randomUUID().toString();
    engine.group().newGroup(groupId).withName(name).create();

    // when
    final var notAssignedUpdate =
        engine
            .group()
            .removeEntity(groupId)
            .withEntityId(notPresentEntityId)
            .withEntityType(EntityType.CLIENT)
            .expectRejection()
            .remove();

    // then
    assertThat(notAssignedUpdate)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to remove entity with ID '%s' from group with ID '%s', but the entity is not assigned to this group."
                .formatted(notPresentEntityId, groupId));
  }
}
