/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.tenant;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;

import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.engine.util.AuthorizationUtil;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class RemoveEntityTenantTest {

  @Rule public final EngineRule engine = EngineRule.singlePartition();
  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldRemoveUserFromTenant() {
    final var tenantId = UUID.randomUUID().toString();
    engine
        .tenant()
        .newTenant()
        .withTenantId(tenantId)
        .withName("name")
        .create()
        .getValue()
        .getTenantKey();
    final var username = "foo";
    engine
        .user()
        .newUser(username)
        .withEmail("foo@bar")
        .withName("Foo Bar")
        .withPassword("zabraboof")
        .create();
    engine
        .tenant()
        .addEntity(tenantId)
        .withEntityId(username)
        .withEntityType(EntityType.USER)
        .add();
    final var removedEntity =
        engine
            .tenant()
            .removeEntity(tenantId)
            .withEntityId(username)
            .withEntityType(EntityType.USER)
            .remove()
            .getValue();

    assertThat(removedEntity)
        .isNotNull()
        .hasTenantId(tenantId)
        .hasEntityId(username)
        .hasEntityType(EntityType.USER);
  }

  @Test
  public void shouldRemoveNonExistingUserFromTenant() {
    final var tenantId = UUID.randomUUID().toString();
    engine
        .tenant()
        .newTenant()
        .withTenantId(tenantId)
        .withName("name")
        .create()
        .getValue()
        .getTenantKey();
    final var username = "foo";
    engine
        .tenant()
        .addEntity(tenantId)
        .withEntityId(username)
        .withEntityType(EntityType.USER)
        .add();
    final var removedEntity =
        engine
            .tenant()
            .removeEntity(tenantId)
            .withEntityId(username)
            .withEntityType(EntityType.USER)
            .remove()
            .getValue();

    assertThat(removedEntity)
        .isNotNull()
        .hasTenantId(tenantId)
        .hasEntityId(username)
        .hasEntityType(EntityType.USER);
  }

  @Test
  public void shouldRejectIfTenantIsNotPresentEntityRemoval() {
    final var notPresentTenantId = UUID.randomUUID().toString();
    final var notPresentUpdateRecord =
        engine.tenant().removeEntity(notPresentTenantId).expectRejection().remove();

    assertThat(notPresentUpdateRecord)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to remove entity from tenant '"
                + notPresentTenantId
                + "', but no tenant with this ID exists.");
  }

  @Test
  public void shouldRejectIfEntityIsNotPresentEntityRemoval() {
    // given
    final var tenantId = UUID.randomUUID().toString();
    final var username = UUID.randomUUID().toString();
    final var tenantRecord = engine.tenant().newTenant().withTenantId(tenantId).create();
    engine
        .user()
        .newUser(username)
        .withEmail("foo@bar")
        .withName("Foo Bar")
        .withPassword("zabraboof")
        .create();

    // when
    final var createdTenant = tenantRecord.getValue();
    final var notPresentUpdateRecord =
        engine
            .tenant()
            .removeEntity(tenantId)
            .withEntityId(username)
            .withEntityType(EntityType.USER)
            .expectRejection()
            .remove();

    assertThat(createdTenant).isNotNull().hasTenantId(tenantId);

    assertThat(notPresentUpdateRecord)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to remove user with ID '%s' from tenant with ID '%s', but the user is not assigned to this tenant."
                .formatted(username, tenantId));
  }

  @Test
  public void shouldRejectIfEntityIsNotAssigned() {
    // given
    final var tenantId = UUID.randomUUID().toString();
    engine.tenant().newTenant().withTenantId(tenantId).create();

    // when
    final var username = "foo";
    engine
        .user()
        .newUser(username)
        .withEmail("foo@bar")
        .withName("Foo Bar")
        .withPassword("zabraboof")
        .create();
    final var notAssignedUpdateRecord =
        engine
            .tenant()
            .removeEntity(tenantId)
            .withEntityId(username)
            .withEntityType(EntityType.USER)
            .expectRejection()
            .remove();

    assertThat(notAssignedUpdateRecord)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to remove user with ID '%s' from tenant with ID '%s', but the user is not assigned to this tenant."
                .formatted(username, tenantId));
  }

  @Test
  public void shouldRemoveGroupFromTenant() {
    final var tenantId = UUID.randomUUID().toString();
    engine
        .tenant()
        .newTenant()
        .withTenantId(tenantId)
        .withName("name")
        .create()
        .getValue()
        .getTenantKey();
    final var name = "foo";
    final var groupId = "123";
    engine.group().newGroup(groupId).withName(name).create();
    engine
        .tenant()
        .addEntity(tenantId)
        .withEntityId(groupId)
        .withEntityType(EntityType.GROUP)
        .add();
    final var removedEntity =
        engine
            .tenant()
            .removeEntity(tenantId)
            .withEntityId(groupId)
            .withEntityType(EntityType.GROUP)
            .remove()
            .getValue();

    assertThat(removedEntity)
        .isNotNull()
        .hasTenantId(tenantId)
        .hasEntityId(groupId)
        .hasEntityType(EntityType.GROUP);
  }

  @Test
  public void shouldRejectIfTenantIsNotPresentGroupRemoval() {
    final var notPresentTenantId = UUID.randomUUID().toString();
    final var notPresentUpdateRecord =
        engine.tenant().removeEntity(notPresentTenantId).expectRejection().remove();

    assertThat(notPresentUpdateRecord)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to remove entity from tenant '"
                + notPresentTenantId
                + "', but no tenant with this ID exists.");
  }

  @Test
  public void shouldRejectIfEntityIsNotPresentGroupRemoval() {
    // given
    final var tenantId = UUID.randomUUID().toString();
    final var name = UUID.randomUUID().toString();
    final var groupId = "123";
    final var tenantRecord = engine.tenant().newTenant().withTenantId(tenantId).create();
    engine.group().newGroup(groupId).withName(name).create();

    // when
    final var createdTenant = tenantRecord.getValue();
    final var notPresentUpdateRecord =
        engine
            .tenant()
            .removeEntity(tenantId)
            .withEntityId(groupId)
            .withEntityType(EntityType.GROUP)
            .expectRejection()
            .remove();

    assertThat(createdTenant).isNotNull().hasTenantId(tenantId);

    assertThat(notPresentUpdateRecord)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to remove group with ID '%s' from tenant with ID '%s', but the group is not assigned to this tenant."
                .formatted(groupId, tenantId));
  }

  @Test
  public void shouldRemoveNonExistingGroupIfGroupsClaimEnabled() {
    // given
    final var tenantId = UUID.randomUUID().toString();
    final var name = UUID.randomUUID().toString();
    final var groupId = "123";
    final var tenantRecord = engine.tenant().newTenant().withTenantId(tenantId).create();
    engine.group().newGroup(groupId).withName(name).create();

    // when
    final var createdTenant = tenantRecord.getValue();
    final var notPresentUpdateRecord =
        engine
            .tenant()
            .removeEntity(tenantId)
            .withEntityId(groupId)
            .withEntityType(EntityType.GROUP)
            .expectRejection()
            .remove(
                AuthorizationUtil.getAuthInfoWithClaim(
                    Authorization.USER_GROUPS_CLAIMS, List.of("g1")))
            .getValue();

    assertThat(createdTenant).isNotNull().hasTenantId(tenantId);

    assertThat(notPresentUpdateRecord)
        .isNotNull()
        .hasTenantId(tenantId)
        .hasEntityId(groupId)
        .hasEntityType(EntityType.GROUP);
  }

  @Test
  public void shouldRejectIfGroupIsNotAssigned() {
    // given
    final var tenantId = UUID.randomUUID().toString();
    engine.tenant().newTenant().withTenantId(tenantId).create();

    // when
    final var name = "foo";
    final var groupId = "123";
    engine.group().newGroup(groupId).withName(name).create();
    final var notAssignedUpdateRecord =
        engine
            .tenant()
            .removeEntity(tenantId)
            .withEntityId(groupId)
            .withEntityType(EntityType.GROUP)
            .expectRejection()
            .remove();

    assertThat(notAssignedUpdateRecord)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to remove group with ID '%s' from tenant with ID '%s', but the group is not assigned to this tenant."
                .formatted(groupId, tenantId));
  }

  @Test
  public void shouldRemoveMappingFromTenant() {
    final var tenantId = UUID.randomUUID().toString();
    engine.tenant().newTenant().withTenantId(tenantId).create().getValue().getTenantKey();
    final var mappingId = Strings.newRandomValidIdentityId();
    engine
        .mapping()
        .newMapping(mappingId)
        .withName("name")
        .withClaimName("cn")
        .withClaimValue("cv")
        .create();
    engine
        .tenant()
        .addEntity(tenantId)
        .withEntityId(mappingId)
        .withEntityType(EntityType.MAPPING_RULE)
        .add();
    final var removedEntity =
        engine
            .tenant()
            .removeEntity(tenantId)
            .withEntityId(mappingId)
            .withEntityType(EntityType.MAPPING_RULE)
            .remove()
            .getValue();

    assertThat(removedEntity)
        .isNotNull()
        .hasTenantId(tenantId)
        .hasEntityId(mappingId)
        .hasEntityType(EntityType.MAPPING_RULE);
  }

  @Test
  public void shouldRejectIfEntityIsNotPresentMappingRemoval() {
    // given
    final var tenantId = UUID.randomUUID().toString();
    final var mappingId = Strings.newRandomValidIdentityId();
    final var tenantRecord = engine.tenant().newTenant().withTenantId(tenantId).create();
    engine
        .mapping()
        .newMapping(mappingId)
        .withName("name")
        .withClaimName("cn")
        .withClaimValue("cv")
        .create();

    // when
    final var createdTenant = tenantRecord.getValue();
    final var notPresentUpdateRecord =
        engine
            .tenant()
            .removeEntity(tenantId)
            .withEntityId(mappingId)
            .withEntityType(EntityType.MAPPING_RULE)
            .expectRejection()
            .remove();

    assertThat(createdTenant).isNotNull().hasTenantId(tenantId);

    assertThat(notPresentUpdateRecord)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to remove mapping with ID '%s' from tenant with ID '%s', but the mapping is not assigned to this tenant."
                .formatted(mappingId, tenantId));
  }

  @Test
  public void shouldRejectIfMappingIsNotAssigned() {
    // given
    final var tenantId = UUID.randomUUID().toString();
    engine.tenant().newTenant().withTenantId(tenantId).create();

    // when
    final var mappingId = "123";
    engine
        .mapping()
        .newMapping(mappingId)
        .withName("name")
        .withClaimName("cn")
        .withClaimValue("cv")
        .create();
    final var notAssignedUpdateRecord =
        engine
            .tenant()
            .removeEntity(tenantId)
            .withEntityId(mappingId)
            .withEntityType(EntityType.MAPPING_RULE)
            .expectRejection()
            .remove();

    assertThat(notAssignedUpdateRecord)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to remove mapping with ID '%s' from tenant with ID '%s', but the mapping is not assigned to this tenant."
                .formatted(mappingId, tenantId));
  }

  @Test
  public void shouldRemoveRoleFromTenant() {
    final var tenantId = UUID.randomUUID().toString();
    engine.tenant().newTenant().withTenantId(tenantId).create().getValue().getTenantKey();
    final var roleId = Strings.newRandomValidIdentityId();
    engine.role().newRole(roleId).create();
    engine.tenant().addEntity(tenantId).withEntityId(roleId).withEntityType(EntityType.ROLE).add();
    final var removedEntity =
        engine
            .tenant()
            .removeEntity(tenantId)
            .withEntityId(roleId)
            .withEntityType(EntityType.ROLE)
            .remove()
            .getValue();

    assertThat(removedEntity)
        .isNotNull()
        .hasTenantId(tenantId)
        .hasEntityId(roleId)
        .hasEntityType(EntityType.ROLE);
  }

  @Test
  public void shouldRejectIfEntityIsNotPresentRoleRemoval() {
    // given
    final var tenantId = UUID.randomUUID().toString();
    final var roleId = Strings.newRandomValidIdentityId();
    final var tenantRecord = engine.tenant().newTenant().withTenantId(tenantId).create();
    engine.role().newRole(roleId).create();

    // when
    final var createdTenant = tenantRecord.getValue();
    final var notPresentUpdateRecord =
        engine
            .tenant()
            .removeEntity(tenantId)
            .withEntityId(roleId)
            .withEntityType(EntityType.ROLE)
            .expectRejection()
            .remove();

    assertThat(createdTenant).isNotNull().hasTenantId(tenantId);

    assertThat(notPresentUpdateRecord)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to remove role with ID '%s' from tenant with ID '%s', but the role is not assigned to this tenant."
                .formatted(roleId, tenantId));
  }

  @Test
  public void shouldRejectIfRoleIsNotAssigned() {
    // given
    final var tenantId = UUID.randomUUID().toString();
    engine.tenant().newTenant().withTenantId(tenantId).create();

    // when
    final var roleId = Strings.newRandomValidIdentityId();
    engine.role().newRole(roleId).create();
    final var notAssignedUpdateRecord =
        engine
            .tenant()
            .removeEntity(tenantId)
            .withEntityId(roleId)
            .withEntityType(EntityType.ROLE)
            .expectRejection()
            .remove();

    assertThat(notAssignedUpdateRecord)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to remove role with ID '%s' from tenant with ID '%s', but the role is not assigned to this tenant."
                .formatted(roleId, tenantId));
  }
}
