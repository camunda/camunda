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
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.GroupRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class GroupSourceOwnershipTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldStoreSourceOnCreate() {
    // when
    final var created = createGroup("entra");

    // then
    assertThat(created.getValue().getSource()).isEqualTo("entra");
  }

  @Test
  public void shouldDefaultToNoSource() {
    // when
    final var created = createGroup("");

    // then
    assertThat(created.getValue().getSource()).isEmpty();
  }

  @Test
  public void shouldUpdateGroupOfSameSourceAndKeepSource() {
    // given
    final var groupId = createGroup("entra").getValue().getGroupId();

    // when
    final var updated =
        ENGINE
            .group()
            .updateGroup(groupId)
            .withSource("entra")
            .withName("Sales")
            .withChangedAttributes(GroupAttribute.NAME)
            .update()
            .getValue();

    // then
    assertThat(updated.getName()).isEqualTo("Sales");
    assertThat(updated.getSource()).isEqualTo("entra");
  }

  @Test
  public void shouldRejectUpdateOfGroupOfOtherSourceAsNotFound() {
    // given
    final var groupId = createGroup("entra").getValue().getGroupId();

    // when
    final var rejection =
        ENGINE
            .group()
            .updateGroup(groupId)
            .withSource("keycloak")
            .withName("Sales")
            .withChangedAttributes(GroupAttribute.NAME)
            .expectRejection()
            .update();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldAdoptUnownedGroupOnUpdateWithSourceMask() {
    // given
    final var groupId = createGroup("").getValue().getGroupId();

    // when
    final var updated =
        ENGINE
            .group()
            .updateGroup(groupId)
            .withSource("entra")
            .withExternalId("ext-1")
            .withChangedAttributes(GroupAttribute.SOURCE, GroupAttribute.EXTERNAL_ID)
            .update()
            .getValue();

    // then
    assertThat(updated.getSource()).isEqualTo("entra");
    assertThat(updated.getExternalId()).isEqualTo("ext-1");
  }

  @Test
  public void shouldRejectUpdateOfUnownedGroupWithoutAdoption() {
    // given
    final var groupId = createGroup("").getValue().getGroupId();

    // when
    final var rejection =
        ENGINE
            .group()
            .updateGroup(groupId)
            .withSource("entra")
            .withName("Sales")
            .withChangedAttributes(GroupAttribute.NAME)
            .expectRejection()
            .update();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason()).contains("not owned by source 'entra'");
  }

  @Test
  public void shouldRejectRestUpdateOfOwnedGroup() {
    // given
    final var groupId = createGroup("entra").getValue().getGroupId();

    // when
    final var rejection =
        ENGINE.group().updateGroup(groupId).withName("Sales").expectRejection().update();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason()).contains("managed by identity provider");
  }

  @Test
  public void shouldDeleteGroupOfSameSource() {
    // given
    final var groupId = createGroup("entra").getValue().getGroupId();

    // when
    ENGINE.group().deleteGroup(groupId).withSource("entra").delete();

    // then
    assertThat(RecordingExporter.groupRecords(GroupIntent.DELETED).withGroupId(groupId).exists())
        .isTrue();
  }

  @Test
  public void shouldRejectDeleteOfGroupOfOtherSourceAsNotFound() {
    // given
    final var groupId = createGroup("entra").getValue().getGroupId();

    // when
    final var rejection =
        ENGINE.group().deleteGroup(groupId).withSource("keycloak").expectRejection().delete();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldRejectDeleteOfUnownedGroupWithoutAdoption() {
    // given
    final var groupId = createGroup("").getValue().getGroupId();

    // when
    final var rejection =
        ENGINE.group().deleteGroup(groupId).withSource("entra").expectRejection().delete();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason()).contains("not owned by source");
  }

  @Test
  public void shouldRejectRestDeleteOfOwnedGroup() {
    // given
    final var groupId = createGroup("entra").getValue().getGroupId();

    // when
    final var rejection = ENGINE.group().deleteGroup(groupId).expectRejection().delete();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason()).contains("managed by identity provider");
  }

  @Test
  public void shouldAddMemberOfSameSource() {
    // given
    final var groupId = createGroup("entra").getValue().getGroupId();
    final var username = createUser("entra");

    // when
    final var added =
        ENGINE
            .group()
            .addEntity(groupId)
            .withSource("entra")
            .withEntityId(username)
            .withEntityType(EntityType.USER)
            .add();

    // then
    assertThat(added.getIntent()).isEqualTo(GroupIntent.ENTITY_ADDED);
  }

  @Test
  public void shouldRejectAddOfMemberOfOtherSource() {
    // given
    final var groupId = createGroup("entra").getValue().getGroupId();
    final var username = createUser("keycloak");

    // when
    final var rejection =
        ENGINE
            .group()
            .addEntity(groupId)
            .withSource("entra")
            .withEntityId(username)
            .withEntityType(EntityType.USER)
            .expectRejection()
            .add();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
  }

  @Test
  public void shouldRejectAddOfUnownedMember() {
    // given
    final var groupId = createGroup("entra").getValue().getGroupId();
    final var username = createUser("");

    // when
    final var rejection =
        ENGINE
            .group()
            .addEntity(groupId)
            .withSource("entra")
            .withEntityId(username)
            .withEntityType(EntityType.USER)
            .expectRejection()
            .add();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
  }

  @Test
  public void shouldRejectAddToGroupOfOtherSourceAsNotFound() {
    // given
    final var groupId = createGroup("entra").getValue().getGroupId();
    final var username = createUser("keycloak");

    // when
    final var rejection =
        ENGINE
            .group()
            .addEntity(groupId)
            .withSource("keycloak")
            .withEntityId(username)
            .withEntityType(EntityType.USER)
            .expectRejection()
            .add();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldAdoptUnownedGroupOnAddWithSourceMask() {
    // given
    final var groupId = createGroup("").getValue().getGroupId();
    final var username = createUser("entra");

    // when
    ENGINE
        .group()
        .addEntity(groupId)
        .withSource("entra")
        .withChangedAttributes(GroupAttribute.SOURCE)
        .withEntityId(username)
        .withEntityType(EntityType.USER)
        .add();

    // then
    assertThat(
            RecordingExporter.groupRecords(GroupIntent.UPDATED)
                .withGroupId(groupId)
                .getFirst()
                .getValue()
                .getSource())
        .isEqualTo("entra");
  }

  @Test
  public void shouldRejectAddToUnownedGroupWithoutAdoption() {
    // given
    final var groupId = createGroup("").getValue().getGroupId();
    final var username = createUser("entra");

    // when
    final var rejection =
        ENGINE
            .group()
            .addEntity(groupId)
            .withSource("entra")
            .withEntityId(username)
            .withEntityType(EntityType.USER)
            .expectRejection()
            .add();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason()).contains("not owned by source");
  }

  @Test
  public void shouldRejectRestAddOfUserToOwnedGroup() {
    // given
    final var groupId = createGroup("entra").getValue().getGroupId();
    final var username = createUser("entra");

    // when
    final var rejection =
        ENGINE
            .group()
            .addEntity(groupId)
            .withEntityId(username)
            .withEntityType(EntityType.USER)
            .expectRejection()
            .add();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason()).contains("managed by identity provider");
  }

  @Test
  public void shouldAllowRestAddOfMappingRuleToOwnedGroup() {
    // given
    final var groupId = createGroup("entra").getValue().getGroupId();
    final var mappingRuleId = createMappingRule();

    // when
    final var added =
        ENGINE
            .group()
            .addEntity(groupId)
            .withEntityId(mappingRuleId)
            .withEntityType(EntityType.MAPPING_RULE)
            .add();

    // then
    assertThat(added.getIntent()).isEqualTo(GroupIntent.ENTITY_ADDED);
  }

  @Test
  public void shouldAllowRestRemoveOfMappingRuleFromOwnedGroup() {
    // given
    final var groupId = createGroup("entra").getValue().getGroupId();
    final var mappingRuleId = createMappingRule();
    ENGINE
        .group()
        .addEntity(groupId)
        .withEntityId(mappingRuleId)
        .withEntityType(EntityType.MAPPING_RULE)
        .add();

    // when
    final var removed =
        ENGINE
            .group()
            .removeEntity(groupId)
            .withEntityId(mappingRuleId)
            .withEntityType(EntityType.MAPPING_RULE)
            .remove();

    // then
    assertThat(removed.getIntent()).isEqualTo(GroupIntent.ENTITY_REMOVED);
  }

  @Test
  public void shouldAllowRoleAssignmentToOwnedGroup() {
    // given
    final var groupId = createGroup("entra").getValue().getGroupId();
    final var roleId = Strings.newRandomValidIdentityId();
    ENGINE.role().newRole(roleId).withName(roleId).create();

    // when
    final var added =
        ENGINE
            .role()
            .addEntity(roleId)
            .withEntityId(groupId)
            .withEntityType(EntityType.GROUP)
            .add();

    // then
    assertThat(added.getIntent()).isEqualTo(RoleIntent.ENTITY_ADDED);
  }

  @Test
  public void shouldRemoveMemberOfSameSource() {
    // given
    final var groupId = createGroup("entra").getValue().getGroupId();
    final var username = createUser("entra");
    ENGINE
        .group()
        .addEntity(groupId)
        .withSource("entra")
        .withEntityId(username)
        .withEntityType(EntityType.USER)
        .add();

    // when
    final var removed =
        ENGINE
            .group()
            .removeEntity(groupId)
            .withSource("entra")
            .withEntityId(username)
            .withEntityType(EntityType.USER)
            .remove();

    // then
    assertThat(removed.getIntent()).isEqualTo(GroupIntent.ENTITY_REMOVED);
  }

  @Test
  public void shouldRejectRemoveFromGroupOfOtherSourceAsNotFound() {
    // given
    final var groupId = createGroup("entra").getValue().getGroupId();
    final var username = createUser("entra");
    ENGINE
        .group()
        .addEntity(groupId)
        .withSource("entra")
        .withEntityId(username)
        .withEntityType(EntityType.USER)
        .add();

    // when
    final var rejection =
        ENGINE
            .group()
            .removeEntity(groupId)
            .withSource("keycloak")
            .withEntityId(username)
            .withEntityType(EntityType.USER)
            .expectRejection()
            .remove();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldRejectRestRemoveOfUserFromOwnedGroup() {
    // given
    final var groupId = createGroup("entra").getValue().getGroupId();
    final var username = createUser("entra");
    ENGINE
        .group()
        .addEntity(groupId)
        .withSource("entra")
        .withEntityId(username)
        .withEntityType(EntityType.USER)
        .add();

    // when
    final var rejection =
        ENGINE
            .group()
            .removeEntity(groupId)
            .withEntityId(username)
            .withEntityType(EntityType.USER)
            .expectRejection()
            .remove();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason()).contains("managed by identity provider");
  }

  @Test
  public void shouldSetEntitiesOfSameSource() {
    // given
    final var groupId = createGroup("entra").getValue().getGroupId();
    final var first = createUser("entra");
    final var second = createUser("entra");

    // when
    final var response =
        ENGINE
            .group()
            .setEntities(groupId)
            .withSource("entra")
            .withEntityType(EntityType.USER)
            .withEntityIds(List.of(first, second))
            .set();

    // then
    assertThat(response.getValue().getEntityIds()).containsExactly(first, second);
  }

  @Test
  public void shouldRejectSetEntitiesWithMemberOfOtherSource() {
    // given
    final var groupId = createGroup("entra").getValue().getGroupId();
    final var own = createUser("entra");
    final var foreign = createUser("keycloak");

    // when
    final var rejection =
        ENGINE
            .group()
            .setEntities(groupId)
            .withSource("entra")
            .withEntityType(EntityType.USER)
            .withEntityIds(List.of(own, foreign))
            .expectRejection()
            .set();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
  }

  @Test
  public void shouldRejectSetEntitiesOnGroupOfOtherSourceAsNotFound() {
    // given
    final var groupId = createGroup("entra").getValue().getGroupId();
    final var username = createUser("keycloak");

    // when
    final var rejection =
        ENGINE
            .group()
            .setEntities(groupId)
            .withSource("keycloak")
            .withEntityType(EntityType.USER)
            .withEntityIds(List.of(username))
            .expectRejection()
            .set();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldAdoptUnownedGroupOnSetEntitiesWithSourceMask() {
    // given
    final var groupId = createGroup("").getValue().getGroupId();
    final var username = createUser("entra");

    // when
    ENGINE
        .group()
        .setEntities(groupId)
        .withSource("entra")
        .withChangedAttributes(GroupAttribute.SOURCE)
        .withEntityType(EntityType.USER)
        .withEntityIds(List.of(username))
        .set();

    // then
    assertThat(
            RecordingExporter.groupRecords(GroupIntent.UPDATED)
                .withGroupId(groupId)
                .getFirst()
                .getValue()
                .getSource())
        .isEqualTo("entra");
  }

  @Test
  public void shouldRejectRestSetEntitiesOfUsersOnOwnedGroup() {
    // given
    final var groupId = createGroup("entra").getValue().getGroupId();
    final var username = createUser("entra");

    // when
    final var rejection =
        ENGINE
            .group()
            .setEntities(groupId)
            .withEntityType(EntityType.USER)
            .withEntityIds(List.of(username))
            .expectRejection()
            .set();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason()).contains("managed by identity provider");
  }

  private static Record<GroupRecordValue> createGroup(final String source) {
    final var groupId = Strings.newRandomValidIdentityId();
    return ENGINE.group().newGroup(groupId).withName(groupId).withSource(source).create();
  }

  private static String createUser(final String source) {
    final var username = Strings.newRandomValidIdentityId();
    ENGINE.user().newUser(username).withName(username).withSource(source).create();
    return username;
  }

  private static String createMappingRule() {
    return ENGINE
        .mappingRule()
        .newMappingRule(Strings.newRandomValidIdentityId())
        .withClaimName("claimName")
        .withClaimValue(Strings.newRandomValidIdentityId())
        .withName("name")
        .create()
        .getValue()
        .getMappingRuleId();
  }
}
