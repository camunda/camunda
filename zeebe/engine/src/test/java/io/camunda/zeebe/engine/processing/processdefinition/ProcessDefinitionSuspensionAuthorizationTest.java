/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processdefinition;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.api.model.config.initialization.ConfiguredUser;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.EngineRule.ResetRecordingExporterMode;
import io.camunda.zeebe.engine.util.EngineRule.ResetRecordingExporterTestWatcherMode;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessDefinitionSuspensionIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.ProcessMetadataValue;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;

/** Suspend and resume are gated by the same permission, so both are covered here. */
public class ProcessDefinitionSuspensionAuthorizationTest {

  private static final String FORBIDDEN_MESSAGE =
      "Insufficient permissions to perform operation 'SUSPEND_PROCESS_DEFINITION' on resource 'PROCESS_DEFINITION', required resource identifiers are one of '[*, %s]'";

  private static final ConfiguredUser DEFAULT_USER =
      new ConfiguredUser(
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString());

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .withResetRecordingExporterTestWatcherMode(
              ResetRecordingExporterTestWatcherMode.BEFORE_ALL_TESTS_AND_AFTER_EACH_TEST)
          .withIdentitySetup(ResetRecordingExporterMode.NO_RESET_AFTER_IDENTITY_SETUP)
          .withAuthorizationsEnabled(true)
          .withSecurityConfig(cfg -> cfg.getInitialization().setUsers(List.of(DEFAULT_USER)))
          .withSecurityConfig(
              cfg -> {
                final var defaultRoles = new HashMap<>(cfg.getInitialization().getDefaultRoles());
                defaultRoles.put("admin", Map.of("users", List.of(DEFAULT_USER.getUsername())));
                cfg.getInitialization().setDefaultRoles(defaultRoles);
              });

  @Test
  public void shouldSuspendAsAdmin() {
    // given
    final var process = deployProcess();

    // when
    final var suspended =
        engine
            .processDefinitionSuspension()
            .withProcessDefinitionKey(process.getProcessDefinitionKey())
            .suspend(DEFAULT_USER.getUsername());

    // then
    assertThat(suspended.getIntent()).isEqualTo(ProcessDefinitionSuspensionIntent.SUSPENDED);
  }

  @Test
  public void shouldSuspendWithPermission() {
    // given
    final var process = deployProcess();
    final var user = createUser();
    grantPermission(user, process.getBpmnProcessId());

    // when
    final var suspended =
        engine
            .processDefinitionSuspension()
            .withProcessDefinitionKey(process.getProcessDefinitionKey())
            .suspend(user.getUsername());

    // then
    assertThat(suspended.getIntent()).isEqualTo(ProcessDefinitionSuspensionIntent.SUSPENDED);
  }

  @Test
  public void shouldResumeWithPermission() {
    // given
    final var process = deployProcess();
    final var user = createUser();
    grantPermission(user, process.getBpmnProcessId());

    // when
    final var resumed =
        engine
            .processDefinitionSuspension()
            .withBpmnProcessId(process.getBpmnProcessId())
            .resume(user.getUsername());

    // then
    assertThat(resumed.getIntent()).isEqualTo(ProcessDefinitionSuspensionIntent.RESUMED);
  }

  @Test
  public void shouldSuspendWithWildcardPermission() {
    // given
    final var process = deployProcess();
    final var user = createUser();
    grantWildcardPermission(user);

    // when
    final var suspended =
        engine
            .processDefinitionSuspension()
            .withBpmnProcessId(process.getBpmnProcessId())
            .withVersion(process.getVersion())
            .suspend(user.getUsername());

    // then
    assertThat(suspended.getIntent()).isEqualTo(ProcessDefinitionSuspensionIntent.SUSPENDED);
  }

  @Test
  public void shouldRejectSuspendByBpmnProcessIdWithoutPermission() {
    // given
    final var process = deployProcess();
    final var user = createUser();

    // when
    final var rejection =
        engine
            .processDefinitionSuspension()
            .withBpmnProcessId(process.getBpmnProcessId())
            .expectRejection()
            .suspend(user.getUsername());

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.FORBIDDEN)
        .hasRejectionReason(FORBIDDEN_MESSAGE.formatted(process.getBpmnProcessId()));
  }

  @Test
  public void shouldRejectResumeByBpmnProcessIdAndVersionWithoutPermission() {
    // given
    final var process = deployProcess();
    final var user = createUser();

    // when
    final var rejection =
        engine
            .processDefinitionSuspension()
            .withBpmnProcessId(process.getBpmnProcessId())
            .withVersion(process.getVersion())
            .expectRejection()
            .resume(user.getUsername());

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.FORBIDDEN)
        .hasRejectionReason(FORBIDDEN_MESSAGE.formatted(process.getBpmnProcessId()));
  }

  @Test
  public void shouldRejectWithProcessInstanceSuspensionPermissionOnly() {
    // given
    final var process = deployProcess();
    final var user = createUser();
    grantPermission(user, PermissionType.SUSPEND_PROCESS_INSTANCE, process.getBpmnProcessId());

    // when
    final var rejection =
        engine
            .processDefinitionSuspension()
            .withBpmnProcessId(process.getBpmnProcessId())
            .expectRejection()
            .suspend(user.getUsername());

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.FORBIDDEN)
        .hasRejectionReason(FORBIDDEN_MESSAGE.formatted(process.getBpmnProcessId()));
  }

  @Test
  public void shouldRejectWithPermissionForOtherProcess() {
    // given
    final var process = deployProcess();
    final var otherProcess = deployProcess();
    final var user = createUser();
    grantPermission(user, otherProcess.getBpmnProcessId());

    // when
    final var rejection =
        engine
            .processDefinitionSuspension()
            .withBpmnProcessId(process.getBpmnProcessId())
            .expectRejection()
            .suspend(user.getUsername());

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.FORBIDDEN)
        .hasRejectionReason(FORBIDDEN_MESSAGE.formatted(process.getBpmnProcessId()));
  }

  @Test
  public void shouldRejectUnknownBpmnProcessIdWithoutPermissionAsForbidden() {
    // given
    final var user = createUser();

    // when
    final var rejection =
        engine
            .processDefinitionSuspension()
            .withBpmnProcessId("unknown-" + UUID.randomUUID())
            .expectRejection()
            .resume(user.getUsername());

    // then - the permission is checked before the lookup, so the caller learns nothing about
    // whether the process definition exists
    Assertions.assertThat(rejection).hasRejectionType(RejectionType.FORBIDDEN);
  }

  @Test
  public void shouldRejectSuspendByKeyWithoutPermissionAsNotFound() {
    // given
    final var process = deployProcess();
    final var user = createUser();

    // when
    final var rejection =
        engine
            .processDefinitionSuspension()
            .withProcessDefinitionKey(process.getProcessDefinitionKey())
            .expectRejection()
            .suspend(user.getUsername());

    // then - same rejection as for an unknown key, so the caller can't tell them apart
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to suspend process definition with key '%d', but no such process definition was found"
                .formatted(process.getProcessDefinitionKey()));
  }

  @Test
  public void shouldRejectResumeByKeyWithoutPermissionAsNotFound() {
    // given
    final var process = deployProcess();
    final var user = createUser();

    // when
    final var rejection =
        engine
            .processDefinitionSuspension()
            .withProcessDefinitionKey(process.getProcessDefinitionKey())
            .expectRejection()
            .resume(user.getUsername());

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to resume process definition with key '%d', but no such process definition was found"
                .formatted(process.getProcessDefinitionKey()));
  }

  private ProcessMetadataValue deployProcess() {
    final var processId = "process-" + UUID.randomUUID();
    return engine
        .deployment()
        .withXmlResource(Bpmn.createExecutableProcess(processId).startEvent().endEvent().done())
        .deploy(DEFAULT_USER.getUsername())
        .getValue()
        .getProcessesMetadata()
        .getFirst();
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

  private void grantPermission(final UserRecordValue user, final String bpmnProcessId) {
    grantPermission(user, PermissionType.SUSPEND_PROCESS_DEFINITION, bpmnProcessId);
  }

  private void grantPermission(
      final UserRecordValue user, final PermissionType permission, final String bpmnProcessId) {
    engine
        .authorization()
        .newAuthorization()
        .withPermissions(permission)
        .withOwnerId(user.getUsername())
        .withOwnerType(AuthorizationOwnerType.USER)
        .withResourceType(AuthorizationResourceType.PROCESS_DEFINITION)
        .withResourceMatcher(AuthorizationResourceMatcher.ID)
        .withResourceId(bpmnProcessId)
        .create(DEFAULT_USER.getUsername());
  }

  private void grantWildcardPermission(final UserRecordValue user) {
    engine
        .authorization()
        .newAuthorization()
        .withPermissions(PermissionType.SUSPEND_PROCESS_DEFINITION)
        .withOwnerId(user.getUsername())
        .withOwnerType(AuthorizationOwnerType.USER)
        .withResourceType(AuthorizationResourceType.PROCESS_DEFINITION)
        .withResourceMatcher(AuthorizationResourceMatcher.ANY)
        .withResourceId("*")
        .create(DEFAULT_USER.getUsername());
  }
}
