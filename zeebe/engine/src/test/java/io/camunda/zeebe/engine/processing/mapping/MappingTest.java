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
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.Ignore;
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
            .newMapping(claimName)
            .withClaimValue(claimValue)
            .withMappingId(id)
            .withName(name)
            .create();

    final var createMapping = mappingRecord.getValue();
    Assertions.assertThat(createMapping)
        .isNotNull()
        .hasFieldOrProperty("mappingKey")
        .hasFieldOrPropertyWithValue("claimName", claimName)
        .hasFieldOrPropertyWithValue("claimValue", claimValue)
        .hasFieldOrPropertyWithValue("name", name)
        .hasFieldOrPropertyWithValue("mappingId", id);
  }

  @Test
  public void shouldNotDuplicateWithSameClaim() {
    // given
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    engine.mapping().newMapping(claimName).withClaimValue(claimValue).create();

    // when
    final var duplicatedMappingRecord =
        engine
            .mapping()
            .newMapping(claimName)
            .withClaimValue(claimValue)
            .expectRejection()
            .create();

    assertThat(duplicatedMappingRecord)
        .hasRejectionType(RejectionType.ALREADY_EXISTS)
        .hasRejectionReason(
            String.format(
                "Expected to create mapping with claimName '%s' and claimValue '%s', but a mapping with this claim already exists.",
                claimName, claimValue));
  }

  @Test
  public void shouldNotDuplicateWithSameId() {
    // given
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    final var id = UUID.randomUUID().toString();
    engine.mapping().newMapping(claimName).withClaimValue(claimValue).withMappingId(id).create();

    // when
    final var duplicatedMappingRecord =
        engine
            .mapping()
            .newMapping(UUID.randomUUID().toString())
            .withClaimValue(UUID.randomUUID().toString())
            .withMappingId(id)
            .expectRejection()
            .create();

    assertThat(duplicatedMappingRecord)
        .hasRejectionType(RejectionType.ALREADY_EXISTS)
        .hasRejectionReason(
            String.format(
                "Expected to create mapping with id '%s', but a mapping with this id already exists.",
                id));
  }

  @Test
  public void shouldUpdateMapping() {
    // given
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    final var name = UUID.randomUUID().toString();
    final var id = UUID.randomUUID().toString();
    final var mappingKey =
        engine
            .mapping()
            .newMapping(claimName)
            .withClaimValue(claimValue)
            .withName(name)
            .withMappingId(id)
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
    Assertions.assertThat(updatedMapping)
        .isNotNull()
        .hasFieldOrPropertyWithValue("mappingId", id)
        .hasFieldOrPropertyWithValue("name", name + "New")
        .hasFieldOrPropertyWithValue("claimName", claimName + "New")
        .hasFieldOrPropertyWithValue("claimValue", claimValue + "New");
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
                "Expected to update mapping with id '%s', but a mapping with this id does not exist.",
                id));
  }

  @Test
  public void shouldNotUpdateToExistingClaim() {
    // given
    final var existingClaimName = UUID.randomUUID().toString();
    final var existingClaimValue = UUID.randomUUID().toString();
    engine.mapping().newMapping(existingClaimName).withClaimValue(existingClaimValue).create();

    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    final var name = UUID.randomUUID().toString();
    final var id = UUID.randomUUID().toString();
    final var mappingKey =
        engine
            .mapping()
            .newMapping(claimName)
            .withClaimValue(claimValue)
            .withName(name)
            .withMappingId(id)
            .create();

    // when
    final var updateMappingToExisting =
        engine
            .mapping()
            .updateMapping(id)
            .withClaimName(existingClaimName)
            .withClaimValue(existingClaimValue)
            .withName(name)
            .expectRejection()
            .update();

    assertThat(updateMappingToExisting)
        .hasRejectionType(RejectionType.ALREADY_EXISTS)
        .hasRejectionReason(
            String.format(
                "Expected to create mapping with claimName '%s' and claimValue '%s', but a mapping with this claim already exists.",
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
        .newMapping(claimName)
        .withClaimValue(claimValue)
        .withName(name)
        .withMappingId(mappingId)
        .create()
        .getValue();

    // when
    final var deletedMapping = engine.mapping().deleteMapping(mappingId).delete().getValue();

    // then
    Assertions.assertThat(deletedMapping)
        .isNotNull()
        .hasFieldOrPropertyWithValue("mappingId", mappingId);
  }

  @Ignore("https://github.com/camunda/camunda/issues/30092")
  @Test
  public void shouldCleanupGroupAndRoleMembership() {
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    final var mappingRecord =
        engine.mapping().newMapping(claimName).withClaimValue(claimValue).create();
    final var groupId = "123";
    final var groupKey = Long.parseLong(groupId);
    engine.group().newGroup("group").withGroupId(groupId).create();
    final var role = engine.role().newRole("role").create();
    engine
        .group()
        .addEntity(groupId)
        .withEntityKey(mappingRecord.getKey())
        .withEntityType(EntityType.MAPPING)
        .add();
    engine
        .role()
        .addEntity(role.getKey())
        .withEntityKey(mappingRecord.getKey())
        .withEntityType(EntityType.MAPPING)
        .add();

    // when
    engine.mapping().deleteMapping(mappingRecord.getValue().getMappingId()).delete();

    // then
    Assertions.assertThat(
            RecordingExporter.groupRecords(GroupIntent.ENTITY_REMOVED)
                .withGroupKey(groupKey)
                .withEntityKey(mappingRecord.getKey())
                .exists())
        .isTrue();
    Assertions.assertThat(
            RecordingExporter.roleRecords(RoleIntent.ENTITY_REMOVED)
                .withRoleKey(role.getKey())
                .withEntityKey(mappingRecord.getKey())
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
