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
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.impl.record.value.job.JobResult;
import io.camunda.zeebe.protocol.impl.record.value.job.JobResultCorrections;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
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

public class UserTaskAssignAuthorizationTest {
  public static final String USER_TASK_ID = "userTask";
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
              cfg ->
                  cfg.getInitialization()
                      .getDefaultRoles()
                      .put("admin", Map.of("users", List.of(DEFAULT_USER.getUsername()))));

  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Before
  public void before() {
    engine
        .deployment()
        .withXmlResource(
            "process.bpmn",
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .userTask(USER_TASK_ID)
                .zeebeUserTask()
                .endEvent()
                .done())
        .deploy(DEFAULT_USER.getUsername());
  }

  @Test
  public void shouldBeAuthorizedToAssignUserTaskWithDefaultUser() {
    // given
    final var processInstanceKey = createProcessInstance();

    // when
    engine
        .userTask()
        .ofInstance(processInstanceKey)
        .withAssignee("assignee")
        .assign(DEFAULT_USER.getUsername());

    // then
    assertThat(
            RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldNotDuplicateAssigneePermissionsOnReassignmentToSameUser() {
    // given
    final var processInstanceKey = createProcessInstance();
    final var userTaskKey =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(USER_TASK_ID)
            .getFirst()
            .getKey();

    // when
    engine
        .userTask()
        .ofInstance(processInstanceKey)
        .withAssignee("my_assignee")
        .assign(DEFAULT_USER.getUsername());

    // then
    assertThat(
            RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .isTrue();

    final var createUserTaskPermissionsForAssigneeAuthRecord =
        RecordingExporter.authorizationRecords(AuthorizationIntent.CREATED)
            .withOwnerId("my_assignee")
            .withResourceType(AuthorizationResourceType.USER_TASK)
            .getFirst()
            .getValue();

    Assertions.assertThat(createUserTaskPermissionsForAssigneeAuthRecord)
        .hasOwnerType(AuthorizationOwnerType.USER)
        .hasResourceId(Long.toString(userTaskKey))
        .hasOnlyPermissionTypes(PermissionType.READ, PermissionType.UPDATE);

    // unassign the task
    engine.userTask().ofInstance(processInstanceKey).unassign(DEFAULT_USER.getUsername());

    // reassign to the same user
    final var userTaskReassigningRecord =
        engine
            .userTask()
            .ofInstance(processInstanceKey)
            .withAssignee("my_assignee")
            .assign(DEFAULT_USER.getUsername());

    final var userTaskReassignedRecord =
        RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED)
            .withProcessInstanceKey(processInstanceKey)
            .filter(r -> r.getPosition() > userTaskReassigningRecord.getPosition())
            .getFirst();
    // user task is reassigned to the same user
    Assertions.assertThat(userTaskReassignedRecord)
        .hasIntent(UserTaskIntent.ASSIGNED)
        .hasRecordType(RecordType.EVENT);

    Assertions.assertThat(userTaskReassignedRecord.getValue())
        .hasAssignee("my_assignee")
        .hasOnlyChangedAttributes(UserTaskRecord.ASSIGNEE);

    // no new authorization record is created
    assertThat(
            RecordingExporter.authorizationRecords(AuthorizationIntent.CREATED)
                .withOwnerId("my_assignee")
                .limit(r -> r.getPosition() < userTaskReassignedRecord.getPosition()))
        .hasSize(1)
        .first()
        .satisfies(
            r ->
                Assertions.assertThat(r.getValue())
                    .isEqualTo(createUserTaskPermissionsForAssigneeAuthRecord));
  }

  @Test
  public void shouldAddUserTaskPermissionsToCorrectedAssigneeOnTaskUpdate() {
    // given
    final String processId = PROCESS_ID + "_with_assigning_listener";
    deployProcess(
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .userTask(
                USER_TASK_ID,
                t -> t.zeebeTaskListener(l -> l.assigning().type("correct_assignee_job")))
            .zeebeUserTask()
            .endEvent()
            .done());
    final var pik = createProcessInstance(processId);

    // when
    final var assigningRecord =
        engine.userTask().ofInstance(pik).withAssignee("sam").assign(DEFAULT_USER.getUsername());
    final long userTaskKey = assigningRecord.getValue().getUserTaskKey();

    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(pik)
        .withType("correct_assignee_job")
        .await();

    engine
        .job()
        .ofInstance(pik)
        .withType("correct_assignee_job")
        .withResult(
            new JobResult()
                .setCorrections(new JobResultCorrections().setAssignee("frodo"))
                .setCorrectedAttributes(List.of("assignee")))
        .complete();

    // then
    Assertions.assertThat(
            RecordingExporter.authorizationRecords(AuthorizationIntent.CREATED)
                .withOwnerId("frodo")
                .withResourceType(AuthorizationResourceType.USER_TASK)
                .getFirst()
                .getValue())
        .hasOwnerType(AuthorizationOwnerType.USER)
        .hasResourceId(Long.toString(userTaskKey))
        .hasOnlyPermissionTypes(PermissionType.READ, PermissionType.UPDATE);
  }

  @Test
  public void taskAssigneeShouldBeAuthorizedToUnassignHimself() {
    // given
    createUser("legolas");
    final String processId = PROCESS_ID + "_" + UUID.randomUUID();
    deployProcess(
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .userTask(USER_TASK_ID, t -> t.zeebeAssignee("legolas"))
            .zeebeUserTask()
            .endEvent()
            .done());
    final var pik = createProcessInstance(processId);
    final var userTaskKey =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(pik)
            .withElementId(USER_TASK_ID)
            .getFirst()
            .getKey();

    // ensure that the assignee has permissions on the task
    final var createUserTaskPermissionsForAssigneeAuthRecord =
        RecordingExporter.authorizationRecords(AuthorizationIntent.CREATED)
            .withOwnerId("legolas")
            .withResourceType(AuthorizationResourceType.USER_TASK)
            .getFirst()
            .getValue();

    Assertions.assertThat(createUserTaskPermissionsForAssigneeAuthRecord)
        .hasOwnerType(AuthorizationOwnerType.USER)
        .hasResourceId(Long.toString(userTaskKey))
        .hasOnlyPermissionTypes(PermissionType.READ, PermissionType.UPDATE);

    // when
    final var userTaskUnassigningRecord = engine.userTask().ofInstance(pik).unassign("legolas");

    // then
    assertThat(
            RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED)
                .withProcessInstanceKey(pik)
                .filter(r -> r.getPosition() > userTaskUnassigningRecord.getPosition())
                .exists())
        .as("Expected USER_TASK.ASSIGNED event after the unassign operation")
        .isTrue();
  }

  @Test
  public void shouldBeAuthorizedToAssignUserTaskWithUser() {
    // given
    final var processInstanceKey = createProcessInstance();
    final var user = createUser();
    addPermissionsToUser(
        user,
        AuthorizationResourceType.PROCESS_DEFINITION,
        PermissionType.UPDATE_USER_TASK,
        AuthorizationResourceMatcher.ID,
        PROCESS_ID);

    // when
    engine
        .userTask()
        .ofInstance(processInstanceKey)
        .withAssignee("assignee")
        .assign(user.getUsername());

    // then
    assertThat(
            RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldBeUnauthorizedToAssignUserTaskIfNoPermissions() {
    // given
    final var processInstanceKey = createProcessInstance();
    final var user = createUser();

    // when
    final var rejection =
        engine
            .userTask()
            .ofInstance(processInstanceKey)
            .withAssignee("assignee")
            .expectRejection()
            .assign(user.getUsername());

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.FORBIDDEN)
        .hasRejectionReason(
            "Insufficient permissions to perform operation 'UPDATE_USER_TASK' on resource 'PROCESS_DEFINITION', required resource identifiers are one of '[*, %s]'"
                .formatted(PROCESS_ID));
  }

  private UserRecordValue createUser() {
    return createUser(UUID.randomUUID().toString());
  }

  private UserRecordValue createUser(final String username) {
    return engine
        .user()
        .newUser(username)
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
    return createProcessInstance(PROCESS_ID);
  }

  private long createProcessInstance(final String processId) {
    return engine.processInstance().ofBpmnProcessId(processId).create(DEFAULT_USER.getUsername());
  }

  private void deployProcess(final BpmnModelInstance modelInstance) {
    engine
        .deployment()
        .withXmlResource("process.bpmn", modelInstance)
        .deploy(DEFAULT_USER.getUsername());
  }
}
