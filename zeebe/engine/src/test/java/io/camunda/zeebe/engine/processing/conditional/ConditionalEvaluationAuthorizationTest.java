/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.conditional;

import static io.camunda.zeebe.protocol.record.value.AuthorizationScope.WILDCARD;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.configuration.ConfiguredUser;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ConditionalEvaluationIntent;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class ConditionalEvaluationAuthorizationTest {

  private static final String PROCESS_ID = "processId";
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
    assignUserToTenant(TenantOwned.DEFAULT_TENANT_IDENTIFIER, DEFAULT_USER.getUsername());

    engine
        .deployment()
        .withXmlResource(
            "process.bpmn",
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .condition(c -> c.condition("=x > y").zeebeVariableNames("x, y"))
                .endEvent()
                .done())
        .deploy(DEFAULT_USER.getUsername());
  }

  @Test
  public void shouldBeAuthorizedToEvaluateConditionalWithDefaultUser() {
    // given
    final var processDefinitionKey =
        RecordingExporter.processRecords()
            .withBpmnProcessId(PROCESS_ID)
            .getFirst()
            .getValue()
            .getProcessDefinitionKey();

    // when
    final var evaluatedRecord =
        engine
            .conditionalEvaluation()
            .withVariables(Map.of("x", 100, "y", 1))
            .evaluate(DEFAULT_USER.getUsername());

    // then
    assertThat(evaluatedRecord.getIntent()).isEqualTo(ConditionalEvaluationIntent.EVALUATED);

    Assertions.assertThat(evaluatedRecord.getValue())
        .hasProcessDefinitionKey(-1L)
        .hasVariables(Map.of("x", 100, "y", 1))
        .hasTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    assertThat(evaluatedRecord.getValue().getStartedProcessInstances())
        .hasSize(1)
        .first()
        .satisfies(
            instance -> {
              assertThat(instance.getProcessDefinitionKey()).isEqualTo(processDefinitionKey);
              assertThat(instance.getProcessInstanceKey()).isPositive();
            });

    final var processInstanceKey =
        evaluatedRecord.getValue().getStartedProcessInstances().getFirst().getProcessInstanceKey();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withBpmnProcessId(PROCESS_ID)
                .withProcessInstanceKey(processInstanceKey)
                .limit(1))
        .hasSize(1);
  }

  @Test
  public void shouldBeAuthorizedToEvaluateConditionalWithUser() {
    // given
    final var user = createUser();
    assignUserToTenant(TenantOwned.DEFAULT_TENANT_IDENTIFIER, user.getUsername());
    addPermissionsToUser(
        user, AuthorizationResourceType.PROCESS_DEFINITION, PermissionType.CREATE_PROCESS_INSTANCE);

    final var processDefinitionKey =
        RecordingExporter.processRecords()
            .withBpmnProcessId(PROCESS_ID)
            .getFirst()
            .getValue()
            .getProcessDefinitionKey();

    // when
    final var evaluatedRecord =
        engine
            .conditionalEvaluation()
            .withVariables(Map.of("x", 100, "y", 1))
            .evaluate(user.getUsername());

    // then
    assertThat(evaluatedRecord.getIntent()).isEqualTo(ConditionalEvaluationIntent.EVALUATED);

    Assertions.assertThat(evaluatedRecord.getValue())
        .hasProcessDefinitionKey(-1L)
        .hasVariables(Map.of("x", 100, "y", 1))
        .hasTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    assertThat(evaluatedRecord.getValue().getStartedProcessInstances())
        .hasSize(1)
        .first()
        .satisfies(
            instance -> {
              assertThat(instance.getProcessDefinitionKey()).isEqualTo(processDefinitionKey);
              assertThat(instance.getProcessInstanceKey()).isPositive();
            });

    final var processInstanceKey =
        evaluatedRecord.getValue().getStartedProcessInstances().getFirst().getProcessInstanceKey();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withBpmnProcessId(PROCESS_ID)
                .withProcessInstanceKey(processInstanceKey)
                .limit(1))
        .hasSize(1);
  }

  @Test
  public void shouldBeAuthorizedToEvaluateConditionalForNonDefaultTenant() {
    // given
    final String customTenant = "custom-tenant";
    final String customProcessId = "custom-tenant-process";

    engine.tenant().newTenant().withTenantId(customTenant).withName("Custom Tenant").create();
    assignUserToTenant(customTenant, DEFAULT_USER.getUsername());

    final var user = createUser();
    assignUserToTenant(customTenant, user.getUsername());
    addPermissionsToUser(
        user, AuthorizationResourceType.PROCESS_DEFINITION, PermissionType.CREATE_PROCESS_INSTANCE);

    engine
        .deployment()
        .withXmlResource(
            "custom-tenant-process.bpmn",
            Bpmn.createExecutableProcess(customProcessId)
                .startEvent()
                .condition(c -> c.condition("=x > y").zeebeVariableNames("x, y"))
                .endEvent()
                .done())
        .withTenantId(customTenant)
        .deploy(DEFAULT_USER.getUsername());

    final var processDefinitionKey =
        RecordingExporter.processRecords()
            .withBpmnProcessId(customProcessId)
            .getFirst()
            .getValue()
            .getProcessDefinitionKey();

    // when
    final var evaluatedRecord =
        engine
            .conditionalEvaluation()
            .withTenantId(customTenant)
            .withVariables(Map.of("x", 100, "y", 1))
            .evaluate(user.getUsername());

    // then
    assertThat(evaluatedRecord.getIntent()).isEqualTo(ConditionalEvaluationIntent.EVALUATED);

    Assertions.assertThat(evaluatedRecord.getValue())
        .hasProcessDefinitionKey(-1L)
        .hasVariables(Map.of("x", 100, "y", 1))
        .hasTenantId(customTenant);

    assertThat(evaluatedRecord.getValue().getStartedProcessInstances())
        .hasSize(1)
        .first()
        .satisfies(
            instance -> {
              assertThat(instance.getProcessDefinitionKey()).isEqualTo(processDefinitionKey);
              assertThat(instance.getProcessInstanceKey()).isPositive();
            });

    final var processInstanceKey =
        evaluatedRecord.getValue().getStartedProcessInstances().getFirst().getProcessInstanceKey();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withBpmnProcessId(customProcessId)
                .withProcessInstanceKey(processInstanceKey)
                .limit(1))
        .hasSize(1);
  }

  @Test
  public void shouldRejectWhenUserLacksProcessPermissions() {
    // given
    final var user = createUser();
    assignUserToTenant(TenantOwned.DEFAULT_TENANT_IDENTIFIER, user.getUsername());

    // when
    final var rejection =
        engine
            .conditionalEvaluation()
            .withVariables(Map.of("x", 100, "y", 1))
            .expectRejection()
            .evaluate(user.getUsername());

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.FORBIDDEN)
        .hasRejectionReason(
            "Insufficient permissions to perform operation 'CREATE_PROCESS_INSTANCE' on resource 'PROCESS_DEFINITION', required resource identifiers are one of '[*, %s]'"
                .formatted(PROCESS_ID));
  }

  @Test
  public void shouldRejectWhenUserLacksPermissionForOneOfMultipleMatchingProcesses() {
    // given
    final String secondProcessId = "second-" + UUID.randomUUID();
    engine
        .deployment()
        .withXmlResource(
            "second-process.bpmn",
            Bpmn.createExecutableProcess(secondProcessId)
                .startEvent()
                .condition(c -> c.condition("=x > y").zeebeVariableNames("x, y"))
                .endEvent()
                .done())
        .deploy(DEFAULT_USER.getUsername());

    final var user = createUser();
    assignUserToTenant(TenantOwned.DEFAULT_TENANT_IDENTIFIER, user.getUsername());
    addPermissionsToUserForSpecificProcess(
        user,
        AuthorizationResourceType.PROCESS_DEFINITION,
        PermissionType.CREATE_PROCESS_INSTANCE,
        PROCESS_ID);

    // when
    final var rejection =
        engine
            .conditionalEvaluation()
            .withVariables(Map.of("x", 100, "y", 1))
            .expectRejection()
            .evaluate(user.getUsername());

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.FORBIDDEN)
        .hasRejectionReason(
            "Insufficient permissions to perform operation 'CREATE_PROCESS_INSTANCE' on resource 'PROCESS_DEFINITION', required resource identifiers are one of '[*, %s]'"
                .formatted(secondProcessId));
  }

  @Test
  public void shouldRejectWhenUserNotAssignedToTenant() {
    // given
    final String tenant = "restricted-tenant";

    engine.tenant().newTenant().withTenantId(tenant).withName("Restricted Tenant").create();
    assignUserToTenant(tenant, DEFAULT_USER.getUsername());

    final var user = createUser();
    assignUserToTenant(TenantOwned.DEFAULT_TENANT_IDENTIFIER, user.getUsername());
    addPermissionsToUser(
        user, AuthorizationResourceType.PROCESS_DEFINITION, PermissionType.CREATE_PROCESS_INSTANCE);

    engine
        .deployment()
        .withXmlResource(
            "tenant-process.bpmn",
            Bpmn.createExecutableProcess("tenant-process")
                .startEvent()
                .condition(c -> c.condition("=x > y").zeebeVariableNames("x, y"))
                .endEvent()
                .done())
        .withTenantId(tenant)
        .deploy(DEFAULT_USER.getUsername());

    // when
    final var rejection =
        engine
            .conditionalEvaluation()
            .withTenantId(tenant)
            .withVariables(Map.of("x", 100, "y", 1))
            .expectRejection()
            .evaluate(user.getUsername());

    // then
    Assertions.assertThat(rejection).hasRejectionType(RejectionType.FORBIDDEN);
    assertThat(rejection.getRejectionReason()).contains("user is not assigned to this tenant");
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
      final PermissionType permissionType) {
    engine
        .authorization()
        .newAuthorization()
        .withPermissions(permissionType)
        .withOwnerId(user.getUsername())
        .withOwnerType(AuthorizationOwnerType.USER)
        .withResourceType(authorization)
        .withResourceMatcher(WILDCARD.getMatcher())
        .withResourceId(WILDCARD.getResourceId())
        .create(DEFAULT_USER.getUsername());
  }

  private void addPermissionsToUserForSpecificProcess(
      final UserRecordValue user,
      final AuthorizationResourceType authorization,
      final PermissionType permissionType,
      final String processId) {
    engine
        .authorization()
        .newAuthorization()
        .withPermissions(permissionType)
        .withOwnerId(user.getUsername())
        .withOwnerType(AuthorizationOwnerType.USER)
        .withResourceType(authorization)
        .withResourceMatcher(AuthorizationResourceMatcher.ID)
        .withResourceId(processId)
        .create(DEFAULT_USER.getUsername());
  }
}
