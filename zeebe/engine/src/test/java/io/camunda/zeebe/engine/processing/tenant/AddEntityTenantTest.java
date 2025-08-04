/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.tenant;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;
import static io.camunda.zeebe.protocol.record.value.EntityType.GROUP;
import static io.camunda.zeebe.protocol.record.value.EntityType.MAPPING_RULE;
import static io.camunda.zeebe.protocol.record.value.EntityType.ROLE;
import static io.camunda.zeebe.protocol.record.value.EntityType.USER;

import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.engine.util.AuthorizationUtil;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.Rule;
import org.junit.Test;

public class AddEntityTenantTest {

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldAddMappingToTenant() {
    // given
    final var entityType = MAPPING_RULE;
    final var entityId = createMapping();
    final var tenantId = UUID.randomUUID().toString();
    final var tenantKey =
        engine
            .tenant()
            .newTenant()
            .withTenantId(tenantId)
            .withName("Tenant 1")
            .create()
            .getValue()
            .getTenantKey();

    // when add user entity to tenant
    final var updatedTenant =
        engine
            .tenant()
            .addEntity(tenantId)
            // keys
            .withEntityId(entityId)
            .withEntityType(entityType)
            .add()
            .getValue();

    // then assert that the entity was added correctly
    Assertions.assertThat(updatedTenant)
        .describedAs(
            "Entity of type %s with ID %s should be correctly added to tenant with ID %s",
            entityType, entityId, tenantKey)
        .isNotNull()
        .hasFieldOrPropertyWithValue("entityId", entityId)
        .hasFieldOrPropertyWithValue("tenantKey", tenantKey)
        .hasFieldOrPropertyWithValue("tenantId", tenantId)
        .hasFieldOrPropertyWithValue("entityType", entityType);
  }

  @Test
  public void shouldAddUserToTenant() {
    // given
    final var entityType = USER;
    final var user = createUser();
    final var userKey = user.getUserKey();
    final var username = user.getUsername();
    final var tenantId = UUID.randomUUID().toString();
    final var tenantKey =
        engine
            .tenant()
            .newTenant()
            .withTenantId(tenantId)
            .withName("Tenant 1")
            .create()
            .getValue()
            .getTenantKey();

    // when add user entity to tenant
    final var updatedTenant =
        engine
            .tenant()
            .addEntity(tenantId)
            .withEntityId(username)
            .withEntityType(entityType)
            .add()
            .getValue();

    // then assert that the entity was added correctly
    Assertions.assertThat(updatedTenant)
        .describedAs(
            "Entity of type %s with ID %s should be correctly added to tenant with ID %s",
            entityType, userKey, tenantKey)
        .isNotNull()
        .hasFieldOrPropertyWithValue("entityId", username)
        .hasFieldOrPropertyWithValue("tenantKey", tenantKey)
        .hasFieldOrPropertyWithValue("tenantId", tenantId)
        .hasFieldOrPropertyWithValue("entityType", entityType);
  }

  @Test
  public void shouldAddNonExistingUserToTenant() {
    // given
    final var entityType = USER;
    final var username = UUID.randomUUID().toString();
    final var tenantId = UUID.randomUUID().toString();
    final var tenantKey =
        engine
            .tenant()
            .newTenant()
            .withTenantId(tenantId)
            .withName("Tenant 1")
            .create()
            .getValue()
            .getTenantKey();

    // when add user entity to tenant
    final var updatedTenant =
        engine
            .tenant()
            .addEntity(tenantId)
            .withEntityId(username)
            .withEntityType(entityType)
            .add()
            .getValue();

    // then assert that the entity was added correctly
    Assertions.assertThat(updatedTenant)
        .describedAs(
            "Entity of type %s with ID %s should be correctly added to tenant with ID %s",
            entityType, username, tenantKey)
        .isNotNull()
        .hasFieldOrPropertyWithValue("entityId", username)
        .hasFieldOrPropertyWithValue("tenantKey", tenantKey)
        .hasFieldOrPropertyWithValue("tenantId", tenantId)
        .hasFieldOrPropertyWithValue("entityType", entityType);
  }

  @Test
  public void shouldAddGroupToTenant() {
    // given
    final var entityType = GROUP;
    final var groupId = Strings.newRandomValidIdentityId();
    createGroup(groupId);
    final var tenantId = UUID.randomUUID().toString();
    engine.tenant().newTenant().withTenantId(tenantId).withName("Tenant 1").create().getValue();

    // when add user entity to tenant
    final var updatedTenant =
        engine
            .tenant()
            .addEntity(tenantId)
            .withEntityId(groupId)
            .withEntityType(entityType)
            .add()
            .getValue();

    // then assert that the entity was added correctly
    assertThat(updatedTenant)
        .describedAs(
            "Entity of type %s with ID %s should be correctly added to tenant with ID %s",
            entityType, groupId, tenantId)
        .isNotNull()
        .hasTenantId(tenantId)
        .hasEntityType(entityType)
        .hasEntityId(groupId);
  }

  @Test
  public void shouldAddNonExistingGroupToTenantIfGroupsClaimEnabled() {
    // given
    final var entityType = GROUP;
    final var groupId = Strings.newRandomValidIdentityId();
    final var tenantId = UUID.randomUUID().toString();
    engine.tenant().newTenant().withTenantId(tenantId).withName("Tenant 1").create().getValue();

    // when add user entity to tenant
    final var updatedTenant =
        engine
            .tenant()
            .addEntity(tenantId)
            .withEntityId(groupId)
            .withEntityType(entityType)
            .add(
                AuthorizationUtil.getAuthInfoWithClaim(
                    Authorization.USER_GROUPS_CLAIMS, List.of("g1")))
            .getValue();

    // then assert that the entity was added correctly
    assertThat(updatedTenant)
        .describedAs(
            "Entity of type %s with ID %s should be correctly added to tenant with ID %s",
            entityType, groupId, tenantId)
        .isNotNull()
        .hasTenantId(tenantId)
        .hasEntityType(entityType)
        .hasEntityId(groupId);
  }

  @Test
  public void shouldAddNonExistingGroupToTenantIfGroupsClaimDisabled() {
    // given
    final var entityType = GROUP;
    final var groupId = Strings.newRandomValidIdentityId();
    final var tenantId = UUID.randomUUID().toString();
    engine.tenant().newTenant().withTenantId(tenantId).withName("Tenant 1").create().getValue();

    // when add user entity to tenant
    final var updatedTenant =
        engine
            .tenant()
            .addEntity(tenantId)
            .withEntityId(groupId)
            .withEntityType(entityType)
            .add()
            .getValue();

    // then assert that the entity was added correctly
    assertThat(updatedTenant)
        .describedAs(
            "Entity of type %s with ID %s should be correctly added to tenant with ID %s",
            entityType, groupId, tenantId)
        .isNotNull()
        .hasTenantId(tenantId)
        .hasEntityType(entityType)
        .hasEntityId(groupId);
  }

  @Test
  public void shouldAddRoleToTenant() {
    // given
    final var entityType = ROLE;
    final var roleId = Strings.newRandomValidIdentityId();
    createRole(roleId);
    final var tenantId = UUID.randomUUID().toString();
    engine.tenant().newTenant().withTenantId(tenantId).withName("Tenant 1").create().getValue();

    // when
    final var updatedTenant =
        engine
            .tenant()
            .addEntity(tenantId)
            .withEntityId(roleId)
            .withEntityType(entityType)
            .add()
            .getValue();

    // then
    assertThat(updatedTenant)
        .describedAs(
            "Entity of type %s with ID %s should be correctly added to tenant with ID %s",
            entityType, roleId, tenantId)
        .isNotNull()
        .hasTenantId(tenantId)
        .hasEntityType(entityType)
        .hasEntityId(roleId);
  }

  @Test
  public void shouldRejectAddingRoleToTenantIfRoleDoesNotExist() {
    // given
    final var roleId = Strings.newRandomValidIdentityId();
    final var tenantId = UUID.randomUUID().toString();
    engine.tenant().newTenant().withTenantId(tenantId).withName("Tenant 1").create().getValue();

    // when
    final var rejection =
        engine
            .tenant()
            .addEntity(tenantId)
            .withEntityId(roleId)
            .withEntityType(ROLE)
            .expectRejection()
            .add();

    // then
    assertThat(rejection)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to add role with ID '%s' to tenant with ID '%s', but the role doesn't exist."
                .formatted(roleId, tenantId));
  }

  @Test
  public void shouldRejectAddingRoleToTenantIfRoleAlreadyAssigned() {
    // given
    final var roleId = Strings.newRandomValidIdentityId();
    createRole(roleId);
    final var tenantId = UUID.randomUUID().toString();
    engine.tenant().newTenant().withTenantId(tenantId).withName("Tenant 1").create().getValue();
    engine.tenant().addEntity(tenantId).withEntityId(roleId).withEntityType(ROLE).add();

    // when
    final var rejection =
        engine
            .tenant()
            .addEntity(tenantId)
            .withEntityId(roleId)
            .withEntityType(ROLE)
            .expectRejection()
            .add();

    // then
    assertThat(rejection)
        .hasRejectionType(RejectionType.ALREADY_EXISTS)
        .hasRejectionReason(
            "Expected to add role with ID '%s' to tenant with ID '%s', but the role is already assigned to the tenant."
                .formatted(roleId, tenantId));
  }

  @Test
  public void shouldRejectIfTenantIsNotPresentWhileAddingEntity() {
    // when try adding entity to a non-existent tenant
    final var nonExistingTenantId = UUID.randomUUID().toString();
    final var notPresentUpdateRecord =
        engine.tenant().addEntity(nonExistingTenantId).expectRejection().add();
    // then assert that the rejection is for tenant not found
    assertThat(notPresentUpdateRecord)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to add entity to tenant with ID '%s', but no tenant with this ID exists."
                .formatted(nonExistingTenantId));
  }

  @Test
  public void shouldRejectIfEntityIsAlreadyAssignedToTenant() {
    // given
    final var user = createUser();
    final var tenantId = UUID.randomUUID().toString();
    engine.tenant().newTenant().withTenantId(tenantId).withName("Tenant 1").create();
    final var username = user.getUsername();
    engine.tenant().addEntity(tenantId).withEntityId(username).withEntityType(USER).add();

    // when try adding a non-existent entity to the tenant
    final var alreadyAssignedRecord =
        engine
            .tenant()
            .addEntity(tenantId)
            .withEntityId(username)
            .withEntityType(USER)
            .expectRejection()
            .add();

    // then assert that the rejection is for entity not found
    assertThat(alreadyAssignedRecord)
        .hasRejectionType(RejectionType.ALREADY_EXISTS)
        .hasRejectionReason(
            "Expected to add user with ID '%s' to tenant with ID '%s', but the user is already assigned to the tenant."
                .formatted(username, tenantId));
  }

  private UserRecordValue createUser() {
    return engine
        .user()
        .newUser(UUID.randomUUID().toString())
        .withName("Foo Bar")
        .withEmail("foo@bar.com")
        .withPassword("password")
        .create()
        .getValue();
  }

  private long createGroup(final String groupId) {
    return engine.group().newGroup(groupId).withName("groupName").create().getValue().getGroupKey();
  }

  private void createRole(final String roleId) {
    engine.role().newRole(roleId).create();
  }

  private String createMapping() {
    return engine
        .mappingRule()
        .newMappingRule(Strings.newRandomValidIdentityId())
        .withClaimName("claimName")
        .withClaimValue("claimValue")
        .withName("name")
        .create()
        .getValue()
        .getMappingRuleId();
  }
}
