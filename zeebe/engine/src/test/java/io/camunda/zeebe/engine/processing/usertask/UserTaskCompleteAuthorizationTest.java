/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.usertask;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.configuration.ConfiguredUser;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
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

public class UserTaskCompleteAuthorizationTest {

  public static final String USER_TASK_ID = "userTask";
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
            "process.bpmn",
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .userTask(USER_TASK_ID)
                .zeebeUserTask()
                .endEvent()
                .done())
        .deploy(defaultUserKey);
  }

  @Test
  public void shouldBeAuthorizedToCompleteUserTaskWithDefaultUser() {
    // given
    final var processInstanceKey = createProcessInstance();

    // when
    ENGINE
        .userTask()
        .ofInstance(processInstanceKey)
        .withAssignee("assignee")
        .complete(defaultUserKey);

    // then
    assertThat(
            RecordingExporter.userTaskRecords(UserTaskIntent.COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeAuthorizedToCompleteUserTaskWithUser() {
    // given
    final var processInstanceKey = createProcessInstance();
    final var userKey = createUser();
    addPermissionsToUser(
        userKey,
        AuthorizationResourceType.PROCESS_DEFINITION,
        PermissionType.UPDATE_USER_TASK,
        PROCESS_ID);

    // when
    ENGINE.userTask().ofInstance(processInstanceKey).withAssignee("assignee").complete(userKey);

    // then
    assertThat(
            RecordingExporter.userTaskRecords(UserTaskIntent.COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeUnauthorizedToCompleteUserTaskIfNoPermissions() {
    // given
    final var processInstanceKey = createProcessInstance();
    final var userKey = createUser();

    // when
    final var rejection =
        ENGINE
            .userTask()
            .ofInstance(processInstanceKey)
            .withAssignee("assignee")
            .expectRejection()
            .complete(userKey);

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.FORBIDDEN)
        .hasRejectionReason(
            "Insufficient permissions to perform operation 'UPDATE_USER_TASK' on resource 'PROCESS_DEFINITION', required resource identifiers are one of '[*, %s]'"
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

  private long createProcessInstance() {
    return ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create(defaultUserKey);
  }
}
