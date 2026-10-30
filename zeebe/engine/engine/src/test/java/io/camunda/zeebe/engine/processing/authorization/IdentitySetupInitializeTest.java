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
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRuleRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.intent.IdentitySetupIntent;
import io.camunda.zeebe.protocol.record.intent.MappingRuleIntent;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.MappingRuleRecordValue;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Set;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class IdentitySetupInitializeTest {

  @Rule public final EngineRule engine = EngineRule.singlePartition();
  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldCreateRoleUserAndTenant() {
    // given
    final var roleId = "roleId";
    final var role = new RoleRecord().setRoleId(roleId);
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
    engine
        .identitySetup()
        .initialize()
        .withRole(role)
        .withUser(user)
        .withTenant(tenant)
        .withRoleMember(
            new RoleRecord().setRoleId(roleId).setEntityType(EntityType.USER).setEntityId(username))
        .withAuthorization(
            new AuthorizationRecord()
                .setResourceType(AuthorizationResourceType.TENANT)
                .setResourceMatcher(AuthorizationResourceMatcher.ID)
                .setResourceId(tenantId)
                .setPermissionTypes(Set.of(PermissionType.READ))
                .setOwnerType(AuthorizationOwnerType.ROLE)
                .setOwnerId(roleId))
        .initialize();

    // then
    Assertions.assertThat(
            RecordingExporter.roleRecords(RoleIntent.CREATED).withRoleId(roleId).exists())
        .isTrue();
    assertThat(
            RecordingExporter.userRecords(UserIntent.CREATED)
                .withUsername(username)
                .getFirst()
                .getValue())
        .hasUsername(username)
        .hasName(username)
        .hasPassword(password)
        .hasEmail(mail);
    assertThat(
            RecordingExporter.tenantRecords(TenantIntent.CREATED)
                .withTenantId(tenantId)
                .getFirst()
                .getValue())
        .hasName(tenantName)
        .hasTenantId(tenantId);
    assertThatEntityIsAssignedToRole(roleId, username, EntityType.USER);
    final var authorizations =
        RecordingExporter.authorizationRecords(AuthorizationIntent.CREATED)
            .withOwnerId(roleId)
            // exclude UNSPECIFIED resource type
            .limit(1)
            .map(Record::getValue)
            .toList();
    Assertions.assertThat(authorizations)
        .singleElement()
        .satisfies(
            record ->
                assertThat(record)
                    .hasResourceType(AuthorizationResourceType.TENANT)
                    .hasResourceId(tenantId)
                    .hasPermissionTypes(Set.of(PermissionType.READ))
                    .hasOwnerId(roleId)
                    .hasOwnerType(AuthorizationOwnerType.ROLE));
  }

  @Test
  public void shouldNotCreateUserIfAlreadyExists() {
    // given
    final var roleId = "roleId";
    final var role = new RoleRecord().setRoleId(roleId);
    final var username = "username";
    final var password = "password";
    final var mail = "e@mail.com";
    final var user =
        new UserRecord()
            .setUsername(username)
            .setName(username)
            .setPassword(password)
            .setEmail(mail);
    engine
        .user()
        .newUser(username)
        .withName(username)
        .withPassword(password)
        .withEmail(mail)
        .create()
        .getKey();

    // when
    engine
        .identitySetup()
        .initialize()
        .withRole(role)
        .withUser(user)
        .withRoleMember(
            new RoleRecord().setRoleId(roleId).setEntityType(EntityType.USER).setEntityId(username))
        .initialize();

    // then
    assertUserIsNotCreated(username);
    assertThatEntityIsAssignedToRole(roleId, username, EntityType.USER);
  }

  @Test
  public void shouldNotCreateRoleIfAlreadyExists() {
    // given
    final var roleId = "roleId";
    final var role = new RoleRecord().setRoleId(roleId);
    final var username = "username";
    final var password = "password";
    final var mail = "e@mail.com";
    final var user =
        new UserRecord()
            .setUsername(username)
            .setName(username)
            .setPassword(password)
            .setEmail(mail);
    engine.role().newRole(roleId).create().getKey();

    // when
    engine
        .identitySetup()
        .initialize()
        .withRole(role)
        .withUser(user)
        .withRoleMember(
            new RoleRecord().setRoleId(roleId).setEntityType(EntityType.USER).setEntityId(username))
        .initialize();

    // then
    assertRoleIsNotCreated(roleId);
    assertThatEntityIsAssignedToRole(roleId, username, EntityType.USER);
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
    engine
        .identitySetup()
        .initialize()
        .withUser(new UserRecord().setUsername(Strings.newRandomValidUsername()))
        .withRole(new RoleRecord().setRoleId(Strings.newRandomValidIdentityId()))
        .withTenant(tenant)
        .initialize();

    // then
    assertTenantIsNotCreated(tenantId);
  }

  @Test
  public void shouldAssignUserToRoleIfBothAlreadyExist() {
    // given
    final var roleId = "roleId";
    final var role = new RoleRecord().setRoleId(roleId);
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
    engine.role().newRole(roleId).create().getKey();
    engine
        .user()
        .newUser(username)
        .withName(username)
        .withPassword(password)
        .withEmail(mail)
        .create()
        .getKey();

    // when
    engine
        .identitySetup()
        .initialize()
        .withRole(role)
        .withUser(user)
        .withRoleMember(
            new RoleRecord()
                .setRoleId(role.getRoleId())
                .setEntityType(EntityType.USER)
                .setEntityId(username))
        .initialize();

    // then
    assertRoleIsNotCreated(roleId);
    assertUserIsNotCreated(username);
    assertThatEntityIsAssignedToRole(roleId, username, EntityType.USER);
  }

  @Test
  public void shouldNotAssignUserToRoleIfAlreadyAssigned() {
    // given
    final var roleId = "roleId";
    final var role = new RoleRecord().setRoleKey(1).setRoleId(roleId);
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
    engine.role().newRole(roleId).create().getKey();
    engine
        .user()
        .newUser(username)
        .withName(username)
        .withPassword(password)
        .withEmail(mail)
        .create()
        .getKey();
    engine.role().addEntity(roleId).withEntityId(username).withEntityType(EntityType.USER).add();

    // when
    final var initializeRecord =
        engine
            .identitySetup()
            .initialize()
            .withRole(role)
            .withUser(user)
            .withRoleMember(
                new RoleRecord()
                    .setRoleId(roleId)
                    .setEntityType(EntityType.USER)
                    .setEntityId(username))
            .initialize();

    // then
    assertRoleIsNotCreated(roleId);
    assertUserIsNotCreated(username);
    assertNoAssignmentIsCreated(roleId, username);
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
    final var role = new RoleRecord().setRoleId(UUID.randomUUID().toString());
    final var mapping1 =
        new MappingRuleRecord()
            .setMappingRuleId(UUID.randomUUID().toString())
            .setName(UUID.randomUUID().toString())
            .setClaimName(UUID.randomUUID().toString())
            .setClaimValue(UUID.randomUUID().toString());
    final var mapping2 =
        new MappingRuleRecord()
            .setMappingRuleId(UUID.randomUUID().toString())
            .setName(UUID.randomUUID().toString())
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
            .withRoleMember(
                new RoleRecord()
                    .setRoleId(role.getRoleId())
                    .setEntityType(EntityType.MAPPING_RULE)
                    .setEntityId(mapping1.getMappingRuleId()))
            .withRoleMember(
                new RoleRecord()
                    .setRoleId(role.getRoleId())
                    .setEntityType(EntityType.MAPPING_RULE)
                    .setEntityId(mapping2.getMappingRuleId()))
            .initialize()
            .getValue();

    // then
    Assertions.assertThat(
            RecordingExporter.roleRecords(RoleIntent.CREATED).withRoleId(role.getRoleId()).exists())
        .isTrue();
    final var createdMappings =
        RecordingExporter.mappingRuleRecords(MappingRuleIntent.CREATED).limit(2).toList().stream()
            .map(Record::getValue)
            .toList();
    Assertions.assertThat(createdMappings)
        .extracting(MappingRuleRecordValue::getClaimName, MappingRuleRecordValue::getClaimValue)
        .containsExactly(
            tuple(mapping1.getClaimName(), mapping1.getClaimValue()),
            tuple(mapping2.getClaimName(), mapping2.getClaimValue()));
    Assertions.assertThat(createdMappings)
        .extracting(MappingRuleRecordValue::getMappingRuleId)
        .containsExactly(mapping1.getMappingRuleId(), mapping2.getMappingRuleId());
    Assertions.assertThat(createdMappings)
        .satisfiesExactly(
            m1 ->
                assertThatEntityIsAssignedToRole(
                    role.getRoleId(), m1.getMappingRuleId(), EntityType.MAPPING_RULE),
            m2 ->
                assertThatEntityIsAssignedToRole(
                    role.getRoleId(), m2.getMappingRuleId(), EntityType.MAPPING_RULE));
  }

  private static void assertUserIsNotCreated(final String username) {
    assertThat(
            RecordingExporter.userRecords(UserIntent.CREATE)
                .withUsername(username)
                .onlyCommandRejections()
                .limit(1)
                .getFirst())
        .hasRejectionType(RejectionType.ALREADY_EXISTS)
        .hasRejectionReason(
            "Expected to create user with username '%s', but a user with this username already exists"
                .formatted(username));
  }

  private static void assertRoleIsNotCreated(final String roleId) {
    assertThat(
            RecordingExporter.roleRecords(RoleIntent.CREATE)
                .withRoleId(roleId)
                .onlyCommandRejections()
                .limit(1)
                .getFirst())
        .hasRejectionType(RejectionType.ALREADY_EXISTS)
        .hasRejectionReason(
            "Expected to create role with ID '%s', but a role with this ID already exists"
                .formatted(roleId));
  }

  private static void assertTenantIsNotCreated(final String tenantId) {
    assertThat(
            RecordingExporter.tenantRecords(TenantIntent.CREATE)
                .withTenantId(tenantId)
                .onlyCommandRejections()
                .limit(1)
                .getFirst())
        .hasRejectionType(RejectionType.ALREADY_EXISTS)
        .hasRejectionReason(
            "Expected to create tenant with ID '%s', but a tenant with this ID already exists"
                .formatted(tenantId));
  }

  private static void assertNoAssignmentIsCreated(final String roleId, final String entityId) {
    assertThat(
            RecordingExporter.roleRecords(RoleIntent.ADD_ENTITY)
                .withRoleId(roleId)
                .withEntityId(entityId)
                .onlyCommandRejections()
                .limit(1)
                .getFirst())
        .hasRejectionType(RejectionType.ALREADY_EXISTS)
        .hasRejectionReason(
            "Expected to add entity with ID '%s' to role with ID '%s', but the entity is already assigned to this role."
                .formatted(entityId, roleId));
  }

  private void assertThatEntityIsAssignedToRole(
      final String roleId, final String entityId, final EntityType entityType) {
    final var roleRecord =
        RecordingExporter.roleRecords(RoleIntent.ENTITY_ADDED)
            .withRoleId(roleId)
            .withEntityId(entityId)
            .getFirst();
    assertThat(roleRecord.getValue())
        .hasRoleId(roleId)
        .hasEntityId(entityId)
        .hasEntityType(entityType);
  }
}
