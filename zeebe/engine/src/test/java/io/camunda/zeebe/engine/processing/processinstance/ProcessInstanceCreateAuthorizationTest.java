/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.configuration.ConfiguredUser;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.UUID;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class ProcessInstanceCreateAuthorizationTest {
  private static final String PROCESS_ID = "processId";

  private static final ConfiguredUser DEFAULT_USER =
      new ConfiguredUser(
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString());

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition()
          .withoutAwaitingIdentitySetup()
          .withSecurityConfig(cfg -> cfg.getAuthorizations().setEnabled(true))
          .withSecurityConfig(cfg -> cfg.getInitialization().setUsers(List.of(DEFAULT_USER)));

  private static long defaultUserKey = -1L;
  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @BeforeClass
  public static void beforeAll() {
    defaultUserKey =
        RecordingExporter.userRecords(UserIntent.CREATED)
            .withUsername(DEFAULT_USER.getUsername())
            .getFirst()
            .getKey();

    ENGINE
        .deployment()
        .withXmlResource(
            "process.bpmn", Bpmn.createExecutableProcess(PROCESS_ID).startEvent().endEvent().done())
        .deploy(defaultUserKey);
  }

  @Test
  public void shouldBeAuthorizedToCreateInstanceWithDefaultUser() {
    // when
    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create(defaultUserKey);

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
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withResult().create(defaultUserKey);

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
    final var userKey = createUser();
    addPermissionsToUser(
        userKey,
        AuthorizationResourceType.PROCESS_DEFINITION,
        PermissionType.CREATE_PROCESS_INSTANCE,
        PROCESS_ID);

    // when
    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create(userKey);

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
    final var userKey = createUser();
    addPermissionsToUser(
        userKey,
        AuthorizationResourceType.PROCESS_DEFINITION,
        PermissionType.CREATE_PROCESS_INSTANCE,
        PROCESS_ID);

    // when
    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withResult().create(userKey);

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
    final var userKey = createUser();

    // when
    ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).expectRejection().create(userKey);

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
    final var userKey = createUser();

    // when
    ENGINE
        .processInstance()
        .ofBpmnProcessId(PROCESS_ID)
        .expectRejection()
        .withResult()
        .asyncCreate(userKey);

    // then
    Assertions.assertThat(
            RecordingExporter.processInstanceCreationRecords().onlyCommandRejections().getFirst())
        .hasRejectionType(RejectionType.FORBIDDEN)
        .hasRejectionReason(
            "Insufficient permissions to perform operation 'CREATE_PROCESS_INSTANCE' on resource 'PROCESS_DEFINITION', required resource identifiers are one of '[*, %s]'"
                .formatted(PROCESS_ID));
  }

  private static long createUser() {
    return ENGINE
        .user()
        .newUser(UUID.randomUUID().toString())
        .withPassword(UUID.randomUUID().toString())
        .withName(UUID.randomUUID().toString())
        .withEmail(UUID.randomUUID().toString())
        .create()
        .getKey();
  }

  private void addPermissionsToUser(
      final long userKey,
      final AuthorizationResourceType authorization,
      final PermissionType permissionType,
      final String... resourceIds) {
    ENGINE
        .authorization()
        .permission()
        .withOwnerKey(userKey)
        .withResourceType(authorization)
        .withPermission(permissionType, resourceIds)
        .add(defaultUserKey);
  }
}
