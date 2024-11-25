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
import static org.awaitility.Awaitility.await;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobHandler;
import io.camunda.zeebe.it.util.RecordingJobHandler;
import io.camunda.zeebe.it.util.ZeebeAssertHelper;
import io.camunda.zeebe.it.util.ZeebeResourcesHelper;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.junit.AutoCloseResources;
import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
@AutoCloseResources
public class UserTaskListenersTest {

  @TestZeebe
  private final TestStandaloneBroker zeebe = new TestStandaloneBroker().withRecordingExporter(true);

  @AutoCloseResource private ZeebeClient client;

  private ZeebeResourcesHelper resourcesHelper;

  @BeforeEach
  void init() {
    client = zeebe.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(5)).build();
    resourcesHelper = new ZeebeResourcesHelper(client);
  }

  @Test
  void shouldCompleteUserTaskWithCompleteTaskListener() {
    // given
    final var action = "my_complete_action";
    final var userTaskKey =
        resourcesHelper.createSingleUserTask(
            t -> t.zeebeTaskListener(l -> l.complete().type("my_listener")));

    final JobHandler completeJobHandler =
        (jobClient, job) -> client.newCompleteCommand(job).send().join();
    client.newWorker().jobType("my_listener").handler(completeJobHandler).open();

    // when: invoke complete user task command
    final var completeUserTaskFuture =
        client.newUserTaskCompleteCommand(userTaskKey).action(action).send();

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
            t -> t.zeebeTaskListener(l -> l.assignment().type("my_listener")));

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
                            .complete()
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
                    .hasErrorType(ErrorType.JOB_NO_RETRIES)
                    .extracting(
                        IncidentRecordValue::getErrorMessage, as(InstanceOfAssertFactories.STRING))
                    .startsWith("io.camunda.zeebe.client.api.command.ClientStatusException:")
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
}
