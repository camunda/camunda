/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.auditlog;

import static io.camunda.zeebe.protocol.record.value.AuthorizationScope.WILDCARD;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.configuration.ConfiguredUser;
import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.intent.MappingRuleIntent;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class IdentityEventsClaimsTest {

  private static final ConfiguredUser DEFAULT_USER =
      new ConfiguredUser(
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString());

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .withIdentitySetup()
          .withSecurityConfig(
              cfg -> {
                cfg.getAuthorizations().setEnabled(true);
                cfg.getInitialization().setUsers(List.of(DEFAULT_USER));
                cfg.getInitialization()
                    .getDefaultRoles()
                    .put("admin", Map.of("users", List.of(DEFAULT_USER.getUsername())));
              });

  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldIncludeClaimsInUserCreatedEvents() {
    // when
    final var user = createUser(DEFAULT_USER.getUsername());

    // then
    final var record =
        RecordingExporter.userRecords(UserIntent.CREATED)
            .withUserKey(user.getUserKey())
            .withUsername(user.getUsername())
            .findFirst();
    assertAuthorizationClaims(record);
  }

  @Test
  public void shouldIncludeClaimsInUserUpdatedEvents() {
    // given
    final var user = createUser(DEFAULT_USER.getUsername());

    // when
    engine
        .user()
        .updateUser()
        .withUsername(user.getUsername())
        .withName(UUID.randomUUID().toString())
        .update(DEFAULT_USER.getUsername());

    // then
    final var record =
        RecordingExporter.userRecords(UserIntent.UPDATED)
            .withUserKey(user.getUserKey())
            .findFirst();
    assertAuthorizationClaims(record);
  }

  @Test
  public void shouldIncludeClaimsInUserDeletedEvents() {
    // given
    final var user = createUser(DEFAULT_USER.getUsername());

    // when
    engine.user().deleteUser(user.getUsername()).delete(DEFAULT_USER.getUsername());

    // then
    final var record =
        RecordingExporter.userRecords(UserIntent.DELETED)
            .withUserKey(user.getUserKey())
            .findFirst();
    assertAuthorizationClaims(record);
  }

  @Test
  public void shouldIncludeClaimsInAuthorizationCreatedEvents() {
    // given
    final var user = createUser(DEFAULT_USER.getUsername());

    // when
    final var authorization =
        engine
            .authorization()
            .newAuthorization()
            .withPermissions(PermissionType.READ_PROCESS_INSTANCE)
            .withOwnerId(user.getUsername())
            .withOwnerType(AuthorizationOwnerType.USER)
            .withResourceType(AuthorizationResourceType.PROCESS_DEFINITION)
            .withResourceMatcher(WILDCARD.getMatcher())
            .withResourceId(WILDCARD.getResourceId())
            .create(DEFAULT_USER.getUsername())
            .getValue();

    // then
    final var record =
        RecordingExporter.authorizationRecords(AuthorizationIntent.CREATED)
            .withAuthorizationKey(authorization.getAuthorizationKey())
            .findFirst();
    assertAuthorizationClaims(record);
  }

  @Test
  public void shouldIncludeClaimsInAuthorizationUpdatedEvents() {
    // given
    final var user = createUser(DEFAULT_USER.getUsername());
    final var authorization =
        engine
            .authorization()
            .newAuthorization()
            .withPermissions(PermissionType.READ_PROCESS_INSTANCE)
            .withOwnerId(user.getUsername())
            .withOwnerType(AuthorizationOwnerType.USER)
            .withResourceType(AuthorizationResourceType.PROCESS_DEFINITION)
            .withResourceMatcher(AuthorizationResourceMatcher.ANY)
            .withResourceId(WILDCARD.getResourceId())
            .create(DEFAULT_USER.getUsername())
            .getValue();

    // when
    engine
        .authorization()
        .updateAuthorization(authorization.getAuthorizationKey())
        .withResourceMatcher(AuthorizationResourceMatcher.ANY)
        .withResourceId(WILDCARD.getResourceId())
        .withPermissions(PermissionType.CREATE_PROCESS_INSTANCE)
        .update(DEFAULT_USER.getUsername());

    // then
    final var record =
        RecordingExporter.authorizationRecords(AuthorizationIntent.UPDATED)
            .withAuthorizationKey(authorization.getAuthorizationKey())
            .findFirst();
    assertAuthorizationClaims(record);
  }

  @Test
  public void shouldIncludeClaimsInAuthorizationDeletedEvents() {
    // given
    final var user = createUser(DEFAULT_USER.getUsername());
    final var authorization =
        engine
            .authorization()
            .newAuthorization()
            .withPermissions(PermissionType.CREATE_PROCESS_INSTANCE)
            .withOwnerId(user.getUsername())
            .withOwnerType(AuthorizationOwnerType.USER)
            .withResourceType(AuthorizationResourceType.PROCESS_DEFINITION)
            .withResourceMatcher(WILDCARD.getMatcher())
            .withResourceId(WILDCARD.getResourceId())
            .create(DEFAULT_USER.getUsername())
            .getValue();

    // when
    engine
        .authorization()
        .deleteAuthorization(authorization.getAuthorizationKey())
        .delete(DEFAULT_USER.getUsername());

    // then
    final var record =
        RecordingExporter.authorizationRecords(AuthorizationIntent.DELETED)
            .withAuthorizationKey(authorization.getAuthorizationKey())
            .findFirst();
    assertAuthorizationClaims(record);
  }

  @Test
  public void shouldIncludeClaimsInGroupCreatedEvents() {
    // when
    final var group =
        engine
            .group()
            .newGroup(UUID.randomUUID().toString())
            .withName(UUID.randomUUID().toString())
            .create(DEFAULT_USER.getUsername())
            .getValue();

    // then
    final var record =
        RecordingExporter.groupRecords(GroupIntent.CREATED)
            .withGroupKey(group.getGroupKey())
            .findFirst();
    assertAuthorizationClaims(record);
  }

  @Test
  public void shouldIncludeClaimsInGroupUpdatedEvents() {
    // given
    final var group =
        engine
            .group()
            .newGroup(UUID.randomUUID().toString())
            .withName(UUID.randomUUID().toString())
            .create()
            .getValue();

    // when
    engine
        .group()
        .updateGroup(group.getGroupId())
        .withName(UUID.randomUUID().toString())
        .update(DEFAULT_USER.getUsername());

    // then
    final var record =
        RecordingExporter.groupRecords(GroupIntent.UPDATED)
            .withGroupKey(group.getGroupKey())
            .findFirst();
    assertAuthorizationClaims(record);
  }

  @Test
  public void shouldIncludeClaimsInGroupDeletedEvents() {
    // given
    final var group =
        engine
            .group()
            .newGroup(UUID.randomUUID().toString())
            .withName(UUID.randomUUID().toString())
            .create(DEFAULT_USER.getUsername())
            .getValue();

    // when
    engine.group().deleteGroup(group.getGroupId()).delete(DEFAULT_USER.getUsername());

    // then
    final var record =
        RecordingExporter.groupRecords(GroupIntent.DELETED)
            .withGroupKey(group.getGroupKey())
            .findFirst();
    assertAuthorizationClaims(record);
  }

  @Test
  public void shouldIncludeClaimsInMappingRuleCreatedEvents() {
    // when
    final var mappingRule =
        engine
            .mappingRule()
            .newMappingRule(UUID.randomUUID().toString())
            .withClaimName(UUID.randomUUID().toString())
            .withClaimValue(UUID.randomUUID().toString())
            .withName(UUID.randomUUID().toString())
            .create(DEFAULT_USER.getUsername())
            .getValue();

    // then
    final var record =
        RecordingExporter.mappingRuleRecords(MappingRuleIntent.CREATED)
            .withMappingRuleId(mappingRule.getMappingRuleId())
            .findFirst();
    assertAuthorizationClaims(record);
  }

  @Test
  public void shouldIncludeClaimsInMappingRuleUpdatedEvents() {
    // given
    final var mappingRule =
        engine
            .mappingRule()
            .newMappingRule(UUID.randomUUID().toString())
            .withClaimName(UUID.randomUUID().toString())
            .withClaimValue(UUID.randomUUID().toString())
            .withName(UUID.randomUUID().toString())
            .create(DEFAULT_USER.getUsername())
            .getValue();

    // when
    engine
        .mappingRule()
        .updateMappingRule(mappingRule.getMappingRuleId())
        .withClaimName(UUID.randomUUID().toString())
        .withClaimValue(UUID.randomUUID().toString())
        .withName(UUID.randomUUID().toString())
        .update(DEFAULT_USER.getUsername());

    // then
    final var record =
        RecordingExporter.mappingRuleRecords(MappingRuleIntent.UPDATED)
            .withMappingRuleId(mappingRule.getMappingRuleId())
            .findFirst();
    assertAuthorizationClaims(record);
  }

  @Test
  public void shouldIncludeClaimsInMappingRuleDeletedEvents() {
    // given
    final var mappingRule =
        engine
            .mappingRule()
            .newMappingRule(UUID.randomUUID().toString())
            .withClaimName(UUID.randomUUID().toString())
            .withClaimValue(UUID.randomUUID().toString())
            .withName(UUID.randomUUID().toString())
            .create(DEFAULT_USER.getUsername())
            .getValue();

    // when
    engine
        .mappingRule()
        .deleteMappingRule(mappingRule.getMappingRuleId())
        .delete(DEFAULT_USER.getUsername());

    // then
    final var record =
        RecordingExporter.mappingRuleRecords(MappingRuleIntent.DELETED)
            .withMappingRuleId(mappingRule.getMappingRuleId())
            .findFirst();
    assertAuthorizationClaims(record);
  }

  @Test
  public void shouldIncludeClaimsInRoleCreatedEvents() {
    // when
    final var role =
        engine
            .role()
            .newRole(UUID.randomUUID().toString())
            .withName(UUID.randomUUID().toString())
            .create(DEFAULT_USER.getUsername())
            .getValue();

    // then
    final var record =
        RecordingExporter.roleRecords(RoleIntent.CREATED)
            .withRoleKey(role.getRoleKey())
            .findFirst();
    assertAuthorizationClaims(record);
  }

  @Test
  public void shouldIncludeClaimsInRoleUpdatedEvents() {
    // given
    final var role =
        engine
            .role()
            .newRole(UUID.randomUUID().toString())
            .withName(UUID.randomUUID().toString())
            .create(DEFAULT_USER.getUsername())
            .getValue();

    // when
    engine
        .role()
        .updateRole(role.getRoleId())
        .withName(UUID.randomUUID().toString())
        .update(DEFAULT_USER.getUsername());

    // then
    final var record =
        RecordingExporter.roleRecords(RoleIntent.UPDATED)
            .withRoleKey(role.getRoleKey())
            .findFirst();
    assertAuthorizationClaims(record);
  }

  @Test
  public void shouldIncludeClaimsInRoleDeletedEvents() {
    // given
    final var role =
        engine
            .role()
            .newRole(UUID.randomUUID().toString())
            .withName(UUID.randomUUID().toString())
            .create(DEFAULT_USER.getUsername())
            .getValue();

    // when
    engine.role().deleteRole(role.getRoleId()).delete(DEFAULT_USER.getUsername());

    // then
    final var record =
        RecordingExporter.roleRecords(RoleIntent.DELETED)
            .withRoleKey(role.getRoleKey())
            .findFirst();
    assertAuthorizationClaims(record);
  }

  @Test
  public void shouldIncludeClaimsInTenantCreatedEvents() {
    // when
    final var tenant =
        engine
            .tenant()
            .newTenant()
            .withTenantId(UUID.randomUUID().toString())
            .withName(UUID.randomUUID().toString())
            .create(DEFAULT_USER.getUsername())
            .getValue();

    // then
    final var record =
        RecordingExporter.tenantRecords(TenantIntent.CREATED)
            .withTenantKey(tenant.getTenantKey())
            .findFirst();
    assertAuthorizationClaims(record);
  }

  @Test
  public void shouldIncludeClaimsInTenantUpdatedEvents() {
    // given
    final var tenant =
        engine
            .tenant()
            .newTenant()
            .withTenantId(UUID.randomUUID().toString())
            .withName(UUID.randomUUID().toString())
            .create(DEFAULT_USER.getUsername())
            .getValue();

    // when
    engine
        .tenant()
        .updateTenant(tenant.getTenantId())
        .withName(UUID.randomUUID().toString())
        .update(DEFAULT_USER.getUsername());

    // then
    final var record =
        RecordingExporter.tenantRecords(TenantIntent.UPDATED)
            .withTenantKey(tenant.getTenantKey())
            .findFirst();
    assertAuthorizationClaims(record);
  }

  @Test
  public void shouldIncludeClaimsInTenantDeletedEvents() {
    // given
    final var tenant =
        engine
            .tenant()
            .newTenant()
            .withTenantId(UUID.randomUUID().toString())
            .withName(UUID.randomUUID().toString())
            .create(DEFAULT_USER.getUsername())
            .getValue();

    // when
    engine.tenant().deleteTenant(tenant.getTenantId()).delete(DEFAULT_USER.getUsername());

    // then
    final var record =
        RecordingExporter.tenantRecords(TenantIntent.DELETED)
            .withTenantKey(tenant.getTenantKey())
            .findFirst();
    assertAuthorizationClaims(record);
  }

  @Test
  public void shouldIncludeClaimsInTenantEntityAddedEvents() {
    // given
    final var user = createUser(DEFAULT_USER.getUsername());
    final var tenant =
        engine
            .tenant()
            .newTenant()
            .withTenantId(UUID.randomUUID().toString())
            .withName(UUID.randomUUID().toString())
            .create(DEFAULT_USER.getUsername())
            .getValue();

    // when
    engine
        .tenant()
        .addEntity(tenant.getTenantId())
        .withEntityId(user.getUsername())
        .withEntityType(EntityType.USER)
        .add(DEFAULT_USER.getUsername());

    // then
    final var record =
        RecordingExporter.tenantRecords(TenantIntent.ENTITY_ADDED)
            .withTenantKey(tenant.getTenantKey())
            .findFirst();
    assertAuthorizationClaims(record);
  }

  @Test
  public void shouldIncludeClaimsInTenantEntityRemovedEvents() {
    // given
    final var user = createUser(DEFAULT_USER.getUsername());
    final var tenant =
        engine
            .tenant()
            .newTenant()
            .withTenantId(UUID.randomUUID().toString())
            .withName(UUID.randomUUID().toString())
            .create(DEFAULT_USER.getUsername())
            .getValue();

    engine
        .tenant()
        .addEntity(tenant.getTenantId())
        .withEntityId(user.getUsername())
        .withEntityType(EntityType.USER)
        .add(DEFAULT_USER.getUsername());

    // when
    engine
        .tenant()
        .removeEntity(tenant.getTenantId())
        .withEntityId(user.getUsername())
        .withEntityType(EntityType.USER)
        .remove(DEFAULT_USER.getUsername());

    // then
    final var record =
        RecordingExporter.tenantRecords(TenantIntent.ENTITY_REMOVED)
            .withTenantId(tenant.getTenantId())
            .findFirst();
    assertAuthorizationClaims(record);
  }

  @Test
  public void shouldIncludeClaimsInRoleEntityAddedEvents() {
    // given
    final var user = createUser(DEFAULT_USER.getUsername());
    final var role =
        engine
            .role()
            .newRole(UUID.randomUUID().toString())
            .withName(UUID.randomUUID().toString())
            .create(DEFAULT_USER.getUsername())
            .getValue();

    // when
    engine
        .role()
        .addEntity(role.getRoleId())
        .withEntityId(user.getUsername())
        .withEntityType(EntityType.USER)
        .add(DEFAULT_USER.getUsername());

    // then
    final var record =
        RecordingExporter.roleRecords(RoleIntent.ENTITY_ADDED)
            .withRoleId(role.getRoleId())
            .findFirst();
    assertAuthorizationClaims(record);
  }

  @Test
  public void shouldIncludeClaimsInRoleEntityRemovedEvents() {
    // given
    final var user = createUser(DEFAULT_USER.getUsername());
    final var role =
        engine
            .role()
            .newRole(UUID.randomUUID().toString())
            .withName(UUID.randomUUID().toString())
            .create(DEFAULT_USER.getUsername())
            .getValue();

    engine
        .role()
        .addEntity(role.getRoleId())
        .withEntityId(user.getUsername())
        .withEntityType(EntityType.USER)
        .add(DEFAULT_USER.getUsername());

    // when
    engine
        .role()
        .removeEntity(role.getRoleId())
        .withEntityId(user.getUsername())
        .withEntityType(EntityType.USER)
        .remove(DEFAULT_USER.getUsername());

    // then
    final var record =
        RecordingExporter.roleRecords(RoleIntent.ENTITY_REMOVED)
            .withRoleId(role.getRoleId())
            .findFirst();
    assertAuthorizationClaims(record);
  }

  @Test
  public void shouldIncludeClaimsInGroupEntityAddedEvents() {
    // given
    final var user = createUser(DEFAULT_USER.getUsername());
    final var group =
        engine
            .group()
            .newGroup(UUID.randomUUID().toString())
            .withName(UUID.randomUUID().toString())
            .create(DEFAULT_USER.getUsername())
            .getValue();

    // when
    engine
        .group()
        .addEntity(group.getGroupId())
        .withEntityId(user.getUsername())
        .withEntityType(EntityType.USER)
        .add(DEFAULT_USER.getUsername());

    // then
    final var record =
        RecordingExporter.groupRecords(GroupIntent.ENTITY_ADDED)
            .withGroupId(group.getGroupId())
            .findFirst();
    assertAuthorizationClaims(record);
  }

  @Test
  public void shouldIncludeClaimsInGroupEntityRemovedEvents() {
    // given
    final var user = createUser(DEFAULT_USER.getUsername());
    final var group =
        engine
            .group()
            .newGroup(UUID.randomUUID().toString())
            .withName(UUID.randomUUID().toString())
            .create(DEFAULT_USER.getUsername())
            .getValue();

    engine
        .group()
        .addEntity(group.getGroupId())
        .withEntityId(user.getUsername())
        .withEntityType(EntityType.USER)
        .add(DEFAULT_USER.getUsername());

    // when
    engine
        .group()
        .removeEntity(group.getGroupId())
        .withEntityId(user.getUsername())
        .withEntityType(EntityType.USER)
        .remove(DEFAULT_USER.getUsername());

    // then
    final var record =
        RecordingExporter.groupRecords(GroupIntent.ENTITY_REMOVED)
            .withGroupId(group.getGroupId())
            .findFirst();
    assertAuthorizationClaims(record);
  }

  private UserRecordValue createUser(final String authorizedUsername) {
    return engine
        .user()
        .newUser(UUID.randomUUID().toString())
        .withPassword(UUID.randomUUID().toString())
        .withName(UUID.randomUUID().toString())
        .withEmail(UUID.randomUUID().toString())
        .create(authorizedUsername)
        .getValue();
  }

  private void assertAuthorizationClaims(final java.util.Optional<?> record) {
    assertThat(record).isPresent();
    assertThat(((io.camunda.zeebe.protocol.record.Record<?>) record.get()).getAuthorizations())
        .containsEntry(Authorization.AUTHORIZED_USERNAME, DEFAULT_USER.getUsername());
  }
}
