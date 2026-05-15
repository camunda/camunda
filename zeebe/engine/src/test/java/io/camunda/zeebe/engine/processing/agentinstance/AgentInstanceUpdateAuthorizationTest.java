/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.agentinstance;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.configuration.ConfiguredUser;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AgentInstanceStatus;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

/**
 * Engine-level authorization coverage for AgentInstance UPDATE, mirroring the fused permission +
 * tenant check used by AdHocSubProcessInstructionActivateProcessor. The processor forwards the
 * {@code rejection.type()} from the auth call verbatim, so callers can observe either {@link
 * RejectionType#FORBIDDEN} (permission denial) or {@link RejectionType#NOT_FOUND} (tenant denial)
 * depending on the failure mode.
 *
 * <p>Lives in a separate file from {@link AgentInstanceUpdateTest} because the authorizations
 * harness has to enable authorizations on the engine; the per-test harness is therefore loaded once
 * per file rather than once per test.
 */
public final class AgentInstanceUpdateAuthorizationTest {

  private static final String PROCESS_ID = "process";
  private static final String SERVICE_TASK_ID = "service-task";
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
          .withSecurityConfig(cfg -> cfg.getAuthorizations().setEnabled(true))
          .withSecurityConfig(cfg -> cfg.getInitialization().setUsers(List.of(DEFAULT_USER)))
          .withSecurityConfig(
              cfg -> {
                cfg.getInitialization()
                    .getDefaultRoles()
                    .put("admin", Map.of("users", List.of(DEFAULT_USER.getUsername())));
                cfg.getMultiTenancy().setChecksEnabled(true);
              });

  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Before
  public void before() {
    engine.tenant().newTenant().withTenantId(CUSTOM_TENANT).withName("Custom Tenant").create();
    assignUserToTenant(TenantOwned.DEFAULT_TENANT_IDENTIFIER, DEFAULT_USER.getUsername());
    assignUserToTenant(CUSTOM_TENANT, DEFAULT_USER.getUsername());
  }

  @Test
  public void shouldAuthorizeWithUpdateProcessInstancePermission() {
    // given - a user with explicit UPDATE_PROCESS_INSTANCE on the parent PROCESS_DEFINITION
    final var agentInstanceKey =
        createAgentInstanceUnderTenant(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    final var user = createUser();
    assignUserToTenant(TenantOwned.DEFAULT_TENANT_IDENTIFIER, user.getUsername());
    addPermissionsToUser(
        user,
        AuthorizationResourceType.PROCESS_DEFINITION,
        PermissionType.UPDATE_PROCESS_INSTANCE,
        AuthorizationResourceMatcher.ID,
        PROCESS_ID);

    // when
    final var updated =
        engine
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withStatus(AgentInstanceStatus.THINKING)
            .update(user.getUsername());

    // then
    assertThat(updated.getIntent()).isEqualTo(AgentInstanceIntent.UPDATED);
    assertThat(updated.getKey()).isEqualTo(agentInstanceKey);
  }

  @Test
  public void shouldRejectWhenCallerNotAuthorized() {
    // given - a user assigned to the resource's tenant but without any permissions
    final var agentInstanceKey =
        createAgentInstanceUnderTenant(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    final var user = createUser();
    assignUserToTenant(TenantOwned.DEFAULT_TENANT_IDENTIFIER, user.getUsername());

    // when
    final var rejection =
        engine
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withStatus(AgentInstanceStatus.THINKING)
            .expectRejection()
            .update(user.getUsername());

    // then - permission denial forwards as FORBIDDEN with the auth-subsystem's standard reason,
    // citing the permission and the process definition (the parent resource the engine checks).
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.FORBIDDEN);
    assertThat(rejection.getRejectionReason())
        .contains(PermissionType.UPDATE_PROCESS_INSTANCE.name())
        .contains(PROCESS_ID);
  }

  @Test
  public void shouldRejectWhenAuthorizedTenantsExcludeAgentInstanceTenant() {
    // given - agent instance created under CUSTOM_TENANT, caller has UPDATE_PROCESS_INSTANCE on
    // the process id but is only assigned to DEFAULT_TENANT
    final var agentInstanceKey = createAgentInstanceUnderTenant(CUSTOM_TENANT);
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
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withStatus(AgentInstanceStatus.THINKING)
            .withAuthorizedTenantIds(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .expectRejection()
            .update(user.getUsername());

    // then - tenant denial on an existing PROCESS_DEFINITION forwards as NOT_FOUND
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
  }

  private long createAgentInstanceUnderTenant(final String tenantId) {
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask(SERVICE_TASK_ID, t -> t.zeebeJobType("agent"))
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
    final var elementInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .withElementId(SERVICE_TASK_ID)
            .getFirst()
            .getKey();
    return engine
        .agentInstances()
        .withElementInstanceKey(elementInstanceKey)
        .withAuthorizedTenantIds(tenantId)
        .create(DEFAULT_USER.getUsername())
        .getValue()
        .getAgentInstanceKey();
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
