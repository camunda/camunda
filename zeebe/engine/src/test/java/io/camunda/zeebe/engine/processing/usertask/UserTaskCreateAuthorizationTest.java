/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.usertask;

import io.camunda.security.configuration.ConfiguredUser;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.impl.record.value.job.JobResult;
import io.camunda.zeebe.protocol.impl.record.value.job.JobResultCorrections;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class UserTaskCreateAuthorizationTest {

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

  @Test
  public void shouldAddUserTaskPermissionsToCorrectedAssigneeOnTaskCreation() {
    // given
    final BpmnModelInstance userTaskWithoutAssignee =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .userTask(
                USER_TASK_ID,
                t -> t.zeebeTaskListener(l -> l.creating().type("correct_assignee_job")))
            .zeebeUserTask()
            .endEvent()
            .done();
    deployProcess(userTaskWithoutAssignee);

    // when
    final var pik = createProcessInstance();
    final long userTaskKey =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATING)
            .withProcessInstanceKey(pik)
            .getFirst()
            .getValue()
            .getUserTaskKey();
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
                .setCorrections(new JobResultCorrections().setAssignee("new_assignee"))
                .setCorrectedAttributes(List.of("assignee")))
        .complete();

    // then
    Assertions.assertThat(
            RecordingExporter.authorizationRecords(AuthorizationIntent.CREATED)
                .withOwnerId("new_assignee")
                .withResourceType(AuthorizationResourceType.USER_TASK)
                .getFirst()
                .getValue())
        .hasOwnerType(AuthorizationOwnerType.USER)
        .hasResourceId(Long.toString(userTaskKey))
        .hasOnlyPermissionTypes(PermissionType.READ, PermissionType.UPDATE);
  }

  @Test
  public void shouldAddUserTaskPermissionsToInitialAssignee() {
    // given
    final BpmnModelInstance userTaskWitInitialAssignee =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .userTask(USER_TASK_ID, t -> t.zeebeAssignee("initial_assignee"))
            .zeebeUserTask()
            .endEvent()
            .done();
    deployProcess(userTaskWitInitialAssignee);

    // when
    final var pik = createProcessInstance();
    final long userTaskKey =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(pik)
            .getFirst()
            .getValue()
            .getUserTaskKey();

    // then
    Assertions.assertThat(
            RecordingExporter.authorizationRecords(AuthorizationIntent.CREATED)
                .withOwnerId("initial_assignee")
                .withResourceType(AuthorizationResourceType.USER_TASK)
                .getFirst()
                .getValue())
        .hasOwnerType(AuthorizationOwnerType.USER)
        .hasResourceId(Long.toString(userTaskKey))
        .hasOnlyPermissionTypes(PermissionType.READ, PermissionType.UPDATE);
  }

  private long createProcessInstance() {
    return engine.processInstance().ofBpmnProcessId(PROCESS_ID).create(DEFAULT_USER.getUsername());
  }

  private void deployProcess(final BpmnModelInstance modelInstance) {
    engine
        .deployment()
        .withXmlResource("process.bpmn", modelInstance)
        .deploy(DEFAULT_USER.getUsername());
  }
}
