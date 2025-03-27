/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.authorization;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.intent.IdentitySetupIntent;
import io.camunda.zeebe.protocol.record.intent.MappingIntent;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationRecordValue;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.MappingRecordValue;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class IdentitySetupInitializeTest {

  @Rule public final EngineRule engine = EngineRule.singlePartition();
  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldCreateRoleUserAndTenant() {
    // given
    final var roleName = "roleName";
    final var role = new RoleRecord().setName(roleName);
    final var username = "username";
    final var password = "password";
    final var mail = "e@mail.com";
    final var user =
        new UserRecord()
            .setUsername(username)
            .setName(username)
            .setPassword(password)
            .setEmail(mail);
    final var tenantId = "tenant-id";
    final var tenantName = "tenant-name";
    final var tenant = new TenantRecord().setName(tenantName).setTenantId(tenantId);

    // when
    final var initialized =
        engine
            .identitySetup()
            .initialize()
            .withRole(role)
            .withUser(user)
            .withTenant(tenant)
            .initialize();
    final var userKey = initialized.getValue().getUsers().getFirst().getUserKey();
    final var roleKey = initialized.getValue().getDefaultRole().getRoleKey();
    final var tenantKey = initialized.getValue().getDefaultTenant().getTenantKey();

    // then
    assertThat(
            RecordingExporter.roleRecords(RoleIntent.CREATED)
                .withRecordKey(roleKey)
                .getFirst()
                .getValue())
        .hasRoleKey(roleKey)
        .hasName(roleName);
    assertThat(
            RecordingExporter.userRecords(UserIntent.CREATED)
                .withRecordKey(userKey)
                .getFirst()
                .getValue())
        .hasUserKey(userKey)
        .hasUsername(username)
        .hasName(username)
        .hasPassword(password)
        .hasEmail(mail);
    assertThat(
            RecordingExporter.tenantRecords(TenantIntent.CREATED)
                .withTenantKey(tenantKey)
                .getFirst()
                .getValue())
        .hasTenantKey(tenantKey)
        .hasName(tenantName)
        .hasTenantId(tenantId);
    assertThatEntityIsAssignedToRole(roleKey, userKey, EntityType.USER);
    assertThatAllPermissionsAreAddedToRole(roleKey);
  }

  @Test
  public void shouldNotCreateUserIfAlreadyExists() {
    // given
    final var roleName = "roleName";
    final var role = new RoleRecord().setName(roleName);
    final var username = "username";
    final var password = "password";
    final var mail = "e@mail.com";
    final var user =
        new UserRecord()
            .setUsername(username)
            .setName(username)
            .setPassword(password)
            .setEmail(mail);
    final var userKey =
        engine
            .user()
            .newUser(username)
            .withName(username)
            .withPassword(password)
            .withEmail(mail)
            .create()
            .getKey();

    // when
    final var initializeRecord =
        engine.identitySetup().initialize().withRole(role).withUser(user).initialize();

    // then
    assertUserIsNotCreated(initializeRecord.getSourceRecordPosition());
    assertThatEntityIsAssignedToRole(
        initializeRecord.getValue().getDefaultRole().getRoleKey(), userKey, EntityType.USER);
  }

  @Test
  @Ignore("Re-enable in https://github.com/camunda/camunda/issues/30109")
  public void shouldNotCreateRoleIfAlreadyExists() {
    // given
    final var roleName = "roleName";
    final var role = new RoleRecord().setRoleKey(1).setName(roleName);
    final var username = "username";
    final var password = "password";
    final var mail = "e@mail.com";
    final var user =
        new UserRecord()
            .setUsername(username)
            .setName(username)
            .setPassword(password)
            .setEmail(mail);
    final var roleKey = engine.role().newRole(roleName).create().getKey();

    // when
    final var initializeRecord =
        engine.identitySetup().initialize().withRole(role).withUser(user).initialize();

    // then
    assertRoleIsNotCreated(initializeRecord.getSourceRecordPosition());
    assertThatEntityIsAssignedToRole(
        roleKey, initializeRecord.getValue().getUsers().getFirst().getUserKey(), EntityType.USER);
    Assertions.assertThat(
            RecordingExporter.records()
                .limit(r -> r.getIntent() == IdentitySetupIntent.INITIALIZED)
                .authorizationRecords()
                .asList())
        .describedAs("No permissions should be added. The role should not be modified.")
        .isEmpty();
  }

  @Test
  public void shouldNotCreateTenantIfAlreadyExists() {
    // given
    final var tenantId = "tenant-id";
    final var tenantName = "tenant-name";
    final var tenant = new TenantRecord().setTenantKey(1).setTenantId(tenantId).setName(tenantName);
    engine.tenant().newTenant().withTenantId(tenantId).withName(tenantName).create().getKey();

    // when
    final var initializeRecord =
        engine
            .identitySetup()
            .initialize()
            .withUser(new UserRecord().setUserKey(2))
            .withRole(new RoleRecord().setRoleKey(3))
            .withTenant(tenant)
            .initialize();

    // then
    assertTenantIsNotCreated(initializeRecord.getSourceRecordPosition());
  }

  @Test
  @Ignore("Re-enable in https://github.com/camunda/camunda/issues/30109")
  public void shouldAssignUserToRoleIfBothAlreadyExist() {
    // given
    final var roleName = "roleName";
    final var role = new RoleRecord().setRoleKey(1).setName(roleName);
    final var username = "username";
    final var password = "password";
    final var mail = "e@mail.com";
    final var user =
        new UserRecord()
            .setUserKey(2)
            .setUsername(username)
            .setName(username)
            .setPassword(password)
            .setEmail(mail);
    final var roleKey = engine.role().newRole(roleName).create().getKey();
    final var userKey =
        engine
            .user()
            .newUser(username)
            .withName(username)
            .withPassword(password)
            .withEmail(mail)
            .create()
            .getKey();

    // when
    final var initializeRecord =
        engine.identitySetup().initialize().withRole(role).withUser(user).initialize();

    // then
    assertRoleIsNotCreated(initializeRecord.getSourceRecordPosition());
    assertUserIsNotCreated(initializeRecord.getSourceRecordPosition());
    assertThatEntityIsAssignedToRole(roleKey, userKey, EntityType.USER);
  }

  @Test
  @Ignore("Re-enable in https://github.com/camunda/camunda/issues/30109")
  public void shouldNotAssignUserToRoleIfAlreadyAssigned() {
    // given
    final var roleName = "roleName";
    final var role = new RoleRecord().setRoleKey(1).setName(roleName);
    final var username = "username";
    final var password = "password";
    final var mail = "e@mail.com";
    final var user =
        new UserRecord()
            .setUserKey(2)
            .setUsername(username)
            .setName(username)
            .setPassword(password)
            .setEmail(mail);
    final var roleKey = engine.role().newRole(roleName).create().getKey();
    final var userKey =
        engine
            .user()
            .newUser(username)
            .withName(username)
            .withPassword(password)
            .withEmail(mail)
            .create()
            .getKey();
    engine.role().addEntity(roleKey).withEntityKey(userKey).withEntityType(EntityType.USER).add();

    // when
    final var initializeRecord =
        engine.identitySetup().initialize().withRole(role).withUser(user).initialize();

    // then
    assertRoleIsNotCreated(initializeRecord.getSourceRecordPosition());
    assertUserIsNotCreated(initializeRecord.getSourceRecordPosition());
    assertNoAssignmentIsCreated(initializeRecord.getSourceRecordPosition());
  }

  @Test
  public void shouldCreateMultipleUsers() {
    // given
    final var user1 =
        new UserRecord()
            .setUsername(UUID.randomUUID().toString())
            .setName(UUID.randomUUID().toString())
            .setPassword(UUID.randomUUID().toString())
            .setEmail(UUID.randomUUID().toString());
    final var user2 =
        new UserRecord()
            .setUsername(UUID.randomUUID().toString())
            .setName(UUID.randomUUID().toString())
            .setPassword(UUID.randomUUID().toString())
            .setEmail(UUID.randomUUID().toString());

    // when
    engine.identitySetup().initialize().withUser(user1).withUser(user2).initialize();

    // then
    Assertions.assertThat(RecordingExporter.userRecords(UserIntent.CREATED).limit(2))
        .extracting(Record::getValue)
        .extracting(
            UserRecordValue::getUsername,
            UserRecordValue::getPassword,
            UserRecordValue::getName,
            UserRecordValue::getEmail)
        .containsExactly(
            tuple(user1.getUsername(), user1.getPassword(), user1.getName(), user1.getEmail()),
            tuple(user2.getUsername(), user2.getPassword(), user2.getName(), user2.getEmail()));
  }

  @Test
  public void shouldCreateConfiguredMappings() {
    // given
    final var role = new RoleRecord().setName(UUID.randomUUID().toString());
    final var mapping1 =
        new MappingRecord()
            .setId(UUID.randomUUID().toString())
            .setClaimName(UUID.randomUUID().toString())
            .setClaimValue(UUID.randomUUID().toString());
    final var mapping2 =
        new MappingRecord()
            .setId(UUID.randomUUID().toString())
            .setClaimName(UUID.randomUUID().toString())
            .setClaimValue(UUID.randomUUID().toString());

    // when
    final var initialized =
        engine
            .identitySetup()
            .initialize()
            .withRole(role)
            .withMapping(mapping1)
            .withMapping(mapping2)
            .initialize()
            .getValue();

    // then
    Assertions.assertThat(RecordingExporter.roleRecords(RoleIntent.CREATED).exists()).isTrue();
    final var createdMappings =
        RecordingExporter.mappingRecords(MappingIntent.CREATED).limit(2).toList().stream()
            .map(Record::getValue)
            .toList();
    Assertions.assertThat(createdMappings)
        .extracting(MappingRecordValue::getClaimName, MappingRecordValue::getClaimValue)
        .containsExactly(
            tuple(mapping1.getClaimName(), mapping1.getClaimValue()),
            tuple(mapping2.getClaimName(), mapping2.getClaimValue()));
    Assertions.assertThat(createdMappings)
        .extracting(MappingRecordValue::getId)
        .containsExactly(mapping1.getId(), mapping2.getId());
    Assertions.assertThat(createdMappings)
        .satisfiesExactly(
            m1 ->
                assertThatEntityIsAssignedToRole(
                    initialized.getDefaultRole().getRoleKey(),
                    m1.getMappingKey(),
                    EntityType.MAPPING),
            m2 ->
                assertThatEntityIsAssignedToRole(
                    initialized.getDefaultRole().getRoleKey(),
                    m2.getMappingKey(),
                    EntityType.MAPPING));
  }

  private static void assertThatAllPermissionsAreAddedToRole(final long roleKey) {
    final var expectedResourceTypes =
        Arrays.stream(AuthorizationResourceType.values())
            .filter(resourceType -> resourceType != AuthorizationResourceType.UNSPECIFIED)
            .toArray(AuthorizationResourceType[]::new);
    final var addedPermissions =
        RecordingExporter.authorizationRecords(AuthorizationIntent.CREATED)
            .withOwnerId(String.valueOf(roleKey))
            // exclude UNSPECIFIED resource type
            .limit(AuthorizationResourceType.values().length - 1)
            .map(Record::getValue)
            .toList();

    Assertions.assertThat(addedPermissions)
        .describedAs("Added permissions for all resource types except UNSPECIFIED")
        .extracting(AuthorizationRecordValue::getResourceType)
        .containsExactly(expectedResourceTypes);

    final Map<AuthorizationResourceType, Set<PermissionType>> expectedPermissions = new HashMap<>();
    for (final AuthorizationResourceType resourceType : expectedResourceTypes) {
      final var permissionTypes = new HashSet<>(resourceType.getSupportedPermissionTypes());
      expectedPermissions.put(resourceType, permissionTypes);
    }

    for (final var resourceType : expectedPermissions.keySet()) {
      Assertions.assertThat(addedPermissions)
          .filteredOn(record -> record.getResourceType() == resourceType)
          .describedAs("Added supported permission types for resource type %s", resourceType)
          .flatMap(AuthorizationRecordValue::getPermissionTypes)
          .containsOnly(expectedPermissions.get(resourceType).toArray(new PermissionType[0]));
    }
  }

  private static void assertUserIsNotCreated(final long initializePosition) {
    Assertions.assertThat(
            RecordingExporter.records()
                .after(initializePosition)
                .limit(r -> r.getIntent() == IdentitySetupIntent.INITIALIZED)
                .userRecords()
                .withIntent(UserIntent.CREATED)
                .toList())
        .isEmpty();
  }

  private static void assertRoleIsNotCreated(final long initializePosition) {
    Assertions.assertThat(
            RecordingExporter.records()
                .after(initializePosition)
                .limit(r -> r.getIntent() == IdentitySetupIntent.INITIALIZED)
                .roleRecords()
                .withIntent(RoleIntent.CREATED)
                .toList())
        .isEmpty();
  }

  private static void assertTenantIsNotCreated(final long initializePosition) {
    Assertions.assertThat(
            RecordingExporter.records()
                .after(initializePosition)
                .limit(r -> r.getIntent() == IdentitySetupIntent.INITIALIZED)
                .withIntent(TenantIntent.CREATED))
        .isEmpty();
  }

  private static void assertNoAssignmentIsCreated(final long initializePosition) {
    Assertions.assertThat(
            RecordingExporter.records()
                .after(initializePosition)
                .limit(r -> r.getIntent() == IdentitySetupIntent.INITIALIZED)
                .roleRecords()
                .withIntent(RoleIntent.ENTITY_ADDED)
                .toList())
        .isEmpty();
  }

  private void assertThatEntityIsAssignedToRole(
      final long roleKey, final long entityKey, final EntityType entityType) {
    final var roleRecord =
        RecordingExporter.roleRecords(RoleIntent.ENTITY_ADDED).withEntityKey(entityKey).getFirst();
    Assertions.assertThat(roleRecord.getKey()).isEqualTo(roleKey);
    assertThat(roleRecord.getValue())
        .hasRoleKey(roleKey)
        .hasEntityKey(entityKey)
        .hasEntityType(entityType);
  }
}
