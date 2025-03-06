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
            .withId(id)
            .withName(name)
            .create();

    final var createMapping = mappingRecord.getValue();
    Assertions.assertThat(createMapping)
        .isNotNull()
        .hasFieldOrProperty("mappingKey")
        .hasFieldOrPropertyWithValue("claimName", claimName)
        .hasFieldOrPropertyWithValue("claimValue", claimValue)
        .hasFieldOrPropertyWithValue("name", name)
        .hasFieldOrPropertyWithValue("id", id);
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
    engine.mapping().newMapping(claimName).withClaimValue(claimValue).withId(id).create();

    // when
    final var duplicatedMappingRecord =
        engine
            .mapping()
            .newMapping(UUID.randomUUID().toString())
            .withClaimValue(UUID.randomUUID().toString())
            .withId(id)
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
  public void shouldDeleteMapping() {
    // given
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    final var name = UUID.randomUUID().toString();
    final var mappingKey =
        engine
            .mapping()
            .newMapping(claimName)
            .withClaimValue(claimValue)
            .withName(name)
            .create()
            .getKey();

    // when
    final var deletedMapping = engine.mapping().deleteMapping(mappingKey).delete().getValue();

    // then
    Assertions.assertThat(deletedMapping)
        .isNotNull()
        .hasFieldOrPropertyWithValue("mappingKey", mappingKey);
  }

  @Test
  public void shouldCleanupGroupAndRoleMembership() {
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    final var mappingRecord =
        engine.mapping().newMapping(claimName).withClaimValue(claimValue).create();
    final var group = engine.group().newGroup("group").create();
    final var role = engine.role().newRole("role").create();
    engine
        .group()
        .addEntity(group.getKey())
        .withEntityKey(mappingRecord.getValue().getId())
        .withEntityType(EntityType.MAPPING)
        .add();
    engine
        .role()
        .addEntity(role.getKey())
        .withEntityKey(mappingRecord.getValue().getId())
        .withEntityType(EntityType.MAPPING)
        .add();

    // when
    engine.mapping().deleteMapping(mappingRecord.getKey()).delete();

    // then
    Assertions.assertThat(
            RecordingExporter.groupRecords(GroupIntent.ENTITY_REMOVED)
                .withGroupKey(group.getKey())
                .withEntityKey(mappingRecord.getValue().getId())
                .exists())
        .isTrue();
    Assertions.assertThat(
            RecordingExporter.roleRecords(RoleIntent.ENTITY_REMOVED)
                .withRoleKey(role.getKey())
                .withEntityKey(mappingRecord.getValue().getId())
                .exists())
        .isTrue();
  }

  @Test
  public void shouldRejectIfMappingIsNotPresent() {
    // when
    final var deletedMapping = engine.mapping().deleteMapping(1L).expectRejection().delete();

    // then
    assertThat(deletedMapping)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to delete mapping with key '1', but a mapping with this key does not exist.");
  }
}
