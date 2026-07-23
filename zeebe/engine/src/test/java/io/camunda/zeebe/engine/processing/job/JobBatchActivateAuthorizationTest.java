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
import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.engine.util.AuthorizationUtil;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.AuthorizationScope;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.TenantFilter;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class JobBatchActivateAuthorizationTest {

  public static final String JOB_TYPE = "jobType";

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
          .withMultiTenancyChecksEnabled(true)
          .withSecurityConfig(
              cfg -> {
                final var defaultRoles = new HashMap<>(cfg.getInitialization().getDefaultRoles());
                defaultRoles.put("admin", Map.of("users", List.of(DEFAULT_USER.getUsername())));
                cfg.getInitialization().setDefaultRoles(defaultRoles);
              });

  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldBeAuthorizedToActivateAllJobsWithDefaultUser() {
    // given
    assignUserToTenant(TenantOwned.DEFAULT_TENANT_IDENTIFIER, DEFAULT_USER.getUsername());
    final var processId1 = Strings.newRandomValidBpmnId();
    final var processId2 = Strings.newRandomValidBpmnId();
    createJobs(processId1, processId2);

    // when
    final var response =
        engine
            .jobs()
            .withType(JOB_TYPE)
            .withMaxJobsToActivate(2)
            .activate(DEFAULT_USER.getUsername());

    // then
    assertThat(response.getValue().getJobs())
        .hasSize(2)
        .extracting(JobRecordValue::getBpmnProcessId)
        .containsExactlyInAnyOrder(processId1, processId2);
  }

  @Test
  public void shouldBeAuthorizedToActivateMultipleJobsWithUser() {
    // given
    assignUserToTenant(TenantOwned.DEFAULT_TENANT_IDENTIFIER, DEFAULT_USER.getUsername());
    final var processId1 = Strings.newRandomValidBpmnId();
    final var processId2 = Strings.newRandomValidBpmnId();
    createJobs(processId1, processId2);
    final var user = createUser();
    assignUserToTenant(TenantOwned.DEFAULT_TENANT_IDENTIFIER, user.getUsername());
    addPermissionsToUser(
        user,
        AuthorizationResourceType.PROCESS_DEFINITION,
        PermissionType.UPDATE_PROCESS_INSTANCE,
        processId1,
        processId2);

    // when
    final var response =
        engine.jobs().withType(JOB_TYPE).withMaxJobsToActivate(2).activate(user.getUsername());

    // then
    assertThat(response.getValue().getJobs())
        .hasSize(2)
        .extracting(JobRecordValue::getBpmnProcessId)
        .containsExactlyInAnyOrder(processId1, processId2);
  }

  @Test
  public void shouldBeAuthorizedToActivateSingleJobWithUser() {
    // given
    assignUserToTenant(TenantOwned.DEFAULT_TENANT_IDENTIFIER, DEFAULT_USER.getUsername());
    final var processId1 = Strings.newRandomValidBpmnId();
    final var processId2 = Strings.newRandomValidBpmnId();
    createJobs(processId1, processId2);
    final var user = createUser();
    assignUserToTenant(TenantOwned.DEFAULT_TENANT_IDENTIFIER, user.getUsername());
    addPermissionsToUser(
        user,
        AuthorizationResourceType.PROCESS_DEFINITION,
        PermissionType.UPDATE_PROCESS_INSTANCE,
        processId1);

    // when
    final var response =
        engine.jobs().withType(JOB_TYPE).withMaxJobsToActivate(2).activate(user.getUsername());

    // then
    assertThat(response.getValue().getJobs())
        .hasSize(1)
        .extracting(JobRecordValue::getBpmnProcessId)
        .containsOnly(processId1);
  }

  @Test
  public void shouldNotActivateJobsWithUnauthorizedUser() {
    // given
    assignUserToTenant(TenantOwned.DEFAULT_TENANT_IDENTIFIER, DEFAULT_USER.getUsername());
    final var processId1 = Strings.newRandomValidBpmnId();
    final var processId2 = Strings.newRandomValidBpmnId();
    createJobs(processId1, processId2);
    final var user = createUser();
    assignUserToTenant(TenantOwned.DEFAULT_TENANT_IDENTIFIER, user.getUsername());

    // when
    final var response =
        engine.jobs().withType(JOB_TYPE).withMaxJobsToActivate(2).activate(user.getUsername());

    // then
    assertThat(response.getValue().getJobs()).isEmpty();
  }

  @Test
  public void shouldRejectWhenUserNotAuthorizedForProvidedTenants() {
    // given
    final String tenant = "restricted-tenant";

    engine.tenant().newTenant().withTenantId(tenant).withName("Restricted Tenant").create();

    final var user = createUser();
    assignUserToTenant(TenantOwned.DEFAULT_TENANT_IDENTIFIER, user.getUsername());

    // when
    final var rejection =
        engine
            .jobs()
            .withType(JOB_TYPE)
            .withMaxJobsToActivate(2)
            .withTenantFilter(TenantFilter.PROVIDED)
            .withTenantIds(List.of(tenant))
            .expectRejection()
            .activate(user.getUsername());

    // then
    Assertions.assertThat(rejection).hasRejectionType(RejectionType.UNAUTHORIZED);
    assertThat(rejection.getRejectionReason())
        .contains("Expected to activate job batch for tenants")
        .contains("but user is not authorized");
  }

  @Test
  public void shouldRejectWhenUserNotAssignedToDefaultTenantAndNoTenantsProvided() {
    // given
    final var user = createUser();
    addPermissionsToUser(
        user, AuthorizationResourceType.PROCESS_DEFINITION, PermissionType.UPDATE_PROCESS_INSTANCE);

    // when
    // no tenant IDs provided: an empty list must not vacuously pass tenant authorization for the
    // default tenant that determineTenantIds() would otherwise default to
    final var rejection =
        engine
            .jobs()
            .withType(JOB_TYPE)
            .withMaxJobsToActivate(2)
            .withTenantFilter(TenantFilter.PROVIDED)
            .expectRejection()
            .activate(user.getUsername());

    // then
    Assertions.assertThat(rejection).hasRejectionType(RejectionType.UNAUTHORIZED);
    assertThat(rejection.getRejectionReason())
        .contains("Expected to activate job batch for tenants")
        .contains("but user is not authorized");
  }

  @Test
  public void shouldActivateEmptyBatchForAnonymousCallerWithProvidedTenantFilter() {
    // given
    final var authInfo =
        AuthorizationUtil.getAuthInfoWithClaim(Authorization.AUTHORIZED_ANONYMOUS_USER, true);

    // when
    final var response =
        engine
            .jobs()
            .withType("nonExistentJobType")
            .withMaxJobsToActivate(2)
            .withTenantFilter(TenantFilter.PROVIDED)
            .withTenantIds(List.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER))
            .activate(authInfo);

    // then
    assertThat(response.getValue().getJobs()).isEmpty();
  }

  private void assignUserToTenant(final String tenantId, final String username) {
    engine
        .tenant()
        .addEntity(tenantId)
        .withEntityType(EntityType.USER)
        .withEntityId(username)
        .add();
  }

  private UserRecordValue createUser() {
    return engine
        .user()
        .newUser(UUID.randomUUID().toString())
        .withPassword(UUID.randomUUID().toString())
        .withName(UUID.randomUUID().toString())
        .withEmail(UUID.randomUUID().toString())
        .create()
        .getValue();
  }

  private void addPermissionsToUser(
      final UserRecordValue user,
      final AuthorizationResourceType authorization,
      final PermissionType permissionType,
      final String... resourceIds) {
    for (final String resourceId : resourceIds) {
      final var resourceMatcher =
          AuthorizationScope.WILDCARD_CHAR.equals(resourceId)
              ? AuthorizationResourceMatcher.ANY
              : AuthorizationResourceMatcher.ID;
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
  }

  private void createJobs(final String... processIds) {
    for (final String processId : processIds) {
      engine
          .deployment()
          .withXmlResource(
              "%s.bpmn".formatted(processId),
              Bpmn.createExecutableProcess(processId)
                  .startEvent()
                  .serviceTask("serviceTask", t -> t.zeebeJobType(JOB_TYPE))
                  .endEvent()
                  .done())
          .deploy(DEFAULT_USER.getUsername());

      engine.processInstance().ofBpmnProcessId(processId).create(DEFAULT_USER.getUsername());
    }

    RecordingExporter.jobRecords(JobIntent.CREATED).limit(processIds.length).await();
  }
}
