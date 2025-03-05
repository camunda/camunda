/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.client.command;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;
import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.AssignUserTaskResponse;
import io.camunda.client.api.response.CompleteUserTaskResponse;
import io.camunda.client.api.worker.JobHandler;
import io.camunda.zeebe.it.util.RecordingJobHandler;
import io.camunda.zeebe.it.util.ZeebeAssertHelper;
import io.camunda.zeebe.it.util.ZeebeResourcesHelper;
import io.camunda.zeebe.protocol.impl.record.value.job.JobResult;
import io.camunda.zeebe.protocol.impl.record.value.job.JobResultCorrections;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.hc.core5.http.HttpStatus;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// Suppressing resource warning because JobWorker instances are managed and closed automatically
// by `CamundaClient#close()`, which is invoked via the JUnit 5 extension due to the `client`
// being annotated with `@AutoClose`. This ensures proper cleanup without requiring explicit
// try-with-resources statements in each test.
@SuppressWarnings("resource")
@ZeebeIntegration
public class UserTaskListenersTest {

  @TestZeebe
  private static final TestStandaloneBroker ZEEBE =
      new TestStandaloneBroker().withRecordingExporter(true).withUnauthenticatedAccess();

  @AutoClose private CamundaClient client;

  private ZeebeResourcesHelper resourcesHelper;

  @BeforeEach
  void init() {
    client = ZEEBE.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(5)).build();
    resourcesHelper = new ZeebeResourcesHelper(client);
  }

  @Test
  void shouldCompleteUserTaskWithCompleteTaskListener() {
    // given
    final var action = "my_complete_action";
    final var userTaskKey =
        resourcesHelper.createSingleUserTask(
            t -> t.zeebeTaskListener(l -> l.completing().type("my_listener")));

    final JobHandler completeJobHandler =
        (jobClient, job) -> client.newCompleteCommand(job).withResult().deny(false).send().join();
    client.newWorker().jobType("my_listener").handler(completeJobHandler).open();

    // when: invoke complete user task command
    final var completeUserTaskFuture =
        client.newUserTaskCompleteCommand(userTaskKey).action(action).send();

    // TL job should be successfully completed with the result "denied" set correctly
    ZeebeAssertHelper.assertJobCompleted(
        "my_listener",
        (userTaskListener) -> {
          assertThat(userTaskListener.getResult().isDenied()).isFalse();
        });

    // wait for successful `COMPLETE` user task command completion
    assertThatCode(completeUserTaskFuture::join).doesNotThrowAnyException();

    // then
    ZeebeAssertHelper.assertUserTaskCompleted(
        userTaskKey,
        (userTask) -> {
          assertThat(userTask.getVariables()).isEmpty();
          assertThat(userTask.getAction()).isEqualTo(action);
        });
  }

  @Test
  void shouldAssignUserTaskAfterCompletingAssignmentTaskListener() {
    // given
    final var assignee = "demo_user";
    final var action = "my_assign_action";
    final var userTaskKey =
        resourcesHelper.createSingleUserTask(
            t -> t.zeebeTaskListener(l -> l.assigning().type("my_listener")));

    final JobHandler completeJobHandler =
        (jobClient, job) -> client.newCompleteCommand(job).send().join();
    client.newWorker().jobType("my_listener").handler(completeJobHandler).open();

    // when: invoke `ASSIGN` user task command
    final var assignUserTaskFuture =
        client.newUserTaskAssignCommand(userTaskKey).assignee(assignee).action(action).send();

    // wait for successful `ASSIGN` user task command completion
    assertThatCode(assignUserTaskFuture::join).doesNotThrowAnyException();

    // then
    ZeebeAssertHelper.assertUserTaskAssigned(
        userTaskKey,
        (userTask) -> {
          assertThat(userTask.getAssignee()).isEqualTo(assignee);
          assertThat(userTask.getVariables()).isEmpty();
          assertThat(userTask.getAction()).isEqualTo(action);
        });
  }

  @Test
  void shouldUpdateUserTaskAfterCompletingUpdatingTaskListener() {
    // given
    final var action = "my_update_action";
    final var userTaskKey =
        resourcesHelper.createSingleUserTask(
            t -> t.zeebeTaskListener(l -> l.updating().type("my_listener")));

    final JobHandler completeJobHandler =
        (jobClient, job) -> client.newCompleteCommand(job).send().join();
    client.newWorker().jobType("my_listener").handler(completeJobHandler).open();

    // when: invoke `UPDATE` user task command
    final var updateUserTaskFuture =
        client
            .newUserTaskUpdateCommand(userTaskKey)
            .candidateUsers("frodo", "samwise")
            .priority(88)
            .action(action)
            .send();

    // wait for successful `UPDATE` user task command completion
    assertThatCode(updateUserTaskFuture::join).doesNotThrowAnyException();

    // then
    ZeebeAssertHelper.assertUserTaskUpdated(
        userTaskKey,
        (userTask) -> {
          assertThat(userTask.getAssignee()).isEmpty();
          assertThat(userTask.getCandidateGroupsList()).isEmpty();
          assertThat(userTask.getDueDate()).isEmpty();
          assertThat(userTask.getFollowUpDate()).isEmpty();
          assertThat(userTask.getVariables()).isEmpty();
          // updated properties
          assertThat(userTask.getCandidateUsersList()).containsExactly("frodo", "samwise");
          assertThat(userTask.getPriority()).isEqualTo(88);
          assertThat(userTask.getChangedAttributes())
              .containsExactly(UserTaskRecord.CANDIDATE_USERS, UserTaskRecord.PRIORITY);
          assertThat(userTask.getAction()).isEqualTo(action);
        });
  }

  /**
   * This test verifies the behavior when attempting to complete a Task Listener job with variables
   * while awaiting the result of the completion command.
   *
   * <p>TL job completion with variables is currently not supported but is planned to be enabled as
   * part of issue <a href="https://github.com/camunda/camunda/issues/23702">#23702</a>.
   *
   * <p>The tested behavior is as follows:
   *
   * <ul>
   *   <li>The first attempt to complete the TL job with variables is rejected due to unsupported
   *       variable payload, resulting in the job being retried until all retries are exhausted.
   *   <li>Upon exhausting retries, a `JOB_NO_RETRIES` incident is created, detailing the rejection
   *       reason due to unsupported variables.
   *   <li>The test then adjusts the handler to complete next job without variables, updates retries
   *       for the previously failed job, and resolves the incident, which triggers the engine to
   *       retry the TL job again.
   *   <li>As a result, the user task `COMPLETE` request finishes successfully without any errors,
   *       and the User Task completes as expected.
   * </ul>
   */
  @Test
  void shouldRejectCompleteTaskListenerJobCompletionWhenVariablesAreSet() {
    // given
    final int jobRetries = 2;
    final var listenerType = "complete_with_variables";
    final var userTaskKey =
        resourcesHelper.createSingleUserTask(
            task ->
                task.zeebeTaskListener(
                    listener ->
                        listener
                            .completing()
                            .type(listenerType)
                            .retries(String.valueOf(jobRetries))));

    final var isCompletingWithVar = new AtomicBoolean(true);
    final JobHandler completeJobWithVariableHandler =
        (jobClient, job) -> {
          final var request = jobClient.newCompleteCommand(job);
          if (isCompletingWithVar.get()) {
            request.variable("my_variable", 123);
          }
          request.send().join();
        };

    final var recordingHandler = new RecordingJobHandler(completeJobWithVariableHandler);
    client.newWorker().jobType(listenerType).handler(recordingHandler).open();

    // when
    final var completeUserTaskFuture =
        client.newUserTaskCompleteCommand(userTaskKey).send().toCompletableFuture();
    waitForJobRetriesToBeExhausted(recordingHandler);

    // then
    final var handledJobs = recordingHandler.getHandledJobs();
    assertThat(handledJobs)
        .hasSize(jobRetries)
        .allSatisfy(job -> assertThat(job.getType()).isEqualTo(listenerType));

    final var rejectionReason =
        "Task Listener job completion with variables payload provided is not yet supported";
    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getIntent().equals(IncidentIntent.CREATED))
                .onlyCommandRejections())
        .describedAs(
            "Expected to have %d `COMPLETE` job command rejections all having same rejection type and reason",
            jobRetries)
        .hasSize(jobRetries)
        .allSatisfy(
            rejection ->
                Assertions.assertThat(rejection)
                    .hasIntent(JobIntent.COMPLETE)
                    .hasRejectionType(RejectionType.INVALID_ARGUMENT)
                    .extracting(Record::getRejectionReason, as(InstanceOfAssertFactories.STRING))
                    .startsWith(rejectionReason));

    // assert that an incident was created after exhausting all retries with a message
    // describing that the reason is the rejection of TL job completion with variables
    final long jobKey = handledJobs.getLast().getKey();
    final long incidentKey =
        ZeebeAssertHelper.assertIncidentCreated(
            incident ->
                assertThat(incident)
                    .hasJobKey(jobKey)
                    .hasErrorType(ErrorType.TASK_LISTENER_NO_RETRIES)
                    .extracting(
                        IncidentRecordValue::getErrorMessage, as(InstanceOfAssertFactories.STRING))
                    .startsWith("io.camunda.client.api.command.ClientStatusException:")
                    .contains("Command 'COMPLETE' rejected with code 'INVALID_ARGUMENT':")
                    .contains(rejectionReason));

    // tune JobHandler not to provide variables while completing the job
    isCompletingWithVar.set(false);
    // update retries for the job and resolve incident
    client.newUpdateRetriesCommand(jobKey).retries(1).send().join();
    client.newResolveIncidentCommand(incidentKey).send().join();

    // `COMPLETE` user task command request was completed successfully
    assertThatCode(completeUserTaskFuture::join).doesNotThrowAnyException();

    ZeebeAssertHelper.assertUserTaskCompleted(
        userTaskKey,
        (userTask) -> {
          assertThat(userTask.getVariables()).isEmpty();
          assertThat(userTask.getAction()).isEqualTo("complete");
        });
  }

  @Test
  void shouldRejectUserTaskUpdateWhenUpdatingTaskListenerDeniesTheWork() {
    // given
    final var listenerType = "complete_with_denial";
    final var userTaskKey =
        resourcesHelper.createSingleUserTask(
            t -> t.zeebeTaskListener(l -> l.updating().type(listenerType)));

    final JobHandler completeJobWithDenialHandler =
        (jobClient, job) ->
            client
                .newCompleteCommand(job)
                .withResult()
                .deny(true)
                .deniedReason("Reason to deny lifecycle transition")
                .send()
                .join();
    client.newWorker().jobType(listenerType).handler(completeJobWithDenialHandler).open();

    // when: invoke `UPDATE` user task command
    final var updateUserTaskFuture =
        client.newUserTaskUpdateCommand(userTaskKey).candidateUsers("user123").send();

    // then: TL job should be successfully completed with the result "denied" set correctly
    ZeebeAssertHelper.assertJobCompleted(
        listenerType,
        userTaskListener -> {
          assertThat(userTaskListener.getResult().isDenied()).isTrue();
          assertThat(userTaskListener.getResult().getDeniedReason())
              .isEqualTo("Reason to deny lifecycle transition");
        });

    // and: verify the rejection and rejection reason
    final var rejectionReason =
        String.format(
            "Command 'UPDATE' rejected with code 'INVALID_STATE': Update of the User Task with key '%s' was denied by Task Listener. "
                + "Reason to deny: 'Reason to deny lifecycle transition'",
            userTaskKey);
    assertThatExceptionOfType(ProblemException.class)
        .isThrownBy(updateUserTaskFuture::join)
        .satisfies(
            ex -> {
              assertThat(ex.details().getTitle()).isEqualTo(RejectionType.INVALID_STATE.name());
              assertThat(ex.details().getDetail()).isEqualTo(rejectionReason);
              assertThat(ex.details().getStatus()).isEqualTo(HttpStatus.SC_CONFLICT);
            });

    // and: verify the expected sequence of User Task intents
    assertUserTaskIntentsSequence(
        UserTaskIntent.UPDATING, UserTaskIntent.DENY_TASK_LISTENER, UserTaskIntent.UPDATE_DENIED);
  }

  @Test
  public void shouldRejectUserTaskCompletionWhenTaskListenerDeniesTheWork() {
    // given
    final var listenerType = "my_listener";
    final var userTaskKey =
        resourcesHelper.createSingleUserTask(
            t -> t.zeebeTaskListener(l -> l.completing().type(listenerType)));

    final JobHandler completeJobHandler =
        (jobClient, job) ->
            client
                .newCompleteCommand(job)
                .withResult()
                .deny(true)
                .deniedReason("Reason to deny lifecycle transition")
                .send()
                .join();
    final var recordingHandler = new RecordingJobHandler(completeJobHandler);

    client.newWorker().jobType(listenerType).handler(recordingHandler).open();

    // when: invoke complete user task command
    final CamundaFuture<CompleteUserTaskResponse> completeUserTaskFuture =
        client.newUserTaskCompleteCommand(userTaskKey).send();

    // TL job should be successfully completed with the result "denied" set correctly
    ZeebeAssertHelper.assertJobCompleted(
        listenerType,
        userTaskListener -> {
          assertThat(userTaskListener.getResult().isDenied()).isTrue();
          assertThat(userTaskListener.getResult().getDeniedReason())
              .isEqualTo("Reason to deny lifecycle transition");
        });

    final var rejectionReason =
        String.format(
            "Command 'COMPLETE' rejected with code 'INVALID_STATE': Completion of the User Task with key '%s' was denied by Task Listener. "
                + "Reason to deny: 'Reason to deny lifecycle transition'",
            userTaskKey);

    // verify the rejection
    assertThatExceptionOfType(ProblemException.class)
        .isThrownBy(completeUserTaskFuture::join)
        .satisfies(
            ex -> {
              assertThat(ex.details().getTitle()).isEqualTo(RejectionType.INVALID_STATE.name());
              assertThat(ex.details().getDetail()).isEqualTo(rejectionReason);
              assertThat(ex.details().getStatus()).isEqualTo(HttpStatus.SC_CONFLICT);
            });

    // verify the expected sequence of User Task intents
    assertUserTaskIntentsSequence(
        UserTaskIntent.COMPLETING,
        UserTaskIntent.DENY_TASK_LISTENER,
        UserTaskIntent.COMPLETION_DENIED);
  }

  @Test
  public void shouldRejectUserTaskCompletionWhenTaskListenerDeniesTheWorkNoReasonProvided() {
    // given
    final var listenerType = "my_listener";
    final var userTaskKey =
        resourcesHelper.createSingleUserTask(
            t -> t.zeebeTaskListener(l -> l.completing().type(listenerType)));

    final JobHandler completeJobHandler =
        (jobClient, job) -> client.newCompleteCommand(job).withResult().deny(true).send().join();
    final var recordingHandler = new RecordingJobHandler(completeJobHandler);

    client.newWorker().jobType(listenerType).handler(recordingHandler).open();

    // when: invoke complete user task command
    final CamundaFuture<CompleteUserTaskResponse> completeUserTaskFuture =
        client.newUserTaskCompleteCommand(userTaskKey).send();

    // TL job should be successfully completed with the result "denied" set correctly
    ZeebeAssertHelper.assertJobCompleted(
        listenerType,
        userTaskListener -> {
          assertThat(userTaskListener.getResult().isDenied()).isTrue();
          assertThat(userTaskListener.getResult().getDeniedReason()).isEqualTo("");
        });

    final var rejectionReason =
        String.format(
            "Command 'COMPLETE' rejected with code 'INVALID_STATE': Completion of the User Task with key '%s' was denied by Task Listener. "
                + "Reason to deny: ''",
            userTaskKey);

    // verify the rejection
    assertThatExceptionOfType(ProblemException.class)
        .isThrownBy(completeUserTaskFuture::join)
        .satisfies(
            ex -> {
              assertThat(ex.details().getTitle()).isEqualTo(RejectionType.INVALID_STATE.name());
              assertThat(ex.details().getDetail()).isEqualTo(rejectionReason);
              assertThat(ex.details().getStatus()).isEqualTo(HttpStatus.SC_CONFLICT);
            });

    // verify the expected sequence of User Task intents
    assertUserTaskIntentsSequence(
        UserTaskIntent.COMPLETING,
        UserTaskIntent.DENY_TASK_LISTENER,
        UserTaskIntent.COMPLETION_DENIED);
  }

  @Test
  public void shouldRejectUserTaskAssignmentWhenTaskListenerDeniesTheWork() {
    // given
    final var listenerType = "my_listener";
    final var userTaskKey =
        resourcesHelper.createSingleUserTask(
            t -> t.zeebeTaskListener(l -> l.assigning().type(listenerType)));

    final JobHandler completeJobHandler =
        (jobClient, job) ->
            client
                .newCompleteCommand(job)
                .withResult()
                .deny(true)
                .deniedReason("Reason to deny lifecycle transition")
                .send()
                .join();
    final var recordingHandler = new RecordingJobHandler(completeJobHandler);

    client.newWorker().jobType(listenerType).handler(recordingHandler).open();

    // when: invoke complete user task command

    final CamundaFuture<AssignUserTaskResponse> assignUserTaskFuture =
        client.newUserTaskAssignCommand(userTaskKey).assignee("john").send();

    // TL job should be successfully completed with the result "denied" set correctly
    ZeebeAssertHelper.assertJobCompleted(
        listenerType,
        userTaskListener -> {
          assertThat(userTaskListener.getResult().isDenied()).isTrue();
          assertThat(userTaskListener.getResult().getDeniedReason())
              .isEqualTo("Reason to deny lifecycle transition");
        });

    final var rejectionReason =
        String.format(
            "Command 'ASSIGN' rejected with code 'INVALID_STATE': Assignment of the User Task with key '%s' was denied by Task Listener. "
                + "Reason to deny: 'Reason to deny lifecycle transition'",
            userTaskKey);

    // verify the rejection
    assertThatExceptionOfType(ProblemException.class)
        .isThrownBy(assignUserTaskFuture::join)
        .satisfies(
            ex -> {
              assertThat(ex.details().getTitle()).isEqualTo(RejectionType.INVALID_STATE.name());
              assertThat(ex.details().getDetail()).isEqualTo(rejectionReason);
              assertThat(ex.details().getStatus()).isEqualTo(HttpStatus.SC_CONFLICT);
            });

    // verify the expected sequence of User Task intents
    assertUserTaskIntentsSequence(
        UserTaskIntent.ASSIGNING,
        UserTaskIntent.DENY_TASK_LISTENER,
        UserTaskIntent.ASSIGNMENT_DENIED);
  }

  @Test
  void shouldUpdateUserTaskWithUpdatingTaskListenerWithCorrections() {
    // given
    final var listenerType = "complete_updating_listener_with_corrections";
    final var userTaskKey =
        resourcesHelper.createSingleUserTask(
            t ->
                t.zeebeTaskListener(l -> l.updating().type(listenerType))
                    .zeebeCandidateGroups("initial_group_A, initial_group_B")
                    .zeebeTaskPriority("22"));

    final JobHandler completeJobWithCorrectionsHandler =
        (jobClient, job) ->
            client
                .newCompleteCommand(job)
                .withResult()
                .correctAssignee("new_assignee")
                .correctDueDate("new_due_date")
                .correctFollowUpDate("new_follow_up_date")
                .correctCandidateUsers(List.of("new_user_A", "new_user_B"))
                .correctCandidateGroups(List.of("new_group_C"))
                .correctPriority(99)
                .send()
                .join();

    client.newWorker().jobType(listenerType).handler(completeJobWithCorrectionsHandler).open();

    // when: invoke `UPDATE` user task command
    final var updateUserTaskFuture =
        client.newUserTaskUpdateCommand(userTaskKey).priority(55).action("escalate").send();

    final JobResult expectedResult =
        new JobResult()
            .setDenied(false)
            .setCorrections(
                new JobResultCorrections()
                    .setAssignee("new_assignee")
                    .setDueDate("new_due_date")
                    .setFollowUpDate("new_follow_up_date")
                    .setCandidateUsersList(List.of("new_user_A", "new_user_B"))
                    .setCandidateGroupsList(List.of("new_group_C"))
                    .setPriority(99))
            .setCorrectedAttributes(
                Arrays.asList(
                    UserTaskRecord.ASSIGNEE,
                    UserTaskRecord.DUE_DATE,
                    UserTaskRecord.FOLLOW_UP_DATE,
                    UserTaskRecord.CANDIDATE_USERS,
                    UserTaskRecord.CANDIDATE_GROUPS,
                    UserTaskRecord.PRIORITY));

    // TL job should be successfully completed with expected JobResult
    ZeebeAssertHelper.assertJobCompleted(
        listenerType, tl -> assertThat(tl.getResult()).isEqualTo(expectedResult));

    // wait for successful `UPDATE` user task command completion
    assertThatCode(updateUserTaskFuture::join).doesNotThrowAnyException();

    // then: verify the state of `UPDATED` user task record
    ZeebeAssertHelper.assertUserTaskUpdated(
        userTaskKey,
        (userTask) -> {
          assertThat(userTask.getAssignee()).isEqualTo("new_assignee");
          assertThat(userTask.getDueDate()).isEqualTo("new_due_date");
          assertThat(userTask.getFollowUpDate()).isEqualTo("new_follow_up_date");
          assertThat(userTask.getCandidateUsersList()).containsExactly("new_user_A", "new_user_B");
          assertThat(userTask.getCandidateGroupsList()).containsExactly("new_group_C");
          assertThat(userTask.getPriority()).isEqualTo(99);
        });
  }

  @Test
  void shouldUpdateUserTaskWithUpdatingTaskListenerWithPartialCorrections() {
    // given
    final var listenerType = "updating_listener_with_partial_corrections";
    final var initialDueDate = "2015-01-02T15:35+02:00";
    final var updatedDueDate = "2016-01-02T15:35+02:00";
    final var correctedDueDate = "2017-01-02T15:35+02:00";
    final var initialFollowUpDate = "2020-02-02T15:35+02:00";
    final var correctedFollowUpDate = "2021-02-02T15:35+02:00";

    final var userTaskKey =
        resourcesHelper.createSingleUserTask(
            t ->
                t.zeebeTaskListener(l -> l.updating().type(listenerType))
                    .zeebeAssignee("initial_assignee")
                    .zeebeDueDate(initialDueDate)
                    .zeebeFollowUpDate(initialFollowUpDate)
                    .zeebeCandidateUsers("initial_user_a, initial_user_b")
                    .zeebeCandidateGroups("initial_group_a, initial_group_b")
                    .zeebeTaskPriority("10"));

    // Define a task listener handler that applies corrections (some overriding the update request)
    final JobHandler completeJobWithCorrectionsHandler =
        (jobClient, job) ->
            client
                .newCompleteCommand(job)
                .withResult()
                .correctAssignee("corrected_assignee")
                .correctDueDate(correctedDueDate) // overrides the update
                .correctFollowUpDate(correctedFollowUpDate)
                .correctCandidateUsers(List.of("corrected_user_a", "corrected_user_b"))
                .correctPriority(10) // resets to the initial value, overriding the update
                .send()
                .join();

    client.newWorker().jobType(listenerType).handler(completeJobWithCorrectionsHandler).open();

    // when: send an `UPDATE` command with explicit changes
    final var updateUserTaskFuture =
        client
            .newUserTaskUpdateCommand(userTaskKey)
            .dueDate(updatedDueDate)
            .candidateGroups("updated_group")
            .priority(99) // will be reset to the initial value by correction
            .send();

    // wait for successful `UPDATE` command execution
    assertThatCode(updateUserTaskFuture::join).doesNotThrowAnyException();

    // then: verify the state of `UPDATED` user task record
    ZeebeAssertHelper.assertUserTaskUpdated(
        userTaskKey,
        (userTask) -> {
          // Verify attributes that were updated successfully
          assertThat(userTask.getCandidateGroupsList())
              .describedAs("Candidate groups should reflect the requested update")
              .containsExactly("updated_group");

          // Verify attributes that were corrected by the task listener
          assertThat(userTask.getAssignee())
              .describedAs("Assignee should be updated based on correction")
              .isEqualTo("corrected_assignee");

          assertThat(userTask.getDueDate())
              .describedAs("Due date should reflect correction (overriding the update request)")
              .isEqualTo(correctedDueDate);

          assertThat(userTask.getFollowUpDate())
              .describedAs("Follow-up date should be set based on correction")
              .isEqualTo(correctedFollowUpDate);

          assertThat(userTask.getCandidateUsersList())
              .describedAs("Candidate users should match corrected values")
              .containsExactly("corrected_user_a", "corrected_user_b");

          assertThat(userTask.getChangedAttributes())
              .describedAs("Changed attributes should reflect only actual modifications")
              .containsExactly(
                  UserTaskRecord.ASSIGNEE,
                  UserTaskRecord.CANDIDATE_GROUPS,
                  UserTaskRecord.CANDIDATE_USERS,
                  UserTaskRecord.DUE_DATE,
                  UserTaskRecord.FOLLOW_UP_DATE);

          // Verify unchanged attribute
          assertThat(userTask.getPriority())
              .describedAs("Priority should have the initial value as it was reset by correction")
              .isEqualTo(10);
        });
  }

  @Test
  void shouldCompleteUserTaskWithCompleteTaskListenerWithCorrections() {
    // given
    final var userTaskKey =
        resourcesHelper.createSingleUserTask(
            t -> t.zeebeTaskListener(l -> l.completing().type("my_listener")));

    final JobHandler completeJobHandler =
        (jobClient, job) ->
            client
                .newCompleteCommand(job)
                .withResult()
                .correctAssignee("Test")
                .correctDueDate("due date")
                .correctFollowUpDate("follow up date")
                .correctCandidateUsers(Arrays.asList("User A", "User B"))
                .correctCandidateGroups(Arrays.asList("Group A", "Group B"))
                .correctPriority(80)
                .send()
                .join();

    client.newWorker().jobType("my_listener").handler(completeJobHandler).open();

    // when: invoke complete user task command
    final var completeUserTaskFuture = client.newUserTaskCompleteCommand(userTaskKey).send();

    final JobResult expectedResult =
        new JobResult()
            .setDenied(false)
            .setCorrections(
                new JobResultCorrections()
                    .setAssignee("Test")
                    .setDueDate("due date")
                    .setFollowUpDate("follow up date")
                    .setCandidateUsersList(Arrays.asList("User A", "User B"))
                    .setCandidateGroupsList(Arrays.asList("Group A", "Group B"))
                    .setPriority(80))
            .setCorrectedAttributes(
                Arrays.asList(
                    UserTaskRecord.ASSIGNEE,
                    UserTaskRecord.DUE_DATE,
                    UserTaskRecord.FOLLOW_UP_DATE,
                    UserTaskRecord.CANDIDATE_USERS,
                    UserTaskRecord.CANDIDATE_GROUPS,
                    UserTaskRecord.PRIORITY));

    // TL job should be successfully completed with the result "denied" set correctly and
    // corrections as expected
    ZeebeAssertHelper.assertJobCompleted(
        "my_listener", (tl) -> assertThat(tl.getResult()).isEqualTo(expectedResult));

    // wait for successful `COMPLETE` user task command completion
    assertThatCode(completeUserTaskFuture::join).doesNotThrowAnyException();

    // then
    ZeebeAssertHelper.assertUserTaskCompleted(
        userTaskKey,
        (userTask) -> {
          assertThat(userTask.getAssignee()).isEqualTo("Test");
          assertThat(userTask.getDueDate()).isEqualTo("due date");
          assertThat(userTask.getFollowUpDate()).isEqualTo("follow up date");
          assertThat(userTask.getCandidateUsersList()).containsExactly("User A", "User B");
          assertThat(userTask.getCandidateGroupsList()).containsExactly("Group A", "Group B");
          assertThat(userTask.getPriority()).isEqualTo(80);
        });
  }

  @Test
  void shouldCompleteUserTaskWithCompleteTaskListenerWithPartialCorrections() {
    // given
    final var userTaskKey =
        resourcesHelper.createSingleUserTask(
            t -> t.zeebeTaskListener(l -> l.completing().type("my_listener")));

    final JobHandler completeJobHandler =
        (jobClient, job) ->
            client
                .newCompleteCommand(job)
                .withResult()
                .correctAssignee("Test")
                .correctFollowUpDate("follow up date")
                .correctCandidateUsers(Arrays.asList("User A", "User B"))
                .correctPriority(80)
                .send()
                .join();

    client.newWorker().jobType("my_listener").handler(completeJobHandler).open();

    // when: invoke complete user task command
    final var completeUserTaskFuture = client.newUserTaskCompleteCommand(userTaskKey).send();

    final JobResult expectedResult =
        new JobResult()
            .setDenied(false)
            .setCorrections(
                new JobResultCorrections()
                    .setAssignee("Test")
                    .setFollowUpDate("follow up date")
                    .setCandidateUsersList(Arrays.asList("User A", "User B"))
                    .setPriority(80))
            .setCorrectedAttributes(
                Arrays.asList(
                    UserTaskRecord.ASSIGNEE,
                    UserTaskRecord.FOLLOW_UP_DATE,
                    UserTaskRecord.CANDIDATE_USERS,
                    UserTaskRecord.PRIORITY));

    // TL job should be successfully completed with the result "denied" set correctly and
    // corrections as expected
    ZeebeAssertHelper.assertJobCompleted(
        "my_listener", (tl) -> assertThat(tl.getResult()).isEqualTo(expectedResult));

    // wait for successful `COMPLETE` user task command completion
    assertThatCode(completeUserTaskFuture::join).doesNotThrowAnyException();

    // then
    ZeebeAssertHelper.assertUserTaskCompleted(
        userTaskKey,
        (userTask) -> {
          assertThat(userTask.getAssignee()).isEqualTo("Test");
          assertThat(userTask.getFollowUpDate()).isEqualTo("follow up date");
          assertThat(userTask.getCandidateUsersList()).containsExactly("User A", "User B");
          assertThat(userTask.getPriority()).isEqualTo(80);
        });
  }

  private void waitForJobRetriesToBeExhausted(final RecordingJobHandler recordingHandler) {
    await("until all retries are exhausted")
        .untilAsserted(
            () ->
                assertThat(recordingHandler.getHandledJobs())
                    .describedAs("Job should be retried until retries are exhausted")
                    .last()
                    .extracting(ActivatedJob::getRetries)
                    .isEqualTo(1));
  }

  private void assertUserTaskIntentsSequence(final UserTaskIntent... intents) {
    assertThat(intents).describedAs("Expected intents not to be empty").isNotEmpty();
    assertThat(
            RecordingExporter.userTaskRecords()
                .limit(r -> r.getIntent() == intents[intents.length - 1]))
        .extracting(Record::getIntent)
        .describedAs("Verify the expected sequence of User Task intents")
        .containsSequence(intents);
  }
}
