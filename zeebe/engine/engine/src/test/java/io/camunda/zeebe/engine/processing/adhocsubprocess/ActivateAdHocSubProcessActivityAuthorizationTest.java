/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.adhocsubprocess;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.api.model.config.initialization.ConfiguredUser;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.AdHocSubProcessInstructionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
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

/**
 * Engine-level authorization coverage for AdHocSubProcessInstruction ACTIVATE, mirroring {@link
 * io.camunda.zeebe.engine.processing.agentinstance.AgentInstanceUpdateAuthorizationTest}. The
 * processor rejects with {@link RejectionType#NOT_FOUND} whenever the caller's authorized tenants
 * don't include the ad-hoc sub-process instance's tenant, to avoid leaking cross-tenant existence
 * information.
 *
 * <p>Lives in a separate file from {@link ActivateAdHocSubProcessActivityTest} because the
 * authorizations harness has to enable authorizations and multi-tenancy checks on the engine.
 */
public final class ActivateAdHocSubProcessActivityAuthorizationTest {

  private static final String PROCESS_ID = "process";
  private static final String AD_HOC_SUB_PROCESS_ELEMENT_ID = "ad-hoc";
  private static final String CUSTOM_TENANT = "custom-tenant";
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
    engine.tenant().newTenant().withTenantId(CUSTOM_TENANT).withName("Custom Tenant").create();
    assignUserToTenant(TenantOwned.DEFAULT_TENANT_IDENTIFIER, DEFAULT_USER.getUsername());
    assignUserToTenant(CUSTOM_TENANT, DEFAULT_USER.getUsername());
  }

  @Test
  public void shouldActivateWithMatchingTenantAndPermission() {
    // given - ad-hoc sub-process created under CUSTOM_TENANT, caller has UPDATE_PROCESS_INSTANCE
    // on the process id and is assigned to CUSTOM_TENANT
    final var adHocSubProcessInstanceKey = createAdHocSubProcessUnderTenant(CUSTOM_TENANT);
    final var user = createUser();
    assignUserToTenant(CUSTOM_TENANT, user.getUsername());
    addPermissionsToUser(
        user,
        AuthorizationResourceType.PROCESS_DEFINITION,
        PermissionType.UPDATE_PROCESS_INSTANCE,
        AuthorizationResourceMatcher.ID,
        PROCESS_ID);

    // when
    final var activated =
        engine
            .adHocSubProcessActivity()
            .withAdHocSubProcessInstanceKey(adHocSubProcessInstanceKey)
            .withElementIds("A")
            .withAuthorizedTenantIds(CUSTOM_TENANT)
            .activate(user.getUsername());

    // then
    assertThat(activated.getIntent()).isEqualTo(AdHocSubProcessInstructionIntent.ACTIVATED);
  }

  @Test
  public void shouldRejectWhenAuthorizedTenantsExcludeAdHocSubProcessTenant() {
    // given - ad-hoc sub-process created under CUSTOM_TENANT, caller has UPDATE_PROCESS_INSTANCE
    // on the process id but is only assigned to DEFAULT_TENANT
    final var adHocSubProcessInstanceKey = createAdHocSubProcessUnderTenant(CUSTOM_TENANT);
    final var user = createUser();
    assignUserToTenant(TenantOwned.DEFAULT_TENANT_IDENTIFIER, user.getUsername());
    addPermissionsToUser(
        user,
        AuthorizationResourceType.PROCESS_DEFINITION,
        PermissionType.UPDATE_PROCESS_INSTANCE,
        AuthorizationResourceMatcher.ID,
        PROCESS_ID);

    // when
    final var rejection =
        engine
            .adHocSubProcessActivity()
            .withAdHocSubProcessInstanceKey(adHocSubProcessInstanceKey)
            .withElementIds("A")
            .withAuthorizedTenantIds(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .expectRejection()
            .activate(user.getUsername());

    // then - tenant denial on an existing ad-hoc sub-process forwards as NOT_FOUND, masking its
    // existence from callers unauthorized for its tenant
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
    assertThat(rejection.getRejectionReason())
        .contains(
            "Expected to activate activities for ad-hoc sub-process but no ad-hoc sub-process instance found with key '%s'"
                .formatted(adHocSubProcessInstanceKey));
  }

  private long createAdHocSubProcessUnderTenant(final String tenantId) {
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .adHocSubProcess(
                    AD_HOC_SUB_PROCESS_ELEMENT_ID, adHocSubProcess -> adHocSubProcess.task("A"))
                .endEvent()
                .done())
        .withTenantId(tenantId)
        .deploy(DEFAULT_USER.getUsername());
    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withTenantId(tenantId)
            .create(DEFAULT_USER.getUsername());
    return RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.AD_HOC_SUB_PROCESS)
        .getFirst()
        .getKey();
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
