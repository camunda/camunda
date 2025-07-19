/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.mapping;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class MappingTest {

  @Rule public final EngineRule engine = EngineRule.singlePartition();
  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldCreateMapping() {
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    final var id = UUID.randomUUID().toString();
    final var name = UUID.randomUUID().toString();
    final var mappingRecord =
        engine
            .mapping()
            .newMapping(id)
            .withClaimName(claimName)
            .withClaimValue(claimValue)
            .withName(name)
            .create();

    final var createMapping = mappingRecord.getValue();
    assertThat(createMapping)
        .isNotNull()
        .hasClaimName(claimName)
        .hasClaimValue(claimValue)
        .hasName(name)
        .hasMappingRuleId(id);
  }

  @Test
  public void shouldNotDuplicateWithSameClaim() {
    // given
    final var mappingId = Strings.newRandomValidIdentityId();
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    final var name = UUID.randomUUID().toString();
    engine
        .mapping()
        .newMapping(mappingId)
        .withClaimName(claimName)
        .withClaimValue(claimValue)
        .withName(name)
        .create();

    // when
    final var duplicatedMappingRecord =
        engine
            .mapping()
            .newMapping(mappingId)
            .withClaimName(claimName)
            .withClaimValue(claimValue)
            .withName(name)
            .expectRejection()
            .create();

    assertThat(duplicatedMappingRecord)
        .hasRejectionType(RejectionType.ALREADY_EXISTS)
        .hasRejectionReason(
            String.format(
                "Expected to create mapping rule with claimName '%s' and claimValue '%s', but a mapping rule with this claim already exists.",
                claimName, claimValue));
  }

  @Test
  public void shouldNotDuplicateWithSameId() {
    // given
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    final var name = UUID.randomUUID().toString();
    final var id = UUID.randomUUID().toString();
    engine
        .mapping()
        .newMapping(id)
        .withClaimName(claimName)
        .withClaimValue(claimValue)
        .withName(name)
        .create();

    // when
    final var duplicatedMappingRecord =
        engine
            .mapping()
            .newMapping(id)
            .withClaimValue(UUID.randomUUID().toString())
            .withClaimName(UUID.randomUUID().toString())
            .withName(UUID.randomUUID().toString())
            .expectRejection()
            .create();

    assertThat(duplicatedMappingRecord)
        .hasRejectionType(RejectionType.ALREADY_EXISTS)
        .hasRejectionReason(
            String.format(
                "Expected to create mapping rule with id '%s', but a mapping rule with this id already exists.",
                id));
  }

  @Test
  public void shouldUpdateMapping() {
    // given
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    final var name = UUID.randomUUID().toString();
    final var id = UUID.randomUUID().toString();
    engine
        .mapping()
        .newMapping(id)
        .withClaimName(claimName)
        .withClaimValue(claimValue)
        .withName(name)
        .create()
        .getKey();

    // when
    final var updatedMapping =
        engine
            .mapping()
            .updateMapping(id)
            .withClaimName(claimName + "New")
            .withClaimValue(claimValue + "New")
            .withName(name + "New")
            .update()
            .getValue();

    // then
    assertThat(updatedMapping)
        .isNotNull()
        .hasMappingRuleId(id)
        .hasName(name + "New")
        .hasClaimName(claimName + "New")
        .hasClaimValue(claimValue + "New");
  }

  @Test
  public void shouldNotUpdateNoneExistingId() {

    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    final var name = UUID.randomUUID().toString();
    final var id = UUID.randomUUID().toString();

    // when
    final var updateMappingToExisting =
        engine
            .mapping()
            .updateMapping(id)
            .withClaimName(claimName)
            .withClaimValue(claimValue)
            .withName(name)
            .expectRejection()
            .update();

    assertThat(updateMappingToExisting)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            String.format(
                "Expected to update mapping rule with id '%s', but a mapping rule with this id does not exist.",
                id));
  }

  @Test
  public void shouldNotUpdateToExistingClaim() {
    // given
    final var mappingId = Strings.newRandomValidIdentityId();
    final var existingClaimName = UUID.randomUUID().toString();
    final var existingClaimValue = UUID.randomUUID().toString();
    final var existingName = UUID.randomUUID().toString();
    engine
        .mapping()
        .newMapping(mappingId)
        .withClaimName(existingClaimName)
        .withClaimValue(existingClaimValue)
        .withName(existingName)
        .create();

    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    final var name = UUID.randomUUID().toString();
    final var id = UUID.randomUUID().toString();
    engine
        .mapping()
        .newMapping(id)
        .withClaimName(claimName)
        .withClaimValue(claimValue)
        .withName(name)
        .create();

    // when
    final var updateMappingToExisting =
        engine
            .mapping()
            .updateMapping(id)
            .withClaimName(existingClaimName)
            .withClaimValue(existingClaimValue)
            .withName(existingName)
            .expectRejection()
            .update();

    assertThat(updateMappingToExisting)
        .hasRejectionType(RejectionType.ALREADY_EXISTS)
        .hasRejectionReason(
            String.format(
                "Expected to update mapping rule with claimName '%s' and claimValue '%s', but a mapping rule with this claim already exists.",
                existingClaimName, existingClaimValue));
  }

  @Test
  public void shouldDeleteMapping() {
    // given
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    final var name = UUID.randomUUID().toString();
    final var mappingId = UUID.randomUUID().toString();

    engine
        .mapping()
        .newMapping(mappingId)
        .withClaimName(claimName)
        .withClaimValue(claimValue)
        .withName(name)
        .create()
        .getValue();

    // when
    final var deletedMapping = engine.mapping().deleteMapping(mappingId).delete().getValue();

    // then
    assertThat(deletedMapping).isNotNull().hasMappingRuleId(mappingId);
  }

  @Test
  public void shouldCleanupGroupAndRoleMembership() {
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    final var name = UUID.randomUUID().toString();
    final var mappingId = Strings.newRandomValidIdentityId();
    final var mappingRecord =
        engine
            .mapping()
            .newMapping(mappingId)
            .withClaimName(claimName)
            .withClaimValue(claimValue)
            .withName(name)
            .create();
    final var groupId = Strings.newRandomValidIdentityId();
    engine.group().newGroup(groupId).withName("group").create();
    final var roleId = Strings.newRandomValidIdentityId();
    engine.role().newRole(roleId).create();
    engine
        .group()
        .addEntity(groupId)
        .withEntityId(mappingId)
        .withEntityType(EntityType.MAPPING_RULE)
        .add();
    engine
        .role()
        .addEntity(roleId)
        .withEntityId(mappingId)
        .withEntityType(EntityType.MAPPING_RULE)
        .add();

    // when
    engine.mapping().deleteMapping(mappingRecord.getValue().getMappingRuleId()).delete();

    // then
    Assertions.assertThat(
            RecordingExporter.groupRecords(GroupIntent.ENTITY_REMOVED)
                .withGroupId(groupId)
                .withEntityId(mappingId)
                .withEntityType(EntityType.MAPPING_RULE)
                .exists())
        .isTrue();
    Assertions.assertThat(
            RecordingExporter.roleRecords(RoleIntent.ENTITY_REMOVED)
                .withRoleId(roleId)
                .withEntityId(mappingId)
                .withEntityType(EntityType.MAPPING_RULE)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldRejectIfMappingIsNotPresent() {
    // when
    final var deletedMapping = engine.mapping().deleteMapping("id").expectRejection().delete();

    // then
    assertThat(deletedMapping)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to delete mapping with id 'id', but a mapping with this id does not exist.");
  }
}
