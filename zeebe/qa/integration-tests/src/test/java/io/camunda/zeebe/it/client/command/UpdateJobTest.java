/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.client.command;

import static io.camunda.zeebe.test.util.record.RecordingExporter.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ActivateJobsCommandStep1;
import io.camunda.client.api.command.UpdateRetriesJobCommandStep1;
import io.camunda.client.api.command.UpdateTimeoutJobCommandStep1;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.impl.ResponseMapper;
import io.camunda.client.protocol.rest.JobChangeset;
import io.camunda.zeebe.it.util.ZeebeResourcesHelper;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@ZeebeIntegration
public class UpdateJobTest {

  @AutoClose CamundaClient client;

  @TestZeebe
  final TestStandaloneBroker zeebe =
      new TestStandaloneBroker().withRecordingExporter(true).withUnauthenticatedAccess();

  ZeebeResourcesHelper resourcesHelper;

  @BeforeEach
  public void init() {
    client = zeebe.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(15)).build();
    resourcesHelper = new ZeebeResourcesHelper(client);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldIncreaseJobTimeoutInMillis(final boolean useRest, final TestInfo testInfo) {
    // given
    final String jobType = "job-" + testInfo.getDisplayName();
    createProcessInstance(jobType);
    final var job = activateJob(client, useRest, jobType);
    final long jobKey = job.getKey();
    final long initialDeadline = job.getDeadline();
    final long timeout = Duration.ofMinutes(15).toMillis();

    // when
    getTimeoutCommand(client, useRest, jobKey).timeout(timeout).send().join();

    // then
    assertTimeoutIncreased(initialDeadline, jobKey, useRest);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldDecreaseJobTimeoutInMillis(final boolean useRest, final TestInfo testInfo) {
    // given
    final String jobType = "job-" + testInfo.getDisplayName();
    createProcessInstance(jobType);
    final var job = activateJob(client, useRest, jobType);
    final long jobKey = job.getKey();
    final long initialDeadline = job.getDeadline();
    final long timeout = Duration.ofMinutes(13).toMillis();

    // when
    getTimeoutCommand(client, useRest, jobKey).timeout(timeout).send().join();

    // then
    assertTimeoutDecreased(initialDeadline, jobKey, useRest);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldIncreaseJobTimeoutDuration(final boolean useRest, final TestInfo testInfo) {
    // given
    final String jobType = "job-" + testInfo.getDisplayName();
    createProcessInstance(jobType);
    final var job = activateJob(client, useRest, jobType);
    final long jobKey = job.getKey();
    final long initialDeadline = job.getDeadline();
    final Duration timeout = Duration.ofMinutes(15);

    // when
    getTimeoutCommand(client, useRest, jobKey).timeout(timeout).send().join();

    // then
    assertTimeoutIncreased(initialDeadline, jobKey, useRest);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldDecreaseJobTimeoutDuration(final boolean useRest, final TestInfo testInfo) {
    // given
    final String jobType = "job-" + testInfo.getDisplayName();
    createProcessInstance(jobType);
    final var job = activateJob(client, useRest, jobType);
    final long jobKey = job.getKey();
    final long initialDeadline = job.getDeadline();
    final Duration timeout = Duration.ofMinutes(13);

    // when
    getTimeoutCommand(client, useRest, jobKey).timeout(timeout).send().join();

    // then
    assertTimeoutDecreased(initialDeadline, jobKey, useRest);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldUpdateJobRetries(final boolean useRest, final TestInfo testInfo) {
    // given
    final String jobType = "job-" + testInfo.getDisplayName();
    createProcessInstance(jobType);
    final var job = activateJob(client, useRest, jobType);
    final long jobKey = job.getKey();
    final int retries = 10;

    // when
    getRetriesCommand(client, useRest, jobKey).retries(retries).send().join();

    // then
    assertRetriesUpdated(jobKey, useRest, retries);
  }

  @Test
  public void shouldUpdateJobRetriesAndTimeout(final TestInfo testInfo) {
    // given
    final String jobType = "job-" + testInfo.getDisplayName();
    createProcessInstance(jobType);
    final var job = activateJob(client, true, jobType);
    final long jobKey = job.getKey();
    final int retries = 10;
    final long timeout = Duration.ofMinutes(15).toMillis();
    final var changeset = new JobChangeset().retries(retries).timeout(timeout);
    final var initialDeadline = job.getDeadline();

    // when
    client
        .newUpdateJobCommand(jobKey)
        .update(ResponseMapper.fromProtocolObject(changeset))
        .send()
        .join();

    // then
    assertTimeoutIncreased(initialDeadline, jobKey, true);
    assertRetriesUpdated(jobKey, true, retries);
  }

  @Test
  public void shouldUpdateOnyJobRetries(final TestInfo testInfo) {
    // given
    final String jobType = "job-" + testInfo.getDisplayName();
    createProcessInstance(jobType);
    final var job = activateJob(client, true, jobType);
    final long jobKey = job.getKey();
    final int retries = 10;
    final var changeset = new JobChangeset().retries(retries);
    final var initialDeadline = job.getDeadline();

    // when
    client
        .newUpdateJobCommand(jobKey)
        .update(ResponseMapper.fromProtocolObject(changeset))
        .send()
        .join();

    // then
    assertRetriesUpdated(jobKey, true, retries);
    final var deadline = retrieveCurrentDeadline(jobKey, JobIntent.UPDATE, JobIntent.UPDATED);
    assertThat(deadline).isEqualTo(initialDeadline);
  }

  @Test
  public void shouldUpdateOnlyJobTimeout(final TestInfo testInfo) {
    // given
    final String jobType = "job-" + testInfo.getDisplayName();
    createProcessInstance(jobType);
    final var job = activateJob(client, true, jobType);
    final long jobKey = job.getKey();
    final long timeout = Duration.ofMinutes(15).toMillis();
    final var changeset = new JobChangeset().timeout(timeout);
    final var initialDeadline = job.getDeadline();
    final var initialRetries = job.getRetries();

    // when
    client
        .newUpdateJobCommand(jobKey)
        .update(ResponseMapper.fromProtocolObject(changeset))
        .send()
        .join();

    // then
    assertTimeoutIncreased(initialDeadline, jobKey, true);
    final var retries = retrieveRetries(jobKey, JobIntent.UPDATE, JobIntent.UPDATED);
    assertThat(retries).isEqualTo(initialRetries);
  }

  @Test
  public void shouldUpdateJobRetriesAndTimeoutMultiParam(final TestInfo testInfo) {
    // given
    final String jobType = "job-" + testInfo.getDisplayName();
    createProcessInstance(jobType);
    final var job = activateJob(client, true, jobType);
    final long jobKey = job.getKey();
    final int retries = 10;
    final long timeout = Duration.ofMinutes(15).toMillis();
    final var initialDeadline = job.getDeadline();

    // when
    client.newUpdateJobCommand(jobKey).update(retries, timeout).send().join();

    // then
    assertTimeoutIncreased(initialDeadline, jobKey, true);
    assertRetriesUpdated(jobKey, true, retries);
  }

  @Test
  public void shouldUpdateOnyJobRetriesMultiParam(final TestInfo testInfo) {
    // given
    final String jobType = "job-" + testInfo.getDisplayName();
    createProcessInstance(jobType);
    final var job = activateJob(client, true, jobType);
    final long jobKey = job.getKey();
    final int retries = 10;
    final var initialDeadline = job.getDeadline();

    // when
    client.newUpdateJobCommand(jobKey).update(retries, null).send().join();

    // then
    assertRetriesUpdated(jobKey, true, retries);
    final var deadline = retrieveCurrentDeadline(jobKey, JobIntent.UPDATE, JobIntent.UPDATED);
    assertThat(deadline).isEqualTo(initialDeadline);
  }

  @Test
  public void shouldUpdateOnyJobRetriesSingleCommand(final TestInfo testInfo) {
    // given
    final String jobType = "job-" + testInfo.getDisplayName();
    createProcessInstance(jobType);
    final var job = activateJob(client, true, jobType);
    final long jobKey = job.getKey();
    final int retries = 10;
    final var initialDeadline = job.getDeadline();

    // when
    client.newUpdateJobCommand(jobKey).updateRetries(retries).send().join();

    // then
    assertRetriesUpdated(jobKey, true, retries);
    final var deadline = retrieveCurrentDeadline(jobKey, JobIntent.UPDATE, JobIntent.UPDATED);
    assertThat(deadline).isEqualTo(initialDeadline);
  }

  @Test
  public void shouldUpdateOnlyJobTimeoutMultiParam(final TestInfo testInfo) {
    // given
    final String jobType = "job-" + testInfo.getDisplayName();
    createProcessInstance(jobType);
    final var job = activateJob(client, true, jobType);
    final long jobKey = job.getKey();
    final long timeout = Duration.ofMinutes(15).toMillis();
    final var initialDeadline = job.getDeadline();
    final var initialRetries = job.getRetries();

    // when
    client.newUpdateJobCommand(jobKey).update(null, timeout).send().join();

    // then
    assertTimeoutIncreased(initialDeadline, jobKey, true);
    final var retries = retrieveRetries(jobKey, JobIntent.UPDATE, JobIntent.UPDATED);
    assertThat(retries).isEqualTo(initialRetries);
  }

  @Test
  public void shouldUpdateOnlyJobTimeoutSingleCommand(final TestInfo testInfo) {
    // given
    final String jobType = "job-" + testInfo.getDisplayName();
    createProcessInstance(jobType);
    final var job = activateJob(client, true, jobType);
    final long jobKey = job.getKey();
    final long timeout = Duration.ofMinutes(15).toMillis();
    final var initialDeadline = job.getDeadline();
    final var initialRetries = job.getRetries();

    // when
    client.newUpdateJobCommand(jobKey).updateTimeout(timeout).send().join();

    // then
    assertTimeoutIncreased(initialDeadline, jobKey, true);
    final var retries = retrieveRetries(jobKey, JobIntent.UPDATE, JobIntent.UPDATED);
    assertThat(retries).isEqualTo(initialRetries);
  }

  @Test
  public void shouldUpdateOnlyJobTimeoutSingleCommandDuration(final TestInfo testInfo) {
    // given
    final String jobType = "job-" + testInfo.getDisplayName();
    createProcessInstance(jobType);
    final var job = activateJob(client, true, jobType);
    final long jobKey = job.getKey();
    final Duration timeout = Duration.ofMinutes(15);
    final var initialDeadline = job.getDeadline();
    final var initialRetries = job.getRetries();

    // when
    client.newUpdateJobCommand(jobKey).updateTimeout(timeout).send().join();

    // then
    assertTimeoutIncreased(initialDeadline, jobKey, true);
    final var retries = retrieveRetries(jobKey, JobIntent.UPDATE, JobIntent.UPDATED);
    assertThat(retries).isEqualTo(initialRetries);
  }

  @Test
  public void shouldRejectIfBothFieldsAreNull(final TestInfo testInfo) {
    // given
    final String jobType = "job-" + testInfo.getDisplayName();
    createProcessInstance(jobType);
    final var job = activateJob(client, true, jobType);
    final long jobKey = job.getKey();

    // then
    final var expectedMessage = "At least one of [retries, timeout] is required";

    assertThatThrownBy(() -> client.newUpdateJobCommand(jobKey).update(null, null).send().join())
        .hasMessageContaining(expectedMessage);
  }

  @Test
  public void shouldRejectIfChangesetIsNull(final TestInfo testInfo) {
    // given
    final String jobType = "job-" + testInfo.getDisplayName();
    createProcessInstance(jobType);
    final var job = activateJob(client, true, jobType);
    final long jobKey = job.getKey();

    // then
    final var expectedMessage = "At least one of [retries, timeout] is required";

    assertThatThrownBy(() -> client.newUpdateJobCommand(jobKey).update(null).send().join())
        .hasMessageContaining(expectedMessage);
  }

  private void assertTimeoutIncreased(
      final long initialDeadline, final long jobKey, final boolean useRest) {
    final Long updatedDeadline =
        useRest
            ? retrieveCurrentDeadline(jobKey, JobIntent.UPDATE, JobIntent.UPDATED)
            : retrieveCurrentDeadline(jobKey, JobIntent.UPDATE_TIMEOUT, JobIntent.TIMEOUT_UPDATED);

    assertThat(updatedDeadline).isNotNull();
    assertThat(updatedDeadline).isGreaterThan(initialDeadline);
  }

  private void assertTimeoutDecreased(
      final long initialDeadline, final long jobKey, final boolean useRest) {
    final Long updatedDeadline =
        useRest
            ? retrieveCurrentDeadline(jobKey, JobIntent.UPDATE, JobIntent.UPDATED)
            : retrieveCurrentDeadline(jobKey, JobIntent.UPDATE_TIMEOUT, JobIntent.TIMEOUT_UPDATED);

    assertThat(updatedDeadline).isNotNull();
    assertThat(updatedDeadline).isLessThan(initialDeadline);
  }

  private Long retrieveCurrentDeadline(
      final long jobKey, final JobIntent update, final JobIntent updated) {
    assertThat(jobRecords(update).withRecordKey(jobKey).exists()).isTrue();

    return jobRecords(updated)
        .withRecordKey(jobKey)
        .findFirst()
        .map(r -> r.getValue().getDeadline())
        .orElse(null);
  }

  private void assertRetriesUpdated(
      final long jobKey, final boolean useRest, final int updateRetries) {
    final JobIntent update = useRest ? JobIntent.UPDATE : JobIntent.UPDATE_RETRIES;
    final JobIntent updated = useRest ? JobIntent.UPDATED : JobIntent.RETRIES_UPDATED;

    assertThat(jobRecords(update).withRecordKey(jobKey).exists()).isTrue();

    final var retries = retrieveRetries(jobKey, update, updated);
    assertThat(retries).isEqualTo(updateRetries);
  }

  private int retrieveRetries(final long jobKey, final JobIntent update, final JobIntent updated) {
    assertThat(jobRecords(update).withRecordKey(jobKey).exists()).isTrue();

    return jobRecords(updated)
        .withRecordKey(jobKey)
        .map(r -> r.getValue().getRetries())
        .findFirst()
        .get();
  }

  private UpdateTimeoutJobCommandStep1 getTimeoutCommand(
      final CamundaClient client, final boolean useRest, final long jobKey) {
    final UpdateTimeoutJobCommandStep1 timeoutCommand = client.newUpdateTimeoutCommand(jobKey);
    return useRest ? timeoutCommand.useRest() : timeoutCommand.useGrpc();
  }

  private UpdateRetriesJobCommandStep1 getRetriesCommand(
      final CamundaClient client, final boolean useRest, final long jobKey) {
    final UpdateRetriesJobCommandStep1 retriesCommand = client.newUpdateRetriesCommand(jobKey);
    return useRest ? retriesCommand.useRest() : retriesCommand.useGrpc();
  }

  private void createProcessInstance(final String jobType) {
    final var processDefinitionKey =
        resourcesHelper.deployProcess(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(jobType))
                .endEvent()
                .done());
    resourcesHelper.createProcessInstance(processDefinitionKey);
  }

  private ActivatedJob activateJob(
      final CamundaClient client, final boolean useRest, final String jobType) {
    final var activateResponse =
        getActivateCommand(client, useRest)
            .jobType(jobType)
            .maxJobsToActivate(1)
            .timeout(Duration.ofMinutes(14))
            .send()
            .join();

    assertThat(activateResponse.getJobs())
        .describedAs("Expected one job to be activated")
        .hasSize(1);

    return activateResponse.getJobs().get(0);
  }

  private ActivateJobsCommandStep1 getActivateCommand(
      final CamundaClient client, final boolean useRest) {
    final ActivateJobsCommandStep1 activateJobsCommandStep1 = client.newActivateJobsCommand();
    return useRest ? activateJobsCommandStep1.useRest() : activateJobsCommandStep1.useGrpc();
  }
}
