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
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.client.UserTaskClient;
import io.camunda.zeebe.model.bpmn.builder.UserTaskBuilder;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskListenerEventType;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.job.JobResult;
import io.camunda.zeebe.protocol.impl.record.value.job.JobResultCorrections;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

/** Tests verifying that a task listener may correct the task's data. */
public class TaskListenerCorrectionsTest {
  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final List<String> ALL_CORRECTABLE_ATTRIBUTES =
      List.of(
          UserTaskRecord.ASSIGNEE,
          UserTaskRecord.CANDIDATE_GROUPS,
          UserTaskRecord.CANDIDATE_USERS,
          UserTaskRecord.DUE_DATE,
          UserTaskRecord.FOLLOW_UP_DATE,
          UserTaskRecord.PRIORITY);

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();
  private final TaskListenerTestHelper helper = new TaskListenerTestHelper(ENGINE);

  private String listenerType;

  @Before
  public void setup() {
    listenerType = "my_listener_" + UUID.randomUUID();
  }

  @Test
  public void
      shouldAppendUserTaskCorrectedWhenAssigningOnCreationTaskListenerCompletesWithCorrections() {
    testAppendUserTaskCorrectedWhenTaskListenerCompletesWithCorrections(
        ZeebeTaskListenerEventType.assigning,
        u -> u.zeebeAssignee("initial_assignee"),
        userTask -> {},
        "");
  }

  @Test
  public void shouldAppendUserTaskCorrectedWhenAssigningTaskListenerCompletesWithCorrections() {
    testAppendUserTaskCorrectedWhenTaskListenerCompletesWithCorrections(
        ZeebeTaskListenerEventType.assigning,
        u -> u,
        userTask -> userTask.withAssignee("initial_assignee").assign(),
        "assign");
  }

  @Test
  public void shouldAppendUserTaskCorrectedWhenClaimingTaskListenerCompletesWithCorrections() {
    testAppendUserTaskCorrectedWhenTaskListenerCompletesWithCorrections(
        ZeebeTaskListenerEventType.assigning,
        u -> u,
        userTask -> userTask.withAssignee("initial_assignee").claim(),
        "claim");
  }

  @Test
  public void shouldAppendUserTaskCorrectedWhenUpdatingTaskListenerCompletesWithCorrections() {
    testAppendUserTaskCorrectedWhenTaskListenerCompletesWithCorrections(
        ZeebeTaskListenerEventType.updating, u -> u, UserTaskClient::update, "update");
  }

  @Test
  public void shouldAppendUserTaskCorrectedWhenCompletingTaskListenerCompletesWithCorrections() {
    testAppendUserTaskCorrectedWhenTaskListenerCompletesWithCorrections(
        ZeebeTaskListenerEventType.completing, u -> u, UserTaskClient::complete, "complete");
  }

  private void testAppendUserTaskCorrectedWhenTaskListenerCompletesWithCorrections(
      final ZeebeTaskListenerEventType eventType,
      final UnaryOperator<UserTaskBuilder> userTaskBuilder,
      final Consumer<UserTaskClient> userTaskAction,
      final String expectedAction) {
    // given
    final long processInstanceKey =
        helper.createProcessInstance(
            helper.createProcessWithZeebeUserTask(
                t ->
                    userTaskBuilder
                        .apply(t)
                        .zeebeTaskListener(l -> l.eventType(eventType).type(listenerType))
                        .zeebeTaskListener(l -> l.eventType(eventType).type(listenerType + "_2"))));

    final var userTaskRecord =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    final var userTask = userTaskRecord.getValue();
    userTaskAction.accept(ENGINE.userTask().withKey(userTaskRecord.getKey()));

    // when
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(listenerType)
        .withResult(
            new JobResult()
                .setCorrections(
                    new JobResultCorrections()
                        .setAssignee("new_assignee")
                        .setCandidateUsersList(List.of("new_candidate_user"))
                        .setCandidateGroupsList(List.of("new_candidate_group"))
                        .setDueDate("new_due_date")
                        .setFollowUpDate("new_follow_up_date")
                        .setPriority(100))
                .setCorrectedAttributes(
                    List.of(
                        "assignee",
                        "candidateUsersList",
                        "candidateGroupsList",
                        "dueDate",
                        "followUpDate",
                        "priority")))
        .complete();

    // then
    Assertions.assertThat(
            RecordingExporter.userTaskRecords(UserTaskIntent.CORRECTED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .hasChangedAttributes(
            "assignee",
            "candidateUsersList",
            "candidateGroupsList",
            "dueDate",
            "followUpDate",
            "priority")
        .hasAssignee("new_assignee")
        .hasCandidateUsersList(List.of("new_candidate_user"))
        .hasCandidateGroupsList(List.of("new_candidate_group"))
        .hasDueDate("new_due_date")
        .hasFollowUpDate("new_follow_up_date")
        .hasPriority(100)
        .describedAs("Expect that the action references the listened to action")
        .hasAction(expectedAction)
        .describedAs("Expect that the other data is also filled but remains unchanged")
        .hasBpmnProcessId(userTask.getBpmnProcessId())
        .hasCreationTimestamp(userTask.getCreationTimestamp())
        .hasElementId(userTask.getElementId())
        .hasElementInstanceKey(userTask.getElementInstanceKey())
        .hasExternalFormReference(userTask.getExternalFormReference())
        .hasFormKey(userTask.getFormKey())
        .hasProcessDefinitionKey(userTask.getProcessDefinitionKey())
        .hasProcessInstanceKey(userTask.getProcessInstanceKey())
        .hasVariables(userTask.getVariables())
        .hasTenantId(userTask.getTenantId())
        .hasUserTaskKey(userTask.getUserTaskKey());

    // when
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(listenerType + "_2")
        .withResult(
            new JobResult()
                .setCorrections(new JobResultCorrections().setPriority(3))
                .setCorrectedAttributes(List.of("priority")))
        .complete();

    // then
    Assertions.assertThat(
            RecordingExporter.userTaskRecords(UserTaskIntent.CORRECTED)
                .withProcessInstanceKey(processInstanceKey)
                .skip(1)
                .getFirst()
                .getValue())
        .describedAs("Expect only the corrected attributes are mentioned")
        .hasChangedAttributes("priority")
        .describedAs("Expect that the corrected attribute is updated")
        .hasPriority(3)
        .describedAs("Expect that the other corrected data remains unchanged")
        .hasAssignee("new_assignee")
        .hasCandidateUsersList(List.of("new_candidate_user"))
        .hasCandidateGroupsList(List.of("new_candidate_group"))
        .hasDueDate("new_due_date")
        .hasFollowUpDate("new_follow_up_date")
        .describedAs("Expect that the action references the listened to action")
        .hasAction(expectedAction)
        .describedAs("Expect that the other data is also filled but remains unchanged")
        .hasBpmnProcessId(userTask.getBpmnProcessId())
        .hasCreationTimestamp(userTask.getCreationTimestamp())
        .hasElementId(userTask.getElementId())
        .hasElementInstanceKey(userTask.getElementInstanceKey())
        .hasExternalFormReference(userTask.getExternalFormReference())
        .hasFormKey(userTask.getFormKey())
        .hasProcessDefinitionKey(userTask.getProcessDefinitionKey())
        .hasProcessInstanceKey(userTask.getProcessInstanceKey())
        .hasVariables(userTask.getVariables())
        .hasTenantId(userTask.getTenantId())
        .hasUserTaskKey(userTask.getUserTaskKey());
  }

  @Test
  public void shouldPropagateCorrectedDataToAssigningListenerJobHeadersOnTaskCreation() {
    verifyUserTaskDataPropagationAcrossListenerJobHeaders(
        ZeebeTaskListenerEventType.assigning,
        true,
        userTask -> {},
        List.of(
            UserTaskIntent.CREATING,
            UserTaskIntent.CREATED,
            UserTaskIntent.ASSIGNING,
            UserTaskIntent.COMPLETE_TASK_LISTENER,
            UserTaskIntent.CORRECTED,
            UserTaskIntent.COMPLETE_TASK_LISTENER,
            UserTaskIntent.CORRECTED,
            UserTaskIntent.COMPLETE_TASK_LISTENER,
            UserTaskIntent.ASSIGNED));
  }

  @Test
  public void shouldPropagateCorrectedDataToAssigningListenerJobHeadersOnTaskAssignment() {
    verifyUserTaskDataPropagationAcrossListenerJobHeaders(
        ZeebeTaskListenerEventType.assigning,
        false,
        userTask -> userTask.withAssignee("initial_assignee").assign(),
        List.of(
            UserTaskIntent.ASSIGNING,
            UserTaskIntent.COMPLETE_TASK_LISTENER,
            UserTaskIntent.CORRECTED,
            UserTaskIntent.COMPLETE_TASK_LISTENER,
            UserTaskIntent.CORRECTED,
            UserTaskIntent.COMPLETE_TASK_LISTENER,
            UserTaskIntent.ASSIGNED));
  }

  @Test
  public void shouldPropagateCorrectedDataToAssigningListenerJobHeadersOnTaskClaiming() {
    verifyUserTaskDataPropagationAcrossListenerJobHeaders(
        ZeebeTaskListenerEventType.assigning,
        false,
        userTask -> userTask.withAssignee("initial_assignee").claim(),
        List.of(
            UserTaskIntent.CLAIMING,
            UserTaskIntent.COMPLETE_TASK_LISTENER,
            UserTaskIntent.CORRECTED,
            UserTaskIntent.COMPLETE_TASK_LISTENER,
            UserTaskIntent.CORRECTED,
            UserTaskIntent.COMPLETE_TASK_LISTENER,
            UserTaskIntent.ASSIGNED));
  }

  @Test
  public void shouldPropagateCorrectedDataToUpdatingListenerJobHeaders() {
    verifyUserTaskDataPropagationAcrossListenerJobHeaders(
        ZeebeTaskListenerEventType.updating,
        false,
        userTask ->
            userTask
                .withCandidateUsers("initial_candidate_user")
                .withCandidateGroups("initial_candidate_group")
                .withDueDate("2085-09-21T11:22:33+02:00")
                .withFollowUpDate("2095-09-21T11:22:33+02:00")
                .update(),
        List.of(
            UserTaskIntent.UPDATING,
            UserTaskIntent.COMPLETE_TASK_LISTENER,
            UserTaskIntent.CORRECTED,
            UserTaskIntent.COMPLETE_TASK_LISTENER,
            UserTaskIntent.CORRECTED,
            UserTaskIntent.COMPLETE_TASK_LISTENER,
            UserTaskIntent.UPDATED));
  }

  @Test
  public void shouldPropagateCorrectedDataToCompletingListenerJobHeadersOnTaskCompletion() {
    verifyUserTaskDataPropagationAcrossListenerJobHeaders(
        ZeebeTaskListenerEventType.completing,
        false,
        UserTaskClient::complete,
        List.of(
            UserTaskIntent.COMPLETING,
            UserTaskIntent.COMPLETE_TASK_LISTENER,
            UserTaskIntent.CORRECTED,
            UserTaskIntent.COMPLETE_TASK_LISTENER,
            UserTaskIntent.CORRECTED,
            UserTaskIntent.COMPLETE_TASK_LISTENER,
            UserTaskIntent.COMPLETED));
  }

  /**
   * Verifies the propagation of user task data across listener job headers during the task
   * lifecycle.
   *
   * <p>The method validates the following scenarios:
   *
   * <ul>
   *   <li>The headers of the first listener reflect the initial state of the user task, including
   *       configured or default properties.
   *   <li>After corrections are applied to the user task during completion of the first listener
   *       job, the headers of the subsequent listener should reflect the corrected properties.
   *   <li>After user task properties are cleared using corrections during completion of the second
   *       listener job, the headers of the third listener no longer include those cleared
   *       properties.
   *   <li>The entire sequence of user task lifecycle intents aligns with the expected order.
   * </ul>
   *
   * @param eventType the event type of the user task listener
   * @param isAssigneeConfiguredOnTaskCreation whether the assignee is configured during task
   *     creation
   * @param userTaskAction the user task action to trigger the listener (e.g., assign, claim,
   *     complete)
   * @param expectedUserTaskIntents the expected sequence of user task intents for the test
   */
  private void verifyUserTaskDataPropagationAcrossListenerJobHeaders(
      final ZeebeTaskListenerEventType eventType,
      final boolean isAssigneeConfiguredOnTaskCreation,
      final Consumer<UserTaskClient> userTaskAction,
      final List<UserTaskIntent> expectedUserTaskIntents) {

    // given: a process instance with a user task configured with listeners and initial properties
    final long processInstanceKey =
        helper.createProcessInstance(
            helper.createProcessWithZeebeUserTask(
                userTask -> {
                  if (isAssigneeConfiguredOnTaskCreation) {
                    userTask.zeebeAssignee("initial_assignee");
                  }
                  return userTask
                      .zeebeCandidateUsers("initial_candidate_user")
                      .zeebeCandidateGroups("initial_candidate_group")
                      .zeebeDueDate("2085-09-21T11:22:33+02:00")
                      .zeebeFollowUpDate("2095-09-21T11:22:33+02:00")
                      .zeebeTaskListener(l -> l.eventType(eventType).type(listenerType))
                      .zeebeTaskListener(l -> l.eventType(eventType).type(listenerType + "_2"))
                      .zeebeTaskListener(l -> l.eventType(eventType).type(listenerType + "_3"));
                }));

    final var createdUserTaskRecord =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    final var userTaskKey = String.valueOf(createdUserTaskRecord.getKey());

    // when: performing the user task action
    userTaskAction.accept(ENGINE.userTask().ofInstance(processInstanceKey));

    // Step 1: Validate headers of the first listener (initial user task state)
    final var firstListenerJob = helper.activateJob(processInstanceKey, listenerType);
    final var expectedInitialHeaders =
        new HashMap<String, String>() {
          {
            if (isAssigneeConfiguredOnTaskCreation) {
              put(Protocol.USER_TASK_ASSIGNEE_HEADER_NAME, "initial_assignee");
            }
            if (eventType == ZeebeTaskListenerEventType.assigning) {
              put(Protocol.USER_TASK_CHANGED_ATTRIBUTES_HEADER_NAME, "[\"assignee\"]");
            }
            put(Protocol.USER_TASK_KEY_HEADER_NAME, userTaskKey);
            put(Protocol.USER_TASK_CANDIDATE_USERS_HEADER_NAME, "[\"initial_candidate_user\"]");
            put(Protocol.USER_TASK_CANDIDATE_GROUPS_HEADER_NAME, "[\"initial_candidate_group\"]");
            put(Protocol.USER_TASK_DUE_DATE_HEADER_NAME, "2085-09-21T11:22:33+02:00");
            put(Protocol.USER_TASK_FOLLOW_UP_DATE_HEADER_NAME, "2095-09-21T11:22:33+02:00");
            put(Protocol.USER_TASK_PRIORITY_HEADER_NAME, "50");
          }
        };
    assertThat(firstListenerJob.getCustomHeaders())
        .describedAs("Headers should reflect the initial user task data in the first listener")
        .containsAllEntriesOf(expectedInitialHeaders);

    // Step 2: Apply corrections to the user task and validate the headers in the second listener
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(listenerType)
        .withResult(
            new JobResult()
                .setCorrections(
                    new JobResultCorrections()
                        .setAssignee("new_assignee")
                        .setCandidateUsersList(List.of("new_candidate_user"))
                        .setCandidateGroupsList(List.of("new_candidate_group"))
                        .setDueDate("new_due_date")
                        .setFollowUpDate("new_follow_up_date")
                        .setPriority(100))
                .setCorrectedAttributes(
                    List.of(
                        "assignee",
                        "candidateUsersList",
                        "candidateGroupsList",
                        "dueDate",
                        "followUpDate",
                        "priority")))
        .complete();

    final var secondListenerJob = helper.activateJob(processInstanceKey, listenerType + "_2");
    assertThat(secondListenerJob.getCustomHeaders())
        .describedAs(
            "Headers should reflect the corrected user task data in the subsequent listener")
        .contains(
            entry(Protocol.USER_TASK_ASSIGNEE_HEADER_NAME, "new_assignee"),
            entry(Protocol.USER_TASK_KEY_HEADER_NAME, userTaskKey),
            entry(Protocol.USER_TASK_CANDIDATE_USERS_HEADER_NAME, "[\"new_candidate_user\"]"),
            entry(Protocol.USER_TASK_CANDIDATE_GROUPS_HEADER_NAME, "[\"new_candidate_group\"]"),
            entry(Protocol.USER_TASK_DUE_DATE_HEADER_NAME, "new_due_date"),
            entry(Protocol.USER_TASK_FOLLOW_UP_DATE_HEADER_NAME, "new_follow_up_date"),
            entry(Protocol.USER_TASK_PRIORITY_HEADER_NAME, "100"));

    // Step 3: Clear user task properties using corrections and validate third listener headers
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(listenerType + "_2")
        .withResult(
            new JobResult()
                .setCorrections(new JobResultCorrections())
                .setCorrectedAttributes(
                    List.of(
                        "assignee",
                        "candidateUsersList",
                        "candidateGroupsList",
                        "dueDate",
                        "followUpDate",
                        "priority")))
        .complete();

    final var thirdListenerJob = helper.activateJob(processInstanceKey, listenerType + "_3");
    assertThat(thirdListenerJob.getCustomHeaders())
        .describedAs("Headers should not include cleared user task properties")
        .doesNotContainKeys(
            Protocol.USER_TASK_ASSIGNEE_HEADER_NAME,
            Protocol.USER_TASK_PRIORITY_HEADER_NAME,
            Protocol.USER_TASK_CANDIDATE_GROUPS_HEADER_NAME,
            Protocol.USER_TASK_CANDIDATE_USERS_HEADER_NAME,
            Protocol.USER_TASK_DUE_DATE_HEADER_NAME,
            Protocol.USER_TASK_FOLLOW_UP_DATE_HEADER_NAME);

    helper.completeJobs(processInstanceKey, listenerType + "_3");

    // Step 4: Validate the complete sequence of user task lifecycle intents
    helper.assertUserTaskIntentsSequence(
        processInstanceKey, expectedUserTaskIntents.toArray(UserTaskIntent[]::new));
  }

  @Test
  public void
      shouldTrackChangedAttributesOnlyForActuallyCorrectedValuesOnTaskAssignmentAfterCreation() {
    verifyChangedAttributesAreTrackedOnlyForActuallyCorrectedValues(
        ZeebeTaskListenerEventType.assigning, true, userTask -> {}, UserTaskIntent.ASSIGNED);
  }

  @Test
  public void shouldTrackChangedAttributesOnlyForActuallyCorrectedValuesOnTaskAssignment() {
    verifyChangedAttributesAreTrackedOnlyForActuallyCorrectedValues(
        ZeebeTaskListenerEventType.assigning,
        false,
        userTask -> userTask.withAssignee("initial_assignee").assign(),
        UserTaskIntent.ASSIGNED);
  }

  @Test
  public void shouldTrackChangedAttributesOnlyForActuallyCorrectedValuesOnTaskClaiming() {
    verifyChangedAttributesAreTrackedOnlyForActuallyCorrectedValues(
        ZeebeTaskListenerEventType.assigning,
        false,
        userTask -> userTask.withAssignee("initial_assignee").claim(),
        UserTaskIntent.ASSIGNED);
  }

  @Test
  public void shouldTrackChangedAttributesOnlyForActuallyCorrectedValuesOnTaskUpdate() {
    verifyChangedAttributesAreTrackedOnlyForActuallyCorrectedValues(
        ZeebeTaskListenerEventType.updating, false, UserTaskClient::update, UserTaskIntent.UPDATED);
  }

  @Test
  public void shouldTrackChangedAttributesOnlyForActuallyCorrectedValuesOnTaskCompletion() {
    verifyChangedAttributesAreTrackedOnlyForActuallyCorrectedValues(
        ZeebeTaskListenerEventType.completing,
        false,
        UserTaskClient::complete,
        UserTaskIntent.COMPLETED);
  }

  /**
   * Verifies that only the attributes actually corrected by user task listeners are tracked during
   * task lifecycle events.
   *
   * <p>This method validates the following scenarios:
   *
   * <ul>
   *   <li>Attributes corrected by the first listener are tracked and reflected in the emitted
   *       `CORRECTED` user task event.
   *   <li>If the next listener applies the same corrections, the `CORRECTED` intent isn't emitted
   *       because there are no changes in attribute values.
   *   <li>Partial corrections made by a subsequent listener include only the attributes that were
   *       actually modified.
   * </ul>
   *
   * @param eventType the event type of the user task listener
   * @param isAssigneeConfiguredOnTaskCreation whether the assignee is configured during task
   *     creation
   * @param userTaskAction the action performed on the user task
   * @param terminalActionIntent the final intent for the performed action on the user task
   */
  private void verifyChangedAttributesAreTrackedOnlyForActuallyCorrectedValues(
      final ZeebeTaskListenerEventType eventType,
      final boolean isAssigneeConfiguredOnTaskCreation,
      final Consumer<UserTaskClient> userTaskAction,
      final UserTaskIntent terminalActionIntent) {

    // given: a process instance with a user task configured with listeners and initial properties
    final long processInstanceKey =
        helper.createProcessInstance(
            helper.createProcessWithZeebeUserTask(
                userTask -> {
                  if (isAssigneeConfiguredOnTaskCreation) {
                    userTask.zeebeAssignee("initial_assignee");
                  }
                  return userTask
                      .zeebeCandidateUsers("initial_candidate_user")
                      .zeebeCandidateGroups("initial_candidate_group")
                      .zeebeDueDate("2085-09-21T11:22:33+02:00")
                      .zeebeFollowUpDate("2095-09-21T11:22:33+02:00")
                      .zeebeTaskListener(l -> l.eventType(eventType).type(listenerType))
                      .zeebeTaskListener(l -> l.eventType(eventType).type(listenerType + "_2"))
                      .zeebeTaskListener(l -> l.eventType(eventType).type(listenerType + "_3"));
                }));

    // Mocking a result that corrects all user task attributes
    final var correctAllAttributesResult =
        new JobResult()
            .setCorrections(
                new JobResultCorrections()
                    .setAssignee("new_assignee")
                    .setCandidateGroupsList(List.of("new_candidate_group"))
                    .setCandidateUsersList(List.of("new_candidate_user"))
                    .setDueDate("new_due_date")
                    .setFollowUpDate("new_follow_up_date")
                    .setPriority(100))
            .setCorrectedAttributes(
                List.of(
                    "assignee",
                    "candidateGroupsList",
                    "candidateUsersList",
                    "dueDate",
                    "followUpDate",
                    "priority"));

    // when: performing the user task action
    userTaskAction.accept(ENGINE.userTask().ofInstance(processInstanceKey));

    // First listener fully corrects the user task attributes
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(listenerType)
        .withResult(correctAllAttributesResult)
        .complete();

    // Second listener applies the same corrections
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(listenerType + "_2")
        .withResult(correctAllAttributesResult)
        .complete();

    // Third listener partially updates the user task
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(listenerType + "_3")
        .withResult(
            new JobResult()
                .setCorrections(
                    new JobResultCorrections()
                        .setCandidateGroupsList(List.of("modified_candidate_group")) // changed
                        .setDueDate("modified_due_date") // changed
                        .setFollowUpDate("new_follow_up_date") // same as the previous correction
                        .setPriority(88)) // changed
                .setCorrectedAttributes(
                    List.of("candidateGroupsList", "dueDate", "followUpDate", "priority")))
        .complete();

    // then: verify the changed attributes for `COMPLETE_TASK_LISTENER` and `CORRECTED` intents
    final Predicate<io.camunda.zeebe.protocol.record.Record<?>> isRelevantUserTaskIntent =
        record ->
            record.getIntent() == UserTaskIntent.COMPLETE_TASK_LISTENER
                || record.getIntent() == UserTaskIntent.CORRECTED;
    assertThat(
            RecordingExporter.userTaskRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(r -> r.getIntent() == terminalActionIntent))
        .filteredOn(isRelevantUserTaskIntent)
        .extracting(
            io.camunda.zeebe.protocol.record.Record::getIntent,
            r -> r.getValue().getChangedAttributes())
        .describedAs(
            "Expected corrected attributes to be tracked only for values that were actually modified")
        .containsExactly(
            // Listener 1: all attributes corrected
            tuple(UserTaskIntent.COMPLETE_TASK_LISTENER, ALL_CORRECTABLE_ATTRIBUTES),
            tuple(UserTaskIntent.CORRECTED, ALL_CORRECTABLE_ATTRIBUTES),
            // Listener 2: attributes corrected again to the same values
            tuple(UserTaskIntent.COMPLETE_TASK_LISTENER, ALL_CORRECTABLE_ATTRIBUTES),
            // No `CORRECTED` event was fired because the attributes' values were unchanged
            // Listener 3: partially corrected attributes
            tuple(
                UserTaskIntent.COMPLETE_TASK_LISTENER,
                List.of("candidateGroupsList", "dueDate", "followUpDate", "priority")),
            // `followUpDate` isn't present as it has the same value as the previous correction
            tuple(UserTaskIntent.CORRECTED, List.of("candidateGroupsList", "dueDate", "priority")));
  }

  @Test
  public void shouldTrackOnlyChangedAttributesDuringUserTaskAssignmentWithMultipleListeners() {
    // given
    final int initialPriority = 42;
    final long processInstanceKey =
        helper.createProcessInstance(
            helper.createProcessWithZeebeUserTask(
                task ->
                    task.zeebeTaskPriority(Integer.toString(initialPriority))
                        .zeebeTaskListener(listener -> listener.assigning().type(listenerType))
                        .zeebeTaskListener(
                            listener -> listener.assigning().type(listenerType + "_2"))
                        .zeebeTaskListener(
                            listener -> listener.assigning().type(listenerType + "_3"))));

    // when
    ENGINE.userTask().ofInstance(processInstanceKey).withAssignee("merry").assign();

    // then: the first listener job receives `changedAttributes` header with only "assignee"
    helper.assertActivatedJob(
        processInstanceKey,
        listenerType,
        job ->
            assertThat(job.getCustomHeaders())
                .describedAs(
                    "Expect first listener job to receive `changedAttributes` header containing only 'assignee' since it is the only field changed by the ASSIGN command.")
                .containsEntry(
                    Protocol.USER_TASK_CHANGED_ATTRIBUTES_HEADER_NAME, "[\"assignee\"]"));

    // when: first listener applies corrections to the `assignee`, `dueDate`, and `priority` fields
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(listenerType)
        .withResult(
            new JobResult()
                .setCorrections(
                    new JobResultCorrections().setDueDate("corrected_due_date").setPriority(84))
                .setCorrectedAttributes(List.of("dueDate", "priority")))
        .complete();

    // then: the second listener job receives cumulative changes from assignment and corrections
    helper.assertActivatedJob(
        processInstanceKey,
        listenerType + "_2",
        job ->
            assertThat(job.getCustomHeaders())
                .describedAs(
                    "Expect second listener job to receive `changedAttributes` header reflecting "
                        + "cumulative changes from the assignment transition ('assignee') and first listener "
                        + "corrections ('dueDate' and 'priority').")
                .containsEntry(
                    Protocol.USER_TASK_CHANGED_ATTRIBUTES_HEADER_NAME,
                    "[\"assignee\",\"dueDate\",\"priority\"]"));

    // when: second listener resets the `assignee` and `priority` to their initial user task values
    // before the task assignment
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(listenerType + "_2")
        .withResult(
            new JobResult()
                .setCorrections(
                    new JobResultCorrections()
                        .setAssignee("") // Clears the assignee to mark the task as unassigned
                        .setPriority(initialPriority))
                .setCorrectedAttributes(List.of("assignee", "priority")))
        .complete();

    // then: the third listener job sees only `dueDate` as changed after 2nd listener resets fields
    helper.assertActivatedJob(
        processInstanceKey,
        listenerType + "_3",
        job ->
            assertThat(job.getCustomHeaders())
                .describedAs(
                    "Expect third listener job to receive `changedAttributes` header containing "
                        + "only 'dueDate' because the second listener reset 'assignee' and 'priority' "
                        + "back to their original values, leaving 'dueDate' as the only remaining change.")
                .containsEntry(Protocol.USER_TASK_CHANGED_ATTRIBUTES_HEADER_NAME, "[\"dueDate\"]"));

    // when: third listener corrects the `candidateGroupsList`
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(listenerType + "_3")
        .withResult(
            new JobResult()
                .setCorrections(
                    new JobResultCorrections().setCandidateGroupsList(List.of("hobbits")))
                .setCorrectedAttributes(List.of("candidateGroupsList")))
        .complete();

    // then
    // Verify the sequence of intents and the `changedAttributes` emitted for the user task
    assertThat(
            RecordingExporter.userTaskRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(record -> record.getIntent() == UserTaskIntent.ASSIGNED))
        .as("Verify the user task lifecycle and tracking of `changedAttributes`")
        .extracting(
            io.camunda.zeebe.protocol.record.Record::getIntent,
            record -> record.getValue().getChangedAttributes())
        .containsSequence(
            tuple(UserTaskIntent.ASSIGNING, List.of("assignee")),
            tuple(UserTaskIntent.COMPLETE_TASK_LISTENER, List.of("dueDate", "priority")),
            tuple(UserTaskIntent.CORRECTED, List.of("dueDate", "priority")),
            tuple(UserTaskIntent.COMPLETE_TASK_LISTENER, List.of("assignee", "priority")),
            tuple(UserTaskIntent.CORRECTED, List.of("assignee", "priority")),
            tuple(UserTaskIntent.COMPLETE_TASK_LISTENER, List.of("candidateGroupsList")),
            tuple(UserTaskIntent.CORRECTED, List.of("candidateGroupsList")),
            // As a result, `changedAttributes` contains only `dueDate` and `candidateGroupsList`
            // because these are the only properties that differ from the original values of the
            // created user task. All other properties were either unchanged or reset to their
            // initial values that were before task assignment using the corrections.
            tuple(UserTaskIntent.ASSIGNED, List.of("candidateGroupsList", "dueDate")));

    final var createdUserTaskValue =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getValue();
    final var assignedUserTaskValue =
        RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getValue();

    Assertions.assertThat(assignedUserTaskValue)
        // unchanged properties
        .hasFollowUpDate(createdUserTaskValue.getFollowUpDate())
        .hasCandidateUsersList(createdUserTaskValue.getCandidateUsersList())
        // properties reset to their initial values
        .hasAssignee(createdUserTaskValue.getAssignee())
        .hasPriority(createdUserTaskValue.getPriority())
        // changed properties
        .hasDueDate("corrected_due_date")
        .hasCandidateGroupsList("hobbits")
        .hasOnlyChangedAttributes("candidateGroupsList", "dueDate")
        .hasAction("assign");
  }

  @Test
  public void shouldTrackOnlyChangedAttributesDuringUserTaskUpdateWithMultipleListeners() {
    // given
    final int initialPriority = 42;
    final long processInstanceKey =
        helper.createProcessInstance(
            helper.createProcessWithZeebeUserTask(
                task ->
                    task.zeebeTaskPriority(Integer.toString(initialPriority))
                        .zeebeCandidateUsers("legolas, thorin")
                        .zeebeTaskListener(l -> l.updating().type(listenerType))
                        .zeebeTaskListener(l -> l.updating().type(listenerType + "_2"))
                        .zeebeTaskListener(l -> l.updating().type(listenerType + "_3"))));

    // when: UPDATE command modifies `candidateGroups`, `candidateUsers`, `dueDate`, and `priority`
    ENGINE
        .userTask()
        .ofInstance(processInstanceKey)
        .withCandidateGroups("elves", "dwarves")
        .withCandidateUsers("legolas", "thorin") // same as initial users
        .withDueDate("updated_due_date")
        .withPriority(99)
        .update();

    // then
    helper.assertActivatedJob(
        processInstanceKey,
        listenerType,
        job ->
            assertThat(job.getCustomHeaders())
                .describedAs(
                    "Expect the first listener job to receive `changedAttributes` header containing attributes changed by UPDATE command to new values.")
                .containsEntry(
                    Protocol.USER_TASK_CHANGED_ATTRIBUTES_HEADER_NAME,
                    // without `candidateUsersList` as it was updated to the same as initial value
                    "[\"candidateGroupsList\",\"dueDate\",\"priority\"]"));

    // when: first listener corrects `dueDate` and `followUpDate` attributes
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(listenerType)
        .withResult(
            new JobResult()
                .setCorrections(
                    new JobResultCorrections()
                        .setDueDate("corrected_due_date")
                        .setFollowUpDate("corrected_follow_up_date"))
                .setCorrectedAttributes(List.of("dueDate", "followUpDate")))
        .complete();

    // then: the second listener job receives cumulative changes from update and listener correction
    helper.assertActivatedJob(
        processInstanceKey,
        listenerType + "_2",
        job ->
            assertThat(job.getCustomHeaders())
                .describedAs(
                    "Expect second listener job to receive `changedAttributes` header reflecting "
                        + "cumulative changes from the UPDATE command ('candidateGroupsList', 'dueDate', 'priority') "
                        + "and first listener correction ('dueDate', 'followUpDate').")
                .containsEntry(
                    Protocol.USER_TASK_CHANGED_ATTRIBUTES_HEADER_NAME,
                    "[\"candidateGroupsList\",\"dueDate\",\"followUpDate\",\"priority\"]"));

    // when: second listener resets `priority` back to the initial user task value
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(listenerType + "_2")
        .withResult(
            new JobResult()
                .setCorrections(new JobResultCorrections().setPriority(initialPriority))
                .setCorrectedAttributes(List.of("priority")))
        .complete();

    // then: the third listener job sees only `candidateGroupsList` and `assignee` as changed
    helper.assertActivatedJob(
        processInstanceKey,
        listenerType + "_3",
        job ->
            assertThat(job.getCustomHeaders())
                .describedAs(
                    "Expect third listener job to receive `changedAttributes` header without "
                        + "'priority' because the second listener reset it to its initial value.")
                .containsEntry(
                    Protocol.USER_TASK_CHANGED_ATTRIBUTES_HEADER_NAME,
                    "[\"candidateGroupsList\",\"dueDate\",\"followUpDate\"]"));

    // when: third listener corrects `candidateGroupsList` and `assignee`
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(listenerType + "_3")
        .withResult(
            new JobResult()
                .setCorrections(
                    new JobResultCorrections()
                        .setCandidateGroupsList(List.of("hobbits"))
                        .setAssignee("frodo"))
                .setCorrectedAttributes(List.of("candidateGroupsList", "assignee")))
        .complete();

    // then
    assertThat(
            RecordingExporter.userTaskRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(record -> record.getIntent() == UserTaskIntent.UPDATED))
        .as("Verify the user task record lifecycle and tracking of `changedAttributes`")
        .extracting(
            io.camunda.zeebe.protocol.record.Record::getIntent,
            record -> record.getValue().getChangedAttributes())
        .containsSequence(
            tuple(UserTaskIntent.UPDATING, List.of("candidateGroupsList", "dueDate", "priority")),
            tuple(UserTaskIntent.COMPLETE_TASK_LISTENER, List.of("dueDate", "followUpDate")),
            tuple(UserTaskIntent.CORRECTED, List.of("dueDate", "followUpDate")),
            tuple(UserTaskIntent.COMPLETE_TASK_LISTENER, List.of("priority")),
            tuple(UserTaskIntent.CORRECTED, List.of("priority")),
            tuple(
                UserTaskIntent.COMPLETE_TASK_LISTENER, List.of("candidateGroupsList", "assignee")),
            tuple(UserTaskIntent.CORRECTED, List.of("candidateGroupsList", "assignee")),
            // As a result, `changedAttributes` in UPDATED record contains `assignee`, `dueDate`,
            // `followUpDate` and `candidateGroupsList` because these are the only properties that
            // differ from the user task values before update. All other properties were either
            // unchanged or reset to their initial values before finalizing update transition.
            tuple(
                UserTaskIntent.UPDATED,
                List.of("assignee", "candidateGroupsList", "dueDate", "followUpDate")));

    final var updatedUserTaskValue =
        RecordingExporter.userTaskRecords(UserTaskIntent.UPDATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getValue();

    Assertions.assertThat(updatedUserTaskValue)
        .describedAs("Expect to be unchanged as `priority` was reset to the initial value")
        .hasPriority(initialPriority)
        .describedAs("Expect to be updated or corrected")
        .hasAssignee("frodo")
        .hasDueDate("corrected_due_date")
        .hasFollowUpDate("corrected_follow_up_date")
        .hasCandidateGroupsList("hobbits")
        .describedAs(
            "Expect only attributes that were updated or corrected and remain different from pre-update task values")
        .hasOnlyChangedAttributes("assignee", "candidateGroupsList", "dueDate", "followUpDate")
        .hasAction("update");
  }

  @Test
  public void shouldTrackOnlyChangedAttributesDuringUserTaskCompletionWithMultipleListeners() {
    // given
    final long processInstanceKey =
        helper.createProcessInstance(
            helper.createProcessWithZeebeUserTask(
                task ->
                    task.zeebeAssignee("aragorn") // initially assigned
                        .zeebeTaskListener(l -> l.completing().type(listenerType))
                        .zeebeTaskListener(l -> l.completing().type(listenerType + "_2"))
                        .zeebeTaskListener(l -> l.completing().type(listenerType + "_3"))));

    // when: complete user task
    ENGINE.userTask().ofInstance(processInstanceKey).complete();

    // then
    helper.assertActivatedJob(
        processInstanceKey,
        listenerType,
        job ->
            assertThat(job.getCustomHeaders())
                .describedAs(
                    "Expect the first listener job to not have `changedAttributes` header since no attributes changed by complete command")
                .doesNotContainKey(Protocol.USER_TASK_CHANGED_ATTRIBUTES_HEADER_NAME));

    // when: first listener modifies `candidateGroupsList` and `priority`
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(listenerType)
        .withResult(
            new JobResult()
                .setCorrections(
                    new JobResultCorrections()
                        .setCandidateGroupsList(List.of("dúnadan"))
                        .setPriority(99))
                .setCorrectedAttributes(List.of("candidateGroupsList", "priority")))
        .complete();

    // then: the second listener job receives cumulative changes from first listener correction
    helper.assertActivatedJob(
        processInstanceKey,
        listenerType + "_2",
        job ->
            assertThat(job.getCustomHeaders())
                .describedAs(
                    "Expect second listener job to receive `changedAttributes` header reflecting "
                        + "changes from first listener correction ('candidateGroupsList', 'priority').")
                .containsEntry(
                    Protocol.USER_TASK_CHANGED_ATTRIBUTES_HEADER_NAME,
                    "[\"candidateGroupsList\",\"priority\"]"));

    // when: second listener clears `assignee`
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(listenerType + "_2")
        .withResult(
            new JobResult()
                .setCorrections(new JobResultCorrections().setAssignee("")) // unassign task
                .setCorrectedAttributes(List.of("assignee")))
        .complete();

    // then: the third listener job receives cumulative changes from 2 listener corrections
    helper.assertActivatedJob(
        processInstanceKey,
        listenerType + "_3",
        job ->
            assertThat(job.getCustomHeaders())
                .describedAs(
                    "Expect third listener job to receive `changedAttributes` header containing "
                        + "'assignee', 'candidateGroupsList', and 'priority' as cumulative changes "
                        + "from first and second listener corrections.")
                .containsEntry(
                    Protocol.USER_TASK_CHANGED_ATTRIBUTES_HEADER_NAME,
                    "[\"assignee\",\"candidateGroupsList\",\"priority\"]"));

    // when: third listener modifies `dueDate` and resets `priority` to the default value
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(listenerType + "_3")
        .withResult(
            new JobResult()
                .setCorrections(
                    new JobResultCorrections().setDueDate("finalized_due_date").setPriority(50))
                .setCorrectedAttributes(List.of("dueDate", "priority")))
        .complete();

    // then: verify sequence of intents and `changedAttributes`
    assertThat(
            RecordingExporter.userTaskRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(record -> record.getIntent() == UserTaskIntent.COMPLETED))
        .as("Verify the user task lifecycle and tracking of `changedAttributes`")
        .extracting(Record::getIntent, record -> record.getValue().getChangedAttributes())
        .containsSequence(
            tuple(UserTaskIntent.COMPLETING, List.of()), // No direct changes at completion
            tuple(
                UserTaskIntent.COMPLETE_TASK_LISTENER, List.of("candidateGroupsList", "priority")),
            tuple(UserTaskIntent.CORRECTED, List.of("candidateGroupsList", "priority")),
            tuple(UserTaskIntent.COMPLETE_TASK_LISTENER, List.of("assignee")),
            tuple(UserTaskIntent.CORRECTED, List.of("assignee")),
            tuple(UserTaskIntent.COMPLETE_TASK_LISTENER, List.of("dueDate", "priority")),
            tuple(UserTaskIntent.CORRECTED, List.of("dueDate", "priority")),
            // Final `changedAttributes` only includes attributes corrected by listeners.
            // `priority` isn't listed as it was reverted to its default value by the 3d listener
            tuple(UserTaskIntent.COMPLETED, List.of("assignee", "candidateGroupsList", "dueDate")));

    final var completedUserTaskValue =
        RecordingExporter.userTaskRecords(UserTaskIntent.COMPLETED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getValue();

    Assertions.assertThat(completedUserTaskValue)
        .describedAs("Expect to be unchanged")
        .hasFollowUpDate("")
        .hasPriority(50)
        .describedAs("Expect to be corrected")
        .hasAssignee("")
        .hasDueDate("finalized_due_date")
        .hasCandidateGroupsList("dúnadan")
        .describedAs(
            "Expect only attributes that were corrected and remain different from pre-completion task values")
        .hasOnlyChangedAttributes("candidateGroupsList", "dueDate", "assignee")
        .hasAction("complete");
  }

  @Test
  public void shouldPersistCorrectedUserTaskDataWhenAssigningOnCreationTaskListenerCompleted() {
    testPersistCorrectedUserTaskDataWhenAllTaskListenersCompleted(
        ZeebeTaskListenerEventType.assigning,
        u -> u.zeebeAssignee("initial_assignee"),
        userTask -> {},
        UserTaskIntent.ASSIGNED);
  }

  @Test
  public void shouldPersistCorrectedUserTaskDataWhenAssigningTaskListenerCompleted() {
    testPersistCorrectedUserTaskDataWhenAllTaskListenersCompleted(
        ZeebeTaskListenerEventType.assigning,
        u -> u,
        userTask -> userTask.withAssignee("initial_assignee").assign(),
        UserTaskIntent.ASSIGNED);
  }

  @Test
  public void shouldPersistCorrectedUserTaskDataWhenClaimingTaskListenerCompleted() {
    testPersistCorrectedUserTaskDataWhenAllTaskListenersCompleted(
        ZeebeTaskListenerEventType.assigning,
        u -> u,
        userTask -> userTask.withAssignee("initial_assignee").claim(),
        UserTaskIntent.ASSIGNED);
  }

  @Test
  public void shouldPersistCorrectedUserTaskDataWhenUpdatingTaskListenerCompletes() {
    testPersistCorrectedUserTaskDataWhenAllTaskListenersCompleted(
        ZeebeTaskListenerEventType.updating,
        u -> u,
        UserTaskClient::update,
        UserTaskIntent.UPDATED);
  }

  @Test
  public void shouldPersistCorrectedUserTaskDataWhenAllTaskListenersCompleted() {
    testPersistCorrectedUserTaskDataWhenAllTaskListenersCompleted(
        ZeebeTaskListenerEventType.completing,
        u -> u,
        UserTaskClient::complete,
        UserTaskIntent.COMPLETED);
  }

  private void testPersistCorrectedUserTaskDataWhenAllTaskListenersCompleted(
      final ZeebeTaskListenerEventType eventType,
      final UnaryOperator<UserTaskBuilder> userTaskBuilder,
      final Consumer<UserTaskClient> userTaskAction,
      final UserTaskIntent expectedUserTaskIntent) {
    // given
    final long processInstanceKey =
        helper.createProcessInstance(
            helper.createProcessWithZeebeUserTask(
                t ->
                    userTaskBuilder
                        .apply(t)
                        .zeebeTaskListener(l -> l.eventType(eventType).type(listenerType))
                        .zeebeTaskListener(l -> l.eventType(eventType).type(listenerType + "_2"))));

    userTaskAction.accept(ENGINE.userTask().ofInstance(processInstanceKey));

    // when
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(listenerType)
        .withResult(
            new JobResult()
                .setCorrections(
                    new JobResultCorrections()
                        .setAssignee("new_assignee")
                        .setCandidateUsersList(List.of("new_candidate_user"))
                        .setCandidateGroupsList(List.of("new_candidate_group"))
                        .setDueDate("new_due_date")
                        .setFollowUpDate("new_follow_up_date")
                        .setPriority(100))
                .setCorrectedAttributes(
                    List.of(
                        "assignee",
                        "candidateUsersList",
                        "candidateGroupsList",
                        "dueDate",
                        "followUpDate",
                        "priority")))
        .complete();
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(listenerType + "_2")
        .withResult(
            new JobResult()
                .setCorrections(new JobResultCorrections().setAssignee("twice_corrected_assignee"))
                .setCorrectedAttributes(List.of("assignee")))
        .complete();

    // then
    helper.assertUserTaskRecordWithIntent(
        processInstanceKey,
        expectedUserTaskIntent,
        userTaskRecord ->
            Assertions.assertThat(userTaskRecord)
                .describedAs("Expect that the last user task event contains the corrected data")
                .hasChangedAttributes(
                    "assignee",
                    "candidateUsersList",
                    "candidateGroupsList",
                    "dueDate",
                    "followUpDate",
                    "priority")
                .hasCandidateUsersList("new_candidate_user")
                .hasCandidateGroupsList("new_candidate_group")
                .hasDueDate("new_due_date")
                .hasFollowUpDate("new_follow_up_date")
                .hasPriority(100)
                .describedAs("Expect that the most recent correction takes precedence")
                .hasAssignee("twice_corrected_assignee"));
  }

  @Test
  public void shouldRejectDenyingTaskListenerWithCorrections() {
    // given
    final long processInstanceKey =
        helper.createProcessInstance(
            helper.createProcessWithCompletingTaskListeners(listenerType, listenerType + "_2"));

    ENGINE.userTask().ofInstance(processInstanceKey).complete();

    // when
    final var rejection =
        ENGINE
            .job()
            .ofInstance(processInstanceKey)
            .withType(listenerType)
            .withResult(
                new JobResult()
                    .setDenied(true)
                    .setCorrections(new JobResultCorrections().setAssignee("new_assignee"))
                    .setCorrectedAttributes(List.of("assignee")))
            .expectRejection()
            .complete();

    // then
    Assertions.assertThat(rejection)
        .describedAs("Expect that the job completion is rejected")
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            """
            Expected to complete task listener job with corrections, but the job result is denied. \
            The corrections would be reverted by the denial. Either complete the job with \
            corrections without setting denied, or complete the job with a denied result but no \
            corrections.""");
  }

  @Test
  public void shouldRejectTaskListenerCompletionWithUnknownCorrections() {
    // given
    final long processInstanceKey =
        helper.createProcessInstance(
            helper.createProcessWithCompletingTaskListeners(listenerType, listenerType + "_2"));

    ENGINE.userTask().ofInstance(processInstanceKey).complete();

    // when
    final var rejection =
        ENGINE
            .job()
            .ofInstance(processInstanceKey)
            .withType(listenerType)
            .withResult(new JobResult().setCorrectedAttributes(List.of("unknown_property")))
            .expectRejection()
            .complete();

    // then
    Assertions.assertThat(rejection)
        .describedAs("Expect that the job completion is rejected")
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            """
            Expected to complete task listener job with a corrections result, \
            but property 'unknown_property' cannot be corrected. \
            Only the following properties can be corrected: \
            [assignee, candidateGroupsList, candidateUsersList, dueDate, followUpDate, priority].""");
  }
}
