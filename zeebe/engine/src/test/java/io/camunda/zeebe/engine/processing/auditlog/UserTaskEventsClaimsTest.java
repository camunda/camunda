/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.auditlog;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.configuration.ConfiguredUser;
import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableDocumentUpdateSemantic;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class UserTaskEventsClaimsTest {

  private static final String PROCESS_ID = "process";
  private static final String USER_TASK_ID = "userTask";

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
  public void deployProcess() {
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
  public void shouldIncludeClaimsInUserTaskAssignedEvents() {
    // given
    final var processInstanceKey = createProcessInstance();
    final var userTaskKey = findUserTaskKey(processInstanceKey);

    // when
    engine
        .userTask()
        .withKey(userTaskKey)
        .withAssignee("testAssignee")
        .assign(DEFAULT_USER.getUsername());

    // then
    final var record =
        RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED)
            .withRecordKey(userTaskKey)
            .findFirst();
    assertAuthorizationClaims(record);
  }

  @Test
  public void shouldIncludeClaimsInUserTaskUpdatedEvents() {
    // given
    final var processInstanceKey = createProcessInstance();
    final var userTaskKey = findUserTaskKey(processInstanceKey);

    // when
    engine.userTask().withKey(userTaskKey).withPriority(99).update(DEFAULT_USER.getUsername());

    // then
    final var record =
        RecordingExporter.userTaskRecords(UserTaskIntent.UPDATED)
            .withRecordKey(userTaskKey)
            .findFirst();
    assertAuthorizationClaims(record);
  }

  @Test
  public void shouldIncludeClaimsInUserTaskCompletedEvents() {
    // given
    final var processInstanceKey = createProcessInstance();
    final var userTaskKey = findUserTaskKey(processInstanceKey);

    // when
    engine.userTask().withKey(userTaskKey).complete(DEFAULT_USER.getUsername());

    // then
    final var record =
        RecordingExporter.userTaskRecords(UserTaskIntent.COMPLETED)
            .withRecordKey(userTaskKey)
            .findFirst();
    assertAuthorizationClaims(record);
  }

  @Test
  public void shouldIncludeClaimsInUserTaskClaimEvents() {
    // given
    final var processInstanceKey = createProcessInstance();
    final var userTaskKey = findUserTaskKey(processInstanceKey);

    // when
    engine
        .userTask()
        .withKey(userTaskKey)
        .withAssignee(DEFAULT_USER.getUsername())
        .claim(DEFAULT_USER.getUsername());

    // then
    final var record =
        RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED)
            .withRecordKey(userTaskKey)
            .findFirst();
    assertAuthorizationClaims(record);
  }

  @Test
  public void shouldIncludeClaimsInUserTaskUnassignEvents() {
    // given
    final var processInstanceKey = createProcessInstance();
    final var userTaskKey = findUserTaskKey(processInstanceKey);

    // Assign first
    engine
        .userTask()
        .withKey(userTaskKey)
        .withAssignee("testAssignee")
        .assign(DEFAULT_USER.getUsername());

    // when - unassign
    engine.userTask().withKey(userTaskKey).unassign(DEFAULT_USER.getUsername());

    // then
    final var record =
        RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED)
            .withRecordKey(userTaskKey)
            .skip(1) // Skip the initial assign event
            .findFirst();
    assertAuthorizationClaims(record);
  }

  @Test
  public void shouldIncludeClaimsInUserTaskUpdatedEventsWhenUpdatingVariables() {
    // given
    final var processInstanceKey = createProcessInstance();
    final var userTaskRecord = findUserTask(processInstanceKey);
    final var userTaskKey = userTaskRecord.getKey();
    final var elementInstanceKey = userTaskRecord.getValue().getElementInstanceKey();
    final Map<String, Object> variables = Map.of("testVar", "testValue", "number", 42);

    // when - update variables using VariableDocumentUpdateProcessor
    engine
        .variables()
        .ofScope(elementInstanceKey)
        .withDocument(variables)
        .withUpdateSemantic(VariableDocumentUpdateSemantic.LOCAL)
        .update(DEFAULT_USER.getUsername());

    // then - verify UPDATED event is generated for the user task
    final var record =
        RecordingExporter.userTaskRecords(UserTaskIntent.UPDATED)
            .withRecordKey(userTaskKey)
            .findFirst();
    assertAuthorizationClaims(record);
  }

  private long createProcessInstance() {
    return engine.processInstance().ofBpmnProcessId(PROCESS_ID).create(DEFAULT_USER.getUsername());
  }

  private long findUserTaskKey(final long processInstanceKey) {
    return findUserTask(processInstanceKey).getKey();
  }

  private Record<UserTaskRecordValue> findUserTask(final long processInstanceKey) {
    return RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .getFirst();
  }

  private void assertAuthorizationClaims(final java.util.Optional<?> record) {
    assertThat(record).isPresent();
    assertThat(((io.camunda.zeebe.protocol.record.Record<?>) record.get()).getAuthorizations())
        .containsEntry(Authorization.AUTHORIZED_USERNAME, DEFAULT_USER.getUsername());
  }
}
