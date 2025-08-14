/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.tenant;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.configuration.ConfiguredUser;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.TenantRecordValue;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class DeleteTenantAuthorizationTest {

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
                cfg.getMultiTenancy().setChecksEnabled(true);
              });

  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldBeAuthorizedAsDefaultUserToDeleteDefaultTenant() {
    // given
    final var defaultUsername = DEFAULT_USER.getUsername();

    // when
    final var tenantRecord =
        engine.tenant().deleteTenant(TenantOwned.DEFAULT_TENANT_IDENTIFIER).delete(defaultUsername);

    // then
    assertThat(tenantRecord).isNotNull();
  }

  @Test
  public void shouldBeAuthorizedToDeleteAssignedTenantWithPermissions() {
    // given
    final var user = createUser();
    final var tenantId = UUID.randomUUID().toString();
    createTenant(tenantId);
    addPermissionsToUser(
        user,
        AuthorizationResourceType.TENANT,
        PermissionType.DELETE,
        AuthorizationResourceMatcher.ID,
        tenantId);
    assignUserToTenant(user.getUsername(), tenantId);

    // when
    engine.tenant().deleteTenant(tenantId).delete(user.getUsername());

    // then
    assertThat(
            RecordingExporter.tenantRecords()
                .withTenantId(tenantId)
                .withIntent(TenantIntent.DELETED)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeUnauthorizedToDeleteUnassignedTenantWithPermissions() {
    // given
    final var user = createUser();
    final var tenantId = UUID.randomUUID().toString();
    createTenant(tenantId);

    // when / then
    final var rejectedDeleteRecord =
        engine.tenant().deleteTenant(tenantId).expectRejection().delete(user.getUsername());

    // then
    assertThat(rejectedDeleteRecord)
        .hasRejectionType(RejectionType.FORBIDDEN)
        .hasRejectionReason(
            "Insufficient permissions to perform operation 'DELETE' on resource 'TENANT', required resource identifiers are one of '[*, %s]'"
                .formatted(tenantId));
  }

  private UserRecordValue createUser() {
    return engine
        .user()
        .newUser(UUID.randomUUID().toString())
        .withPassword(UUID.randomUUID().toString())
        .withName(UUID.randomUUID().toString())
        .withEmail(UUID.randomUUID().toString())
        .create(DEFAULT_USER.getUsername())
        .getValue();
  }

  private void addPermissionsToUser(
      final UserRecordValue user,
      final AuthorizationResourceType authorization,
      final PermissionType permissionType,
      final AuthorizationResourceMatcher resourceMatcher,
      final String resourceId) {
    engine
        .authorization()
        .newAuthorization()
        .withPermissions(permissionType)
        .withOwnerId(user.getUsername())
        .withOwnerType(AuthorizationOwnerType.USER)
        .withResourceType(authorization)
        .withResourceMatcher(resourceMatcher)
        .withResourceId(resourceId)
        .create(DEFAULT_USER.getUsername());
  }

  private TenantRecordValue createTenant(final String tenantId) {
    return engine.tenant().newTenant().withTenantId(tenantId).create().getValue();
  }

  private void assignUserToTenant(final String username, final String tenantId) {
    engine
        .tenant()
        .addEntity(tenantId)
        .withEntityId(username)
        .withEntityType(EntityType.USER)
        .add();
  }
}
