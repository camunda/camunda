/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.agenthistory;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.api.model.config.initialization.ConfiguredUser;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.AgentHistoryIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
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
 * Engine-level authorization coverage for AgentHistory CREATE. The auth check is sourced from the
 * agent instance record (bpmnProcessId + tenantId), not from the job or element instance directly,
 * so permission and tenant denials are independently verifiable.
 */
public final class AgentHistoryCreateAuthorizationTest {

  private static final String PROCESS_ID = "process";
  private static final String SERVICE_TASK_ID = "agent-task";
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
  public void shouldAuthorizeWithUpdateProcessInstancePermission() {
    final var setup = deployAndSetupUnderTenant(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    final var user = createUser();
    assignUserToTenant(TenantOwned.DEFAULT_TENANT_IDENTIFIER, user.getUsername());
    addPermissionsToUser(
        user,
        AuthorizationResourceType.PROCESS_DEFINITION,
        PermissionType.UPDATE_PROCESS_INSTANCE,
        AuthorizationResourceMatcher.ID,
        PROCESS_ID);

    final var created =
        engine
            .agentHistories()
            .withAgentInstanceKey(setup.agentInstanceKey())
            .withJobKey(setup.jobKey())
            .withElementInstanceKey(setup.elementInstanceKey())
            .create(user.getUsername());

    assertThat(created.getIntent()).isEqualTo(AgentHistoryIntent.CREATED);
  }

  @Test
  public void shouldRejectWhenCallerNotAuthorized() {
    final var setup = deployAndSetupUnderTenant(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    final var user = createUser();
    assignUserToTenant(TenantOwned.DEFAULT_TENANT_IDENTIFIER, user.getUsername());

    final var rejection =
        engine
            .agentHistories()
            .withAgentInstanceKey(setup.agentInstanceKey())
            .withJobKey(setup.jobKey())
            .withElementInstanceKey(setup.elementInstanceKey())
            .expectRejection()
            .create(user.getUsername());

    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.FORBIDDEN);
  }

  @Test
  public void shouldRejectWhenAuthorizedTenantsExcludeAgentInstanceTenant() {
    final var setup = deployAndSetupUnderTenant(CUSTOM_TENANT);
    final var user = createUser();
    assignUserToTenant(TenantOwned.DEFAULT_TENANT_IDENTIFIER, user.getUsername());
    addPermissionsToUser(
        user,
        AuthorizationResourceType.PROCESS_DEFINITION,
        PermissionType.UPDATE_PROCESS_INSTANCE,
        AuthorizationResourceMatcher.ID,
        PROCESS_ID);

    final var rejection =
        engine
            .agentHistories()
            .withAgentInstanceKey(setup.agentInstanceKey())
            .withJobKey(setup.jobKey())
            .withElementInstanceKey(setup.elementInstanceKey())
            .withAuthorizedTenantIds(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .expectRejection()
            .create(user.getUsername());

    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
  }

  private TestSetup deployAndSetupUnderTenant(final String tenantId) {
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

    final long elementInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .withElementId(SERVICE_TASK_ID)
            .getFirst()
            .getKey();

    final long agentInstanceKey =
        engine
            .agentInstances()
            .withElementInstanceKey(elementInstanceKey)
            .withDefinition("gpt-4o", "openai", "You are a helpful agent.")
            .withAuthorizedTenantIds(tenantId)
            .create(DEFAULT_USER.getUsername())
            .getKey();

    engine.jobs().withType("agent").withTenantId(tenantId).activate(DEFAULT_USER.getUsername());
    final long jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType("agent")
            .getFirst()
            .getKey();

    return new TestSetup(agentInstanceKey, jobKey, elementInstanceKey);
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

  private record TestSetup(long agentInstanceKey, long jobKey, long elementInstanceKey) {}
}
