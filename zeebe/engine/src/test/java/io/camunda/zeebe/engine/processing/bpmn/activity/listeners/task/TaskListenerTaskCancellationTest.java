/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.activity.listeners.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskListenerEventType;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.UUID;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class TaskListenerTaskCancellationTest {
  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();
  private final TaskListenerTestHelper helper = new TaskListenerTestHelper(ENGINE);

  private String listenerType;

  @Before
  public void setup() {
    listenerType = "my_listener_" + UUID.randomUUID();
  }

  @Test
  public void shouldCancelUserTaskWithAssignmentListener() {
    // given
    final long processInstanceKey =
        helper.createProcessInstance(helper.createProcessWithAssigningTaskListeners(listenerType));

    ENGINE.userTask().ofInstance(processInstanceKey).withAssignee("new_assignee").assign();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    assertThat(
            RecordingExporter.userTaskRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(r -> r.getIntent() == UserTaskIntent.CANCELED))
        .extracting(
            Record::getIntent,
            r -> r.getValue().getAssignee(),
            r -> r.getValue().getChangedAttributes())
        .containsSubsequence(
            tuple(UserTaskIntent.ASSIGNING, "new_assignee", List.of(UserTaskRecord.ASSIGNEE)),
            tuple(UserTaskIntent.CANCELING, "", List.of()),
            tuple(UserTaskIntent.CANCELED, "", List.of()));
  }

  @Test
  public void shouldCancelUserTaskWithUpdateListener() {
    // given
    final long processInstanceKey =
        helper.createProcessInstance(
            helper.createUserTaskWithTaskListeners(
                ZeebeTaskListenerEventType.updating, listenerType));

    ENGINE
        .userTask()
        .ofInstance(processInstanceKey)
        .withCandidateUsers("frodo", "samwise")
        .update();

    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    assertThat(
            RecordingExporter.userTaskRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(r -> r.getIntent() == UserTaskIntent.CANCELED))
        .extracting(
            Record::getIntent,
            r -> r.getValue().getCandidateUsersList(),
            r -> r.getValue().getChangedAttributes())
        .containsSubsequence(
            tuple(
                UserTaskIntent.UPDATING,
                List.of("frodo", "samwise"),
                List.of(UserTaskRecord.CANDIDATE_USERS)),
            tuple(UserTaskIntent.CANCELING, List.of(), List.of()),
            tuple(UserTaskIntent.CANCELED, List.of(), List.of()));
  }

  @Test
  public void shouldCancelUserTaskWithCompleteListener() {
    // given
    final long processInstanceKey =
        helper.createProcessInstance(helper.createProcessWithCompletingTaskListeners(listenerType));

    ENGINE.userTask().ofInstance(processInstanceKey).complete();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    helper.assertUserTaskIntentsSequence(
        processInstanceKey,
        UserTaskIntent.COMPLETING,
        UserTaskIntent.CANCELING,
        UserTaskIntent.CANCELED);
  }
}
