/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.engine.client.command;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.command.ActivateJobsCommandStep1;
import io.camunda.client.api.response.ActivateJobsResponse;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.zeebe.it.util.ZeebeResourcesHelper;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@ZeebeIntegration
public class LongPollingActivateJobsTest {

  @TestZeebe
  private static final TestStandaloneBroker ZEEBE =
      new TestStandaloneBroker()
          .withRecordingExporter(true)
          .withUnifiedConfig(c -> c.getApi().getLongPolling().setEnabled(true));

  @AutoClose CamundaClient client;
  ZeebeResourcesHelper resourcesHelper;

  @BeforeEach
  void initClientAndInstances() {
    client = ZEEBE.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(15)).build();
    resourcesHelper = new ZeebeResourcesHelper(client);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldActivateJobsRespectingAmountLimit(
      final boolean useRest, final TestInfo testInfo) {
    // given
    final int availableJobs = 3;
    final int activateJobs = 2;

    final String jobType = "job-" + testInfo.getDisplayName();
    resourcesHelper.createJobs(jobType, availableJobs);

    // when
    final ActivateJobsResponse response =
        getCommand(client, useRest).jobType(jobType).maxJobsToActivate(activateJobs).send().join();

    // then
    assertThat(response.getJobs()).hasSize(activateJobs);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  public void shouldActivateJobsIfBatchIsTruncated(final boolean useRest, final TestInfo testInfo) {
    // given
    final int availableJobs = 10;

    final int maxMessageSize =
        (int) ZEEBE.unifiedConfig().getCluster().getNetwork().getMaxMessageSize().toBytes();
    final var largeVariableValue = "x".repeat(maxMessageSize / 4);
    final String variablesJson = String.format("{\"variablesJson\":\"%s\"}", largeVariableValue);

    final String jobType = "job-" + testInfo.getDisplayName();
    resourcesHelper.createJobs(jobType, b -> {}, variablesJson, availableJobs);

    // when
    final var response =
        getCommand(client, useRest).jobType(jobType).maxJobsToActivate(availableJobs).send().join();

    // then
    assertThat(response.getJobs()).hasSize(availableJobs);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldWaitUntilJobsAvailable(final boolean useRest, final TestInfo testInfo) {
    // given
    final int expectedJobsCount = 1;

    final String jobType = "job-" + testInfo.getDisplayName();

    final CamundaFuture<ActivateJobsResponse> responseFuture =
        getCommand(client, useRest).jobType(jobType).maxJobsToActivate(expectedJobsCount).send();

    // when
    resourcesHelper.createSingleJob(jobType);

    // then
    final ActivateJobsResponse response = responseFuture.join();
    assertThat(response.getJobs()).hasSize(expectedJobsCount);
  }

  // TODO: the REST use case is currently not working, see
  // https://github.com/camunda/camunda/issues/19883
  @ParameterizedTest
  @ValueSource(booleans = {false})
  public void shouldActivateJobForOpenRequest(final boolean useRest, final TestInfo testInfo)
      throws InterruptedException {
    // given
    final String jobType = "job-" + testInfo.getDisplayName();

    sendActivateRequestsAndClose(useRest, jobType);

    final var activateJobsResponse =
        getCommand(client, useRest).jobType(jobType).maxJobsToActivate(5).workerName("open").send();

    sendActivateRequestsAndClose(useRest, jobType);

    // when
    resourcesHelper.createSingleJob(jobType);

    // then
    final var jobs = activateJobsResponse.join().getJobs();
    assertThat(jobs).hasSize(1).extracting(ActivatedJob::getWorker).contains("open");
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldActivateJobsWithTags(final boolean useRest, final TestInfo testInfo) {
    // given
    final int expectedJobsCount = 1;

    final String jobType = "job-" + testInfo.getDisplayName();

    final CamundaFuture<ActivateJobsResponse> responseFuture =
        getCommand(client, useRest).jobType(jobType).maxJobsToActivate(expectedJobsCount).send();

    // when
    resourcesHelper.createSingleJob(jobType, Set.of("tag1", "tag2"));

    // then
    final ActivateJobsResponse response = responseFuture.join();
    assertThat(response.getJobs()).hasSize(expectedJobsCount);
    final var responseJob = response.getJobs().get(0);
    assertThat(responseJob.getTags()).isEqualTo(Set.of("tag1", "tag2"));
  }

  private ActivateJobsCommandStep1 getCommand(final CamundaClient client, final boolean useRest) {
    final ActivateJobsCommandStep1 activateJobsCommandStep1 = client.newActivateJobsCommand();
    return useRest ? activateJobsCommandStep1.useRest() : activateJobsCommandStep1.useGrpc();
  }

  private void sendActivateRequestsAndClose(final boolean useRest, final String jobType)
      throws InterruptedException {
    for (int i = 0; i < 3; i++) {
      final CamundaClient tempClient = ZEEBE.newClientBuilder().build();

      getCommand(tempClient, useRest)
          .jobType(jobType)
          .maxJobsToActivate(5)
          .workerName("closed-" + i)
          .send();

      Thread.sleep(100);
      tempClient.close();
    }
  }
}
