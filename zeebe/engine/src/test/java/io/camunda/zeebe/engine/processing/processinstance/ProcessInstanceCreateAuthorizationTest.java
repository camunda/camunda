/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.api.model.config.initialization.ConfiguredUser;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class ProcessInstanceCreateAuthorizationTest {
  private static final String PROCESS_ID = "processId";
  private static final String CUSTOM_TENANT = "custom-tenant";
  private static final String OTHER_TENANT = "other-tenant";

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

  @Before
  public void before() {
    engine
        .deployment()
        .withXmlResource(
            "process.bpmn", Bpmn.createExecutableProcess(PROCESS_ID).startEvent().endEvent().done())
        .deploy(DEFAULT_USER.getUsername());
    assignUserToTenant(TenantOwned.DEFAULT_TENANT_IDENTIFIER, DEFAULT_USER.getUsername());
    engine.tenant().newTenant().withTenantId(CUSTOM_TENANT).withName("Custom Tenant").create();
    engine.tenant().newTenant().withTenantId(OTHER_TENANT).withName("Other Tenant").create();
    assignUserToTenant(CUSTOM_TENANT, DEFAULT_USER.getUsername());
    assignUserToTenant(OTHER_TENANT, DEFAULT_USER.getUsername());
  }

  @Test
  public void shouldBeAuthorizedToCreateInstanceWithDefaultUser() {
    // when
    final var processInstanceKey =
        engine.processInstance().ofBpmnProcessId(PROCESS_ID).create(DEFAULT_USER.getUsername());

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeAuthorizedToCreateInstanceWithResultWithDefaultUser() {
    // when
    final var processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withResult()
            .create(DEFAULT_USER.getUsername());

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeAuthorizedToCreateInstanceWithUser() {
    // given
    final var user = createUser();
    assignUserToTenant(TenantOwned.DEFAULT_TENANT_IDENTIFIER, user.getUsername());
    addPermissionsToUser(
        user,
        AuthorizationResourceType.PROCESS_DEFINITION,
        PermissionType.CREATE_PROCESS_INSTANCE,
        AuthorizationResourceMatcher.ID,
        PROCESS_ID);

    // when
    final var processInstanceKey =
        engine.processInstance().ofBpmnProcessId(PROCESS_ID).create(user.getUsername());

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeAuthorizedToCreateInstanceWithResultWithUser() {
    // given
    final var user = createUser();
    assignUserToTenant(TenantOwned.DEFAULT_TENANT_IDENTIFIER, user.getUsername());
    addPermissionsToUser(
        user,
        AuthorizationResourceType.PROCESS_DEFINITION,
        PermissionType.CREATE_PROCESS_INSTANCE,
        AuthorizationResourceMatcher.ID,
        PROCESS_ID);

    // when
    final var processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withResult()
            .create(user.getUsername());

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeUnauthorizedToCreateInstanceIfNoPermissions() {
    // given
    final var user = createUser();

    // when
    engine
        .processInstance()
        .ofBpmnProcessId(PROCESS_ID)
        .expectRejection()
        .create(user.getUsername());

    // then
    Assertions.assertThat(
            RecordingExporter.processInstanceCreationRecords().onlyCommandRejections().getFirst())
        .hasRejectionType(RejectionType.FORBIDDEN)
        .hasRejectionReason(
            "Insufficient permissions to perform operation 'CREATE_PROCESS_INSTANCE' on resource 'PROCESS_DEFINITION', required resource identifiers are one of '[*, %s]'"
                .formatted(PROCESS_ID));
  }

  @Test
  public void shouldBeUnauthorizedToCreateInstanceWithResultIfNoPermissions() {
    // given
    final var user = createUser();

    // when
    engine
        .processInstance()
        .ofBpmnProcessId(PROCESS_ID)
        .expectRejection()
        .withResult()
        .asyncCreate(user.getUsername());

    // then
    Assertions.assertThat(
            RecordingExporter.processInstanceCreationRecords().onlyCommandRejections().getFirst())
        .hasRejectionType(RejectionType.FORBIDDEN)
        .hasRejectionReason(
            "Insufficient permissions to perform operation 'CREATE_PROCESS_INSTANCE' on resource 'PROCESS_DEFINITION', required resource identifiers are one of '[*, %s]'"
                .formatted(PROCESS_ID));
  }

  @Test
  public void shouldRejectCreationWhenAssignedTenantsExcludeProcessTenant() {
    // given - process deployed under CUSTOM_TENANT, caller has a global (non-tenant-restricted)
    // CREATE_PROCESS_INSTANCE permission but is only assigned to OTHER_TENANT
    deployProcessToTenant(CUSTOM_TENANT);
    final var user = createUser();
    assignUserToTenant(OTHER_TENANT, user.getUsername());
    addPermissionsToUser(
        user,
        AuthorizationResourceType.PROCESS_DEFINITION,
        PermissionType.CREATE_PROCESS_INSTANCE,
        AuthorizationResourceMatcher.ID,
        PROCESS_ID);

    // when
    engine
        .processInstance()
        .ofBpmnProcessId(PROCESS_ID)
        .withTenantId(CUSTOM_TENANT)
        .expectRejection()
        .create(user.getUsername());

    // then - tenant denial on an existing process forwards as NOT_FOUND, masking its existence
    // from callers unauthorized for its tenant
    Assertions.assertThat(
            RecordingExporter.processInstanceCreationRecords().onlyCommandRejections().getFirst())
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to create an instance of process with key '-1', but no such process was found");
  }

  @Test
  public void shouldCreateInstanceWhenAssignedToProcessTenant() {
    // given - same as above, but caller is assigned to CUSTOM_TENANT
    deployProcessToTenant(CUSTOM_TENANT);
    final var user = createUser();
    assignUserToTenant(CUSTOM_TENANT, user.getUsername());
    addPermissionsToUser(
        user,
        AuthorizationResourceType.PROCESS_DEFINITION,
        PermissionType.CREATE_PROCESS_INSTANCE,
        AuthorizationResourceMatcher.ID,
        PROCESS_ID);

    // when
    final var processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withTenantId(CUSTOM_TENANT)
            .create(user.getUsername());

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .isTrue();
  }

  private void deployProcessToTenant(final String tenantId) {
    engine
        .deployment()
        .withXmlResource(
            "process.bpmn", Bpmn.createExecutableProcess(PROCESS_ID).startEvent().endEvent().done())
        .withTenantId(tenantId)
        .deploy(DEFAULT_USER.getUsername());
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
      final AuthorizationResourceMatcher matcher,
      final String resourceId) {
    engine
        .authorization()
        .newAuthorization()
        .withPermissions(permissionType)
        .withOwnerId(user.getUsername())
        .withOwnerType(AuthorizationOwnerType.USER)
        .withResourceType(authorization)
        .withResourceMatcher(matcher)
        .withResourceId(resourceId)
        .create(DEFAULT_USER.getUsername());
  }
}
