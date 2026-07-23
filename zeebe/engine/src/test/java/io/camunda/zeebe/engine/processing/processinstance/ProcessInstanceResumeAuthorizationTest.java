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
import io.camunda.zeebe.engine.util.EngineRule.ResetRecordingExporterMode;
import io.camunda.zeebe.engine.util.EngineRule.ResetRecordingExporterTestWatcherMode;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class ProcessInstanceResumeAuthorizationTest {
  private static final String PROCESS_ID = "processId";
  private static final String TENANT = "custom-tenant";

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

  @Before
  public void before() {
    engine
        .deployment()
        .withXmlResource(
            "process.bpmn",
            Bpmn.createExecutableProcess(PROCESS_ID).startEvent().userTask().endEvent().done())
        .deploy(DEFAULT_USER.getUsername());
  }

  @Test
  public void shouldBeAuthorizedToResumeInstanceWithDefaultUser() {
    // given
    final var processInstanceKey = createProcessInstance();
    engine
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .suspend(DEFAULT_USER.getUsername());

    // when
    engine.processInstance().withInstanceKey(processInstanceKey).resume(DEFAULT_USER.getUsername());

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.RESUMED)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeAuthorizedToResumeInstanceWithUser() {
    // given
    final var processInstanceKey = createProcessInstance();
    final var user = createUser();
    addPermissionsToUser(
        user,
        AuthorizationResourceType.PROCESS_DEFINITION,
        PermissionType.SUSPEND_PROCESS_INSTANCE,
        AuthorizationResourceMatcher.ID,
        PROCESS_ID);
    engine.processInstance().withInstanceKey(processInstanceKey).suspend(user.getUsername());

    // when
    engine.processInstance().withInstanceKey(processInstanceKey).resume(user.getUsername());

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.RESUMED)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeUnauthorizedToResumeInstanceIfNoPermissions() {
    // given
    final var processInstanceKey = createProcessInstance();
    final var user = createUser();

    // when
    final var rejection =
        engine
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .expectResumeRejection()
            .resume(user.getUsername());

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.FORBIDDEN)
        .hasRejectionReason(
            "Insufficient permissions to perform operation 'SUSPEND_PROCESS_INSTANCE' on resource 'PROCESS_DEFINITION', required resource identifiers are one of '[*, %s]'"
                .formatted(PROCESS_ID));
  }

  @Test
  public void shouldEnrichRejectionWithTenantIdAndRootProcessInstanceKeyWhenUnauthorized() {
    // given
    engine
        .deployment()
        .withXmlResource(
            "tenant-process.bpmn",
            Bpmn.createExecutableProcess(PROCESS_ID).startEvent().userTask().endEvent().done())
        .withTenantId(TENANT)
        .deploy(DEFAULT_USER.getUsername());
    final var processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withTenantId(TENANT)
            .create(DEFAULT_USER.getUsername());
    final var user = createUser();

    // when
    final var rejection =
        engine
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .forAuthorizedTenants(TENANT)
            .expectResumeRejection()
            .resume(user.getUsername());

    // then
    Assertions.assertThat(rejection).hasRejectionType(RejectionType.FORBIDDEN);
    Assertions.assertThat(rejection.getValue()).hasTenantId(TENANT);
    assertThat(rejection.getValue().getRootProcessInstanceKey())
        .describedAs("a root process instance's own key is also its rootProcessInstanceKey")
        .isEqualTo(processInstanceKey);
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

  private long createProcessInstance() {
    return engine.processInstance().ofBpmnProcessId(PROCESS_ID).create(DEFAULT_USER.getUsername());
  }
}
