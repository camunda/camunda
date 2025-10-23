/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.engine.client.command;

import static io.camunda.zeebe.test.util.record.RecordingExporter.jobRecords;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ActivateJobsCommandStep1;
import io.camunda.client.api.command.CompleteJobCommandStep1;
import io.camunda.client.api.command.FailJobCommandStep1;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.zeebe.it.util.ZeebeResourcesHelper;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@ZeebeIntegration
public final class FailJobTest {

  @TestZeebe
  private static final TestStandaloneBroker ZEEBE =
      new TestStandaloneBroker().withRecordingExporter(true).withUnauthenticatedAccess();

  @AutoClose CamundaClient client;
  ZeebeResourcesHelper resourcesHelper;

  @BeforeEach
  public void init() {
    client = ZEEBE.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(15)).build();
    resourcesHelper = new ZeebeResourcesHelper(client);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldFailJobWithRemainingRetries(final boolean useRest, final TestInfo testInfo) {
    // given
    final String jobType = "job-" + testInfo.getDisplayName();
    final var jobKey = resourcesHelper.createSingleJob(jobType);

    // when
    getCommand(client, useRest, jobKey).retries(2).send().join();

    // then
    final Record<JobRecordValue> record =
        jobRecords(JobIntent.FAILED).withRecordKey(jobKey).getFirst();
    Assertions.assertThat(record.getValue()).hasRetries(2).hasErrorMessage("");

    final var activatedJob = activateJob(client, useRest, jobType);
    assertThat(activatedJob.getKey()).isEqualTo(jobKey);
    assertThat(activatedJob.getRetries()).isEqualTo(2);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldFailJobWithErrorMessage(final boolean useRest, final TestInfo testInfo) {
    // given
    final String jobType = "job-" + testInfo.getDisplayName();
    final var jobKey = resourcesHelper.createSingleJob(jobType);

    // when
    getCommand(client, useRest, jobKey).retries(0).errorMessage("test").send().join();

    // then
    final Record<JobRecordValue> record =
        jobRecords(JobIntent.FAILED).withRecordKey(jobKey).getFirst();
    Assertions.assertThat(record.getValue()).hasRetries(0).hasErrorMessage("test");
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldFailJobWithRetryBackOff(final boolean useRest, final TestInfo testInfo) {
    // given
    final String jobType = "job-" + testInfo.getDisplayName();
    final var jobKey = resourcesHelper.createSingleJob(jobType);

    // when
    final Duration backoffTimeout = Duration.ofSeconds(30);
    getCommand(client, useRest, jobKey).retries(1).retryBackoff(backoffTimeout).send().join();

    // then
    final Record<JobRecordValue> beforeRecurRecord =
        jobRecords(JobIntent.FAILED).withRecordKey(jobKey).getFirst();
    Assertions.assertThat(beforeRecurRecord.getValue())
        .hasRetries(1)
        .hasRetryBackoff(backoffTimeout.toMillis());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldRejectIfJobIsAlreadyCompleted(final boolean useRest, final TestInfo testInfo) {
    // given
    final String jobType = "job-" + testInfo.getDisplayName();
    final var jobKey = resourcesHelper.createSingleJob(jobType);

    // when
    getCompleteCommand(client, useRest, jobKey).send().join();

    // when
    final var expectedMessage =
        String.format("Expected to fail job with key '%d', but no such job was found", jobKey);

    assertThatThrownBy(() -> getCommand(client, useRest, jobKey).retries(1).send().join())
        .hasMessageContaining(expectedMessage);
  }

  private ActivatedJob activateJob(
      final CamundaClient client, final boolean useRest, final String jobType) {
    final var activateResponse =
        getActivateCommand(client, useRest).jobType(jobType).maxJobsToActivate(1).send().join();

    assertThat(activateResponse.getJobs())
        .describedAs("Expected one job to be activated")
        .hasSize(1);

    return activateResponse.getJobs().get(0);
  }

  private FailJobCommandStep1 getCommand(
      final CamundaClient client, final boolean useRest, final long jobKey) {
    final FailJobCommandStep1 failJobCommandStep1 = client.newFailCommand(jobKey);
    return useRest ? failJobCommandStep1.useRest() : failJobCommandStep1.useGrpc();
  }

  private ActivateJobsCommandStep1 getActivateCommand(
      final CamundaClient client, final boolean useRest) {
    final ActivateJobsCommandStep1 activateJobsCommandStep1 = client.newActivateJobsCommand();
    return useRest ? activateJobsCommandStep1.useRest() : activateJobsCommandStep1.useGrpc();
  }

  private CompleteJobCommandStep1 getCompleteCommand(
      final CamundaClient client, final boolean useRest, final long jobKey) {
    final CompleteJobCommandStep1 completeJobCommandStep1 = client.newCompleteCommand(jobKey);
    return useRest ? completeJobCommandStep1.useRest() : completeJobCommandStep1.useGrpc();
  }
}
