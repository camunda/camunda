/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.activity.listeners.task;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Objects;
import java.util.UUID;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

/** Tests verifying that a task listener may access user task data. */
public class TaskListenerDataAccessTest {
  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();
  private final TaskListenerTestHelper helper = new TaskListenerTestHelper(ENGINE);

  private String listenerType;

  @Before
  public void setup() {
    listenerType = "my_listener_" + UUID.randomUUID();
  }

  @Test
  public void shouldIncludeConfiguredUserTaskDataInCompleteTaskListenerJobHeaders() {
    final var form = helper.deployForm("/form/test-form-1.form");
    final long processInstanceKey =
        helper.createProcessInstance(
            helper.createProcessWithZeebeUserTask(
                t ->
                    t.zeebeAssignee("admin")
                        .zeebeCandidateUsers("user_A, user_B")
                        .zeebeCandidateGroups("group_A, group_C, group_F")
                        .zeebeFormId("Form_0w7r08e")
                        .zeebeDueDate("2095-09-18T10:31:10+02:00")
                        .zeebeTaskPriority("88")
                        .zeebeTaskListener(l -> l.completing().type(listenerType))));

    // when
    final var userTaskCommand = ENGINE.userTask().ofInstance(processInstanceKey).complete();

    // then
    final var activatedListenerJob = helper.activateJob(processInstanceKey, listenerType);

    assertThat(activatedListenerJob.getCustomHeaders())
        .containsOnly(
            entry(
                Protocol.USER_TASK_CANDIDATE_GROUPS_HEADER_NAME,
                "[\"group_A\",\"group_C\",\"group_F\"]"),
            entry(Protocol.USER_TASK_CANDIDATE_USERS_HEADER_NAME, "[\"user_A\",\"user_B\"]"),
            entry(Protocol.USER_TASK_ASSIGNEE_HEADER_NAME, "admin"),
            entry(Protocol.USER_TASK_DUE_DATE_HEADER_NAME, "2095-09-18T10:31:10+02:00"),
            entry(Protocol.USER_TASK_FORM_KEY_HEADER_NAME, Objects.toString(form.getFormKey())),
            entry(Protocol.USER_TASK_KEY_HEADER_NAME, String.valueOf(userTaskCommand.getKey())),
            entry(Protocol.USER_TASK_PRIORITY_HEADER_NAME, "88"),
            entry(Protocol.USER_TASK_ACTION_HEADER_NAME, "complete"));
    helper.completeJobs(processInstanceKey, listenerType);
  }

  @Test
  public void shouldUseUpdatedUserTaskDataInCompleteTaskListenerJobHeadersAfterTaskUpdate() {
    final long processInstanceKey =
        helper.createProcessInstance(
            helper.createProcessWithZeebeUserTask(
                t ->
                    t.zeebeAssignee("admin")
                        .zeebeCandidateUsers("user_A, user_B")
                        .zeebeCandidateGroups("group_A, group_C, group_F")
                        .zeebeDueDate("2085-09-21T11:22:33+02:00")
                        .zeebeFollowUpDate("2095-09-21T11:22:33+02:00")
                        .zeebeTaskListener(l -> l.completing().type(listenerType))));

    // when
    ENGINE
        .userTask()
        .ofInstance(processInstanceKey)
        .withCandidateGroups("group_J", "group_R")
        .withCandidateUsers("user_T")
        .withDueDate("2087-09-21T11:22:33+02:00")
        .withFollowUpDate("2097-09-21T11:22:33+02:00")
        .withPriority(42)
        .update();
    final var userTaskCommand = ENGINE.userTask().ofInstance(processInstanceKey).complete();

    // then
    final var activatedListenerJob = helper.activateJob(processInstanceKey, listenerType);
    assertThat(activatedListenerJob.getCustomHeaders())
        .containsOnly(
            entry(Protocol.USER_TASK_CANDIDATE_GROUPS_HEADER_NAME, "[\"group_J\",\"group_R\"]"),
            entry(Protocol.USER_TASK_CANDIDATE_USERS_HEADER_NAME, "[\"user_T\"]"),
            entry(Protocol.USER_TASK_ASSIGNEE_HEADER_NAME, "admin"),
            entry(Protocol.USER_TASK_DUE_DATE_HEADER_NAME, "2087-09-21T11:22:33+02:00"),
            entry(Protocol.USER_TASK_FOLLOW_UP_DATE_HEADER_NAME, "2097-09-21T11:22:33+02:00"),
            entry(Protocol.USER_TASK_KEY_HEADER_NAME, String.valueOf(userTaskCommand.getKey())),
            entry(Protocol.USER_TASK_PRIORITY_HEADER_NAME, "42"),
            entry(Protocol.USER_TASK_ACTION_HEADER_NAME, "complete"));
    helper.completeJobs(processInstanceKey, listenerType);
  }

  @Test
  public void
      shouldNotIncludeEmptyOrClearedUserTaskPropertiesInCompletingListenerHeadersAfterTaskWasUpdated() {
    // given: a process instance with a user task configured with an initial assignee, candidate
    // users/groups, due/follow-up dates, and a `completing` listener
    final long processInstanceKey =
        helper.createProcessInstance(
            helper.createProcessWithZeebeUserTask(
                userTask ->
                    userTask
                        .zeebeAssignee("admin")
                        .zeebeCandidateUsers("user_A, user_B")
                        .zeebeCandidateGroups("group_A, group_C, group_F")
                        .zeebeDueDate("2085-09-21T11:22:33+02:00")
                        .zeebeFollowUpDate("2095-09-21T11:22:33+02:00")
                        .zeebeTaskListener(listener -> listener.completing().type(listenerType))));

    // when: updating the user task with the specified changes
    ENGINE
        .userTask()
        .ofInstance(processInstanceKey)
        // Clear candidate groups and users, due date, and follow-up date
        .withCandidateGroups()
        .withCandidateUsers()
        .withDueDate("")
        .withFollowUpDate("")
        // Update priority
        .withPriority(1)
        .update();

    // and: completing the user task
    final var userTaskCommand = ENGINE.userTask().ofInstance(processInstanceKey).complete();

    // then: validate the headers of the completing listener job triggered after the task update
    final var activatedListenerJob = helper.activateJob(processInstanceKey, listenerType);

    assertThat(activatedListenerJob.getCustomHeaders())
        .describedAs(
            "Headers should include only the configured, updated or automatically set user task properties")
        .containsOnly(
            // Assignee remains unchanged
            entry(Protocol.USER_TASK_ASSIGNEE_HEADER_NAME, "admin"),
            // Task key is always included
            entry(Protocol.USER_TASK_KEY_HEADER_NAME, String.valueOf(userTaskCommand.getKey())),
            // Default action value for the completing operation
            entry(Protocol.USER_TASK_ACTION_HEADER_NAME, "complete"),
            // Updated priority
            entry(Protocol.USER_TASK_PRIORITY_HEADER_NAME, "1"))
        .describedAs(
            "Headers should not include not configured or cleared properties such as candidate groups, candidate users, due date, and follow-up date")
        .doesNotContainKeys(
            Protocol.USER_TASK_CANDIDATE_GROUPS_HEADER_NAME,
            Protocol.USER_TASK_CANDIDATE_USERS_HEADER_NAME,
            Protocol.USER_TASK_DUE_DATE_HEADER_NAME,
            Protocol.USER_TASK_FOLLOW_UP_DATE_HEADER_NAME);
  }

  @Test
  public void
      shouldNotIncludeEmptyOrClearedUserTaskPropertiesInAssigningListenerHeadersAfterTriggeringTaskUnassignment() {
    // given: a process instance with a user task configured with an initial assignee and an
    // `assigning` listener
    final long processInstanceKey =
        helper.createProcessInstance(
            helper.createProcessWithZeebeUserTask(
                userTask ->
                    userTask
                        .zeebeAssignee("initial_assignee")
                        .zeebeTaskListener(listener -> listener.assigning().type(listenerType))));

    final var createdUserTaskRecord =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final var assigningListenerJob = helper.activateJob(processInstanceKey, listenerType);

    // assert the headers of the assigning listener job triggered after the user task creation
    final var userTaskKey = String.valueOf(createdUserTaskRecord.getKey());
    assertThat(assigningListenerJob.getCustomHeaders())
        .describedAs("Headers should not contain empty or non-configured properties")
        .doesNotContainKeys(
            Protocol.USER_TASK_ACTION_HEADER_NAME,
            Protocol.USER_TASK_CANDIDATE_GROUPS_HEADER_NAME,
            Protocol.USER_TASK_CANDIDATE_USERS_HEADER_NAME,
            Protocol.USER_TASK_DUE_DATE_HEADER_NAME,
            Protocol.USER_TASK_FOLLOW_UP_DATE_HEADER_NAME)
        .describedAs(
            "Headers should include only configured, default or automatically set user task properties")
        .containsOnly(
            // Task key is always included
            entry(Protocol.USER_TASK_KEY_HEADER_NAME, userTaskKey),
            // Assignee should match the initial value
            entry(Protocol.USER_TASK_ASSIGNEE_HEADER_NAME, "initial_assignee"),
            // Default priority is propagated
            entry(Protocol.USER_TASK_PRIORITY_HEADER_NAME, "50"),
            // Value for `assignee` was explicitly set on task creation
            entry(Protocol.USER_TASK_CHANGED_ATTRIBUTES_HEADER_NAME, "[\"assignee\"]"));

    // Complete the assigning listener job triggered after the user task creation
    helper.completeJobs(processInstanceKey, listenerType);

    // when: unassigning the user task
    ENGINE.userTask().ofInstance(processInstanceKey).unassign();

    // then
    final var unassigningListenerJob = helper.activateJob(processInstanceKey, listenerType);
    assertThat(unassigningListenerJob.getCustomHeaders())
        .describedAs(
            "Headers should not include the 'assignee' property, as it is cleared during unassignment")
        .doesNotContainKeys(Protocol.USER_TASK_ASSIGNEE_HEADER_NAME)
        .describedAs("Headers should not contain other empty or non-configured properties")
        .doesNotContainKeys(
            Protocol.USER_TASK_CANDIDATE_GROUPS_HEADER_NAME,
            Protocol.USER_TASK_CANDIDATE_USERS_HEADER_NAME,
            Protocol.USER_TASK_DUE_DATE_HEADER_NAME,
            Protocol.USER_TASK_FOLLOW_UP_DATE_HEADER_NAME)
        .describedAs(
            "Headers should include only configured, default or automatically set user task properties")
        .containsOnly(
            // Task key is always included
            entry(Protocol.USER_TASK_KEY_HEADER_NAME, userTaskKey),
            // Priority remains unchanged
            entry(Protocol.USER_TASK_PRIORITY_HEADER_NAME, "50"),
            // Action should reflect the unassign operation
            entry(Protocol.USER_TASK_ACTION_HEADER_NAME, "unassign"),
            // `assignee` was cleared during unassignment, marking it as a changed attribute.
            entry(Protocol.USER_TASK_CHANGED_ATTRIBUTES_HEADER_NAME, "[\"assignee\"]"));
  }
}
