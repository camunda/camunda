/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.api.model.config.initialization.ConfiguredUser;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.AuthorizationScope;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public final class StandaloneJobAuthorizationTest {

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
          .withAuthorizationsEnabled(true)
          .withSecurityConfig(cfg -> cfg.getInitialization().setUsers(List.of(DEFAULT_USER)))
          .withSecurityConfig(
              cfg -> {
                final var defaultRoles = new HashMap<>(cfg.getInitialization().getDefaultRoles());
                defaultRoles.put("admin", Map.of("users", List.of(DEFAULT_USER.getUsername())));
                cfg.getInitialization().setDefaultRoles(defaultRoles);
              });

  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  private final String jobType = "standalone-" + UUID.randomUUID();

  @Before
  public void assignDefaultUserToTenant() {
    assignUserToTenant(DEFAULT_USER.getUsername());
  }

  @Test
  public void shouldRejectCreationWithoutPermissionOnJobType() {
    // given
    final var user = createUser();
    addPermission(user, PermissionType.CREATE, "another-job-type");

    // when
    final var rejection =
        engine
            .standaloneJob()
            .withType(jobType)
            .byUser(user.getUsername())
            .createExpectingRejection();

    // then
    Assertions.assertThat(rejection).hasRejectionType(RejectionType.FORBIDDEN);
    assertThat(rejection.getRejectionReason()).contains("'CREATE'", "'STANDALONE_JOB'", jobType);
  }

  @Test
  public void shouldCreateWithPermissionOnJobType() {
    // given
    final var user = createUser();
    addPermission(user, PermissionType.CREATE, jobType);

    // when
    final var created =
        engine.standaloneJob().withType(jobType).byUser(user.getUsername()).create();

    // then
    assertThat(created.getValue().getType()).isEqualTo(jobType);
  }

  @Test
  public void shouldNotHandOutStandaloneJobToWorkerAuthorizedOnlyForProcesses() {
    // given
    engine.standaloneJob().withType(jobType).byUser(DEFAULT_USER.getUsername()).create();
    final var worker = createUser();
    addPermissionOn(
        worker,
        AuthorizationResourceType.PROCESS_DEFINITION,
        PermissionType.UPDATE_PROCESS_INSTANCE,
        AuthorizationScope.WILDCARD_CHAR);

    // when
    final var batch = engine.jobs().withType(jobType).activate(worker.getUsername());

    // then
    assertThat(batch.getValue().getJobs()).isEmpty();
  }

  @Test
  public void shouldHandOutStandaloneJobToWorkerWithPermissionOnJobType() {
    // given
    final long jobKey =
        engine
            .standaloneJob()
            .withType(jobType)
            .byUser(DEFAULT_USER.getUsername())
            .create()
            .getKey();
    final var worker = createUser();
    addPermission(worker, PermissionType.UPDATE, jobType);

    // when
    final var batch = engine.jobs().withType(jobType).activate(worker.getUsername());

    // then
    assertThat(batch.getValue().getJobKeys()).containsExactly(jobKey);
  }

  private UserRecordValue createUser() {
    final var user =
        engine
            .user()
            .newUser(UUID.randomUUID().toString())
            .withPassword(UUID.randomUUID().toString())
            .withName(UUID.randomUUID().toString())
            .withEmail(UUID.randomUUID().toString())
            .create()
            .getValue();
    assignUserToTenant(user.getUsername());
    return user;
  }

  private void assignUserToTenant(final String username) {
    engine
        .tenant()
        .addEntity(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
        .withEntityType(EntityType.USER)
        .withEntityId(username)
        .add();
  }

  private void addPermission(
      final UserRecordValue user, final PermissionType permissionType, final String jobType) {
    addPermissionOn(user, AuthorizationResourceType.STANDALONE_JOB, permissionType, jobType);
  }

  private void addPermissionOn(
      final UserRecordValue user,
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType,
      final String resourceId) {
    engine
        .authorization()
        .newAuthorization()
        .withPermissions(permissionType)
        .withOwnerId(user.getUsername())
        .withOwnerType(AuthorizationOwnerType.USER)
        .withResourceType(resourceType)
        .withResourceMatcher(
            AuthorizationScope.WILDCARD_CHAR.equals(resourceId)
                ? AuthorizationResourceMatcher.ANY
                : AuthorizationResourceMatcher.ID)
        .withResourceId(resourceId)
        .create(DEFAULT_USER.getUsername());
  }
}
