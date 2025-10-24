/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment;

import static io.camunda.zeebe.protocol.record.value.AuthorizationScope.WILDCARD;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.configuration.ConfiguredUser;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class MultiTenantDeploymentCreateAuthorizationTest {
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

  @Test
  public void shouldBeAuthorizedToDeployWithDefaultUser() {
    // given
    assignUserToTenant(TenantOwned.DEFAULT_TENANT_IDENTIFIER, DEFAULT_USER.getUsername());
    final var processId = Strings.newRandomValidBpmnId();

    // when
    engine
        .deployment()
        .withXmlResource(
            "process.bpmn", Bpmn.createExecutableProcess(processId).startEvent().endEvent().done())
        .withTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
        .deploy(DEFAULT_USER.getUsername());

    // when
    assertThat(
            RecordingExporter.processRecords(ProcessIntent.CREATED)
                .withBpmnProcessId(processId)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeAuthorizedToDeployWithPermissions() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    final var user = createUser();
    addPermissionsToUser(user, AuthorizationResourceType.RESOURCE, PermissionType.CREATE);
    assignUserToTenant(TenantOwned.DEFAULT_TENANT_IDENTIFIER, user.getUsername());

    // when
    engine
        .deployment()
        .withXmlResource(
            "process.bpmn", Bpmn.createExecutableProcess(processId).startEvent().endEvent().done())
        .withTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
        .deploy(user.getUsername());

    // when
    assertThat(
            RecordingExporter.processRecords(ProcessIntent.CREATED)
                .withBpmnProcessId(processId)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeUnauthorizedToDeployWithoutPermissions() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    final var user = createUser();
    assignUserToTenant(TenantOwned.DEFAULT_TENANT_IDENTIFIER, user.getUsername());

    // when
    final var rejection =
        engine
            .deployment()
            .withXmlResource(
                "process.bpmn",
                Bpmn.createExecutableProcess(processId).startEvent().endEvent().done())
            .withTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .expectRejection()
            .deploy(user.getUsername());

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.FORBIDDEN)
        .hasRejectionReason(
            "Insufficient permissions to perform operation 'CREATE' on resource 'RESOURCE'");
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

  private void assignUserToTenant(final String tenantId, final String username) {
    engine
        .tenant()
        .addEntity(tenantId)
        .withEntityType(EntityType.USER)
        .withEntityId(username)
        .add();
  }
}
