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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.ClientException;
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
    final var userTaskKey =
        resourcesHelper.createSingleUserTask(
            t -> t.zeebeTaskListener(l -> l.complete().type("my_listener")));

    final JobHandler completeJobHandler =
        (jobClient, job) -> client.newCompleteCommand(job).send().join();
    client.newWorker().jobType("my_listener").handler(completeJobHandler).open();

    // when: invoke complete user task command
    final var completeUserTaskFuture = client.newUserTaskCompleteCommand(userTaskKey).send();

    // wait for successful `COMPLETE` user task command completion
    assertThatCode(completeUserTaskFuture::join).doesNotThrowAnyException();

    // then
    ZeebeAssertHelper.assertUserTaskCompleted(
        userTaskKey,
        (userTask) -> {
          assertThat(userTask.getVariables()).isEmpty();
          assertThat(userTask.getAction()).isEqualTo("complete");
        });
  }

  /**
   * This test verifies the behavior when attempting to complete a Task Listener job with variables
   * while awaiting the result of the completion command.
   *
   * <p>TL job completion with variables is currently not supported but is planned to be enabled
   * with the resolution of issue https://github.com/camunda/camunda/issues/23702.
   *
   * <p>The current behavior is as follows:
   *
   * <ul>
   *   <li>The TL job completion request is rejected due to the presence of variables. Since the
   *       request is awaiting a result (using the `.join()` method), it throws a
   *       `ClientStatusException` if the command is rejected.
   *   <li>The job is treated as failed and retried until all retries are exhausted.
   *   <li>Upon exhausting retries, a `JOB_NO_RETRIES` incident is created with a message explaining
   *       that the rejection is due to the unsupported use of variables during TL job completion.
   *   <li>The original `COMPLETE` user task command request fails with a timeout exception, as the
   *       User Task remains in the `COMPLETING` state without a response being written.
   * </ul>
   */
  @Test
  void shouldRejectCompleteTaskListenerJobCompletionWhenVariablesAreSetAndCreateIncident() {
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

    final JobHandler completeJobWithVariableHandler =
        (jobClient, job) ->
            jobClient.newCompleteCommand(job.getKey()).variable("my_variable", 123).send().join();

    final var recordingHandler = new RecordingJobHandler(completeJobWithVariableHandler);
    client.newWorker().jobType(listenerType).handler(recordingHandler).open();

    // when
    final var userTaskCompletionFuture =
        client.newUserTaskCompleteCommand(userTaskKey).send().toCompletableFuture();
    await("until all retries are exhausted")
        .untilAsserted(
            () ->
                assertThat(recordingHandler.getHandledJobs())
                    .describedAs(
                        "TL job should be retried until reaching the final attempt with retries set to 1")
                    .last()
                    .extracting(ActivatedJob::getRetries)
                    .isEqualTo(1));

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
    ZeebeAssertHelper.assertIncidentCreated(
        incident ->
            assertThat(incident)
                .hasJobKey(handledJobs.getLast().getKey())
                .hasErrorType(ErrorType.JOB_NO_RETRIES)
                .extracting(
                    IncidentRecordValue::getErrorMessage, as(InstanceOfAssertFactories.STRING))
                .startsWith("io.camunda.zeebe.client.api.command.ClientStatusException:")
                .contains("Command 'COMPLETE' rejected with code 'INVALID_ARGUMENT':")
                .contains(rejectionReason));

    // The rejection of the TL job `COMPLETE` command, due to variables payload being set,
    // results in the `COMPLETE` user task command request failing with a `timeout` exception.
    assertThatThrownBy(userTaskCompletionFuture::join)
        .isInstanceOf(ClientException.class)
        .hasMessageEndingWith("java.net.SocketTimeoutException: 5 SECONDS");
  }
}
