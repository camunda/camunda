/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.mappingrule;

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

public class MappingRuleTest {

  @Rule public final EngineRule engine = EngineRule.singlePartition();
  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldCreateMappingRule() {
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    final var id = UUID.randomUUID().toString();
    final var name = UUID.randomUUID().toString();
    final var mappingRuleRecord =
        engine
            .mappingRule()
            .newMappingRule(id)
            .withClaimName(claimName)
            .withClaimValue(claimValue)
            .withName(name)
            .create();

    final var createMappingRule = mappingRuleRecord.getValue();
    assertThat(createMappingRule)
        .isNotNull()
        .hasClaimName(claimName)
        .hasClaimValue(claimValue)
        .hasName(name)
        .hasMappingRuleId(id);
  }

  @Test
  public void shouldNotDuplicateWithSameClaim() {
    // given
    final var mappingRuleId = Strings.newRandomValidIdentityId();
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    final var name = UUID.randomUUID().toString();
    engine
        .mappingRule()
        .newMappingRule(mappingRuleId)
        .withClaimName(claimName)
        .withClaimValue(claimValue)
        .withName(name)
        .create();

    // when
    final var duplicatedMappingRecord =
        engine
            .mappingRule()
            .newMappingRule(mappingRuleId)
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
        .mappingRule()
        .newMappingRule(id)
        .withClaimName(claimName)
        .withClaimValue(claimValue)
        .withName(name)
        .create();

    // when
    final var duplicatedMappingRuleRecord =
        engine
            .mappingRule()
            .newMappingRule(id)
            .withClaimValue(UUID.randomUUID().toString())
            .withClaimName(UUID.randomUUID().toString())
            .withName(UUID.randomUUID().toString())
            .expectRejection()
            .create();

    assertThat(duplicatedMappingRuleRecord)
        .hasRejectionType(RejectionType.ALREADY_EXISTS)
        .hasRejectionReason(
            String.format(
                "Expected to create mapping rule with id '%s', but a mapping rule with this id already exists.",
                id));
  }

  @Test
  public void shouldUpdateMappingRule() {
    // given
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    final var name = UUID.randomUUID().toString();
    final var id = UUID.randomUUID().toString();
    engine
        .mappingRule()
        .newMappingRule(id)
        .withClaimName(claimName)
        .withClaimValue(claimValue)
        .withName(name)
        .create()
        .getKey();

    // when
    final var updatedMappingRule =
        engine
            .mappingRule()
            .updateMappingRule(id)
            .withClaimName(claimName + "New")
            .withClaimValue(claimValue + "New")
            .withName(name + "New")
            .update()
            .getValue();

    // then
    assertThat(updatedMappingRule)
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
    final var updateMappingRuleToExisting =
        engine
            .mappingRule()
            .updateMappingRule(id)
            .withClaimName(claimName)
            .withClaimValue(claimValue)
            .withName(name)
            .expectRejection()
            .update();

    assertThat(updateMappingRuleToExisting)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            String.format(
                "Expected to update mapping rule with id '%s', but a mapping rule with this id does not exist.",
                id));
  }

  @Test
  public void shouldNotUpdateToExistingClaim() {
    // given
    final var mappingRuleId = Strings.newRandomValidIdentityId();
    final var existingClaimName = UUID.randomUUID().toString();
    final var existingClaimValue = UUID.randomUUID().toString();
    final var existingName = UUID.randomUUID().toString();
    engine
        .mappingRule()
        .newMappingRule(mappingRuleId)
        .withClaimName(existingClaimName)
        .withClaimValue(existingClaimValue)
        .withName(existingName)
        .create();

    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    final var name = UUID.randomUUID().toString();
    final var id = UUID.randomUUID().toString();
    engine
        .mappingRule()
        .newMappingRule(id)
        .withClaimName(claimName)
        .withClaimValue(claimValue)
        .withName(name)
        .create();

    // when
    final var updateMappingToExisting =
        engine
            .mappingRule()
            .updateMappingRule(id)
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
    final var mappingRuleId = UUID.randomUUID().toString();

    engine
        .mappingRule()
        .newMappingRule(mappingRuleId)
        .withClaimName(claimName)
        .withClaimValue(claimValue)
        .withName(name)
        .create()
        .getValue();

    // when
    final var deletedMappingRule =
        engine.mappingRule().deleteMappingRule(mappingRuleId).delete().getValue();

    // then
    assertThat(deletedMappingRule).isNotNull().hasMappingRuleId(mappingRuleId);
  }

  @Test
  public void shouldCleanupGroupAndRoleMembership() {
    final var claimName = UUID.randomUUID().toString();
    final var claimValue = UUID.randomUUID().toString();
    final var name = UUID.randomUUID().toString();
    final var mappingRuleId = Strings.newRandomValidIdentityId();
    final var mappingRuleRecord =
        engine
            .mappingRule()
            .newMappingRule(mappingRuleId)
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
        .withEntityId(mappingRuleId)
        .withEntityType(EntityType.MAPPING_RULE)
        .add();
    engine
        .role()
        .addEntity(roleId)
        .withEntityId(mappingRuleId)
        .withEntityType(EntityType.MAPPING_RULE)
        .add();

    // when
    engine.mappingRule().deleteMappingRule(mappingRuleRecord.getValue().getMappingRuleId()).delete();

    // then
    Assertions.assertThat(
            RecordingExporter.groupRecords(GroupIntent.ENTITY_REMOVED)
                .withGroupId(groupId)
                .withEntityId(mappingRuleId)
                .withEntityType(EntityType.MAPPING_RULE)
                .exists())
        .isTrue();
    Assertions.assertThat(
            RecordingExporter.roleRecords(RoleIntent.ENTITY_REMOVED)
                .withRoleId(roleId)
                .withEntityId(mappingRuleId)
                .withEntityType(EntityType.MAPPING_RULE)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldRejectIfMappingRuleIsNotPresent() {
    // when
    final var deletedMappingRule =
        engine.mappingRule().deleteMappingRule("id").expectRejection().delete();

    // then
    assertThat(deletedMappingRule)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to delete mapping rule with id 'id', but a mapping rule with this id does not exist.");
  }
}
