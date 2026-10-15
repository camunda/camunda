/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
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

public class GroupSetEntitiesTest {

  @Rule public final EngineRule engine = EngineRule.singlePartition();
  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldAddMissingMembers() {
    // given
    final var groupId = createGroup();
    final var existing = createUser();
    final var added = createUser();
    addMember(groupId, existing, EntityType.USER);

    // when
    final var response = setUsers(groupId, existing, added);

    // then
    assertThat(response.getIntent()).isEqualTo(GroupIntent.ENTITIES_SET);
    assertThat(response.getValue().getEntityType()).isEqualTo(EntityType.USER);
    assertThat(response.getValue().getEntityIds()).containsExactly(existing, added);
    assertThat(eventsOf(response))
        .extracting(Record::getIntent, r -> r.getValue().getEntityId())
        .containsExactly(
            tuple(GroupIntent.ENTITY_ADDED, added), tuple(GroupIntent.ENTITIES_SET, ""));
  }

  @Test
  public void shouldRemoveExtraMembers() {
    // given
    final var groupId = createGroup();
    final var kept = createUser();
    final var removed = createUser();
    addMember(groupId, kept, EntityType.USER);
    addMember(groupId, removed, EntityType.USER);

    // when
    final var response = setUsers(groupId, kept);

    // then
    assertThat(eventsOf(response))
        .extracting(Record::getIntent, r -> r.getValue().getEntityId())
        .containsExactly(
            tuple(GroupIntent.ENTITY_REMOVED, removed), tuple(GroupIntent.ENTITIES_SET, ""));
  }

  @Test
  public void shouldLeaveMembersOfOtherTypesUntouched() {
    // given
    final var groupId = createGroup();
    final var clientId = Strings.newRandomValidIdentityId();
    final var mappingRuleId = createMappingRule();
    final var user = createUser();
    addMember(groupId, clientId, EntityType.CLIENT);
    addMember(groupId, mappingRuleId, EntityType.MAPPING_RULE);

    // when
    final var response = setUsers(groupId, user);

    // then
    assertThat(eventsOf(response))
        .extracting(Record::getIntent, r -> r.getValue().getEntityId())
        .containsExactly(
            tuple(GroupIntent.ENTITY_ADDED, user), tuple(GroupIntent.ENTITIES_SET, ""));
  }

  @Test
  public void shouldOnlyEmitEntitiesSetWhenMembersAlreadyEqual() {
    // given
    final var groupId = createGroup();
    final var first = createUser();
    final var second = createUser();
    addMember(groupId, first, EntityType.USER);
    addMember(groupId, second, EntityType.USER);

    // when
    final var response = setUsers(groupId, second, first);

    // then
    assertThat(eventsOf(response))
        .extracting(Record::getIntent)
        .containsExactly(GroupIntent.ENTITIES_SET);
  }

  @Test
  public void shouldRemoveAllMembersOfTypeWhenListIsEmpty() {
    // given
    final var groupId = createGroup();
    final var first = createUser();
    final var second = createUser();
    final var mappingRuleId = createMappingRule();
    addMember(groupId, first, EntityType.USER);
    addMember(groupId, second, EntityType.USER);
    addMember(groupId, mappingRuleId, EntityType.MAPPING_RULE);

    // when
    final var response = setUsers(groupId);

    // then
    assertThat(eventsOf(response))
        .extracting(Record::getIntent)
        .containsExactlyInAnyOrder(
            GroupIntent.ENTITY_REMOVED, GroupIntent.ENTITY_REMOVED, GroupIntent.ENTITIES_SET);
    assertThat(response.getValue().getEntityIds()).isEmpty();
  }

  @Test
  public void shouldDeduplicateInput() {
    // given
    final var groupId = createGroup();
    final var user = createUser();

    // when
    final var response = setUsers(groupId, user, user);

    // then
    assertThat(response.getValue().getEntityIds()).containsExactly(user);
    assertThat(eventsOf(response))
        .extracting(Record::getIntent)
        .containsExactly(GroupIntent.ENTITY_ADDED, GroupIntent.ENTITIES_SET);
  }

  @Test
  public void shouldRejectIfGroupDoesNotExist() {
    // when
    final var rejection =
        engine
            .group()
            .setEntities(Strings.newRandomValidIdentityId())
            .withEntityType(EntityType.USER)
            .withEntityIds(List.of(createUser()))
            .expectRejection()
            .set();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
    assertThat(rejection.getRejectionReason()).contains("does not exist");
  }

  @Test
  public void shouldRejectUnsupportedEntityType() {
    // given
    final var groupId = createGroup();

    // when
    final var rejection =
        engine
            .group()
            .setEntities(groupId)
            .withEntityType(EntityType.GROUP)
            .withEntityIds(List.of("x"))
            .expectRejection()
            .set();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
  }

  @Test
  public void shouldRejectWholeCommandIfAnyMemberIsInvalid() {
    // given
    final var groupId = createGroup();
    final var validMappingRule = createMappingRule();
    final var removedMappingRule = createMappingRule();
    addMember(groupId, removedMappingRule, EntityType.MAPPING_RULE);
    final var unknownMappingRule = Strings.newRandomValidIdentityId();

    // when
    final var rejection =
        engine
            .group()
            .setEntities(groupId)
            .withEntityType(EntityType.MAPPING_RULE)
            .withEntityIds(List.of(validMappingRule, unknownMappingRule))
            .expectRejection()
            .set();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
    // the failed command changed nothing: a follow-up command still sees the original member
    final var response =
        engine
            .group()
            .setEntities(groupId)
            .withEntityType(EntityType.MAPPING_RULE)
            .withEntityIds(List.of(removedMappingRule))
            .set();
    assertThat(eventsOf(response))
        .extracting(Record::getIntent)
        .containsExactly(GroupIntent.ENTITIES_SET);
  }

  private String createGroup() {
    final var groupId = Strings.newRandomValidIdentityId();
    engine.group().newGroup(groupId).withName(groupId).create();
    return groupId;
  }

  private String createUser() {
    final var username = Strings.newRandomValidIdentityId();
    engine
        .user()
        .newUser(username)
        .withName(username)
        .withEmail(username + "@example.com")
        .withPassword("password")
        .create();
    return username;
  }

  private String createMappingRule() {
    return engine
        .mappingRule()
        .newMappingRule(Strings.newRandomValidIdentityId())
        .withClaimName("claimName")
        .withClaimValue(Strings.newRandomValidIdentityId())
        .withName("name")
        .create()
        .getValue()
        .getMappingRuleId();
  }

  private void addMember(final String groupId, final String entityId, final EntityType type) {
    engine.group().addEntity(groupId).withEntityId(entityId).withEntityType(type).add();
  }

  private Record<GroupRecordValue> setUsers(final String groupId, final String... usernames) {
    return engine
        .group()
        .setEntities(groupId)
        .withEntityType(EntityType.USER)
        .withEntityIds(List.of(usernames))
        .set();
  }

  private List<Record<GroupRecordValue>> eventsOf(final Record<GroupRecordValue> response) {
    return RecordingExporter.groupRecords()
        .onlyEvents()
        .withSourceRecordPosition(response.getSourceRecordPosition())
        .limit(r -> r.getIntent() == GroupIntent.ENTITIES_SET)
        .toList();
  }
}
