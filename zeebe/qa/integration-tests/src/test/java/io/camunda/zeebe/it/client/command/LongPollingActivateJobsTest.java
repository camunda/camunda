/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.client.command;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.command.ActivateJobsCommandStep1;
import io.camunda.zeebe.client.api.response.ActivateJobsResponse;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.it.util.ZeebeResourcesHelper;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.junit.AutoCloseResources;
import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@ZeebeIntegration
@AutoCloseResources
public class LongPollingActivateJobsTest {

  @AutoCloseResource ZeebeClient client;

  @TestZeebe
  final TestStandaloneBroker zeebe =
      new TestStandaloneBroker()
          .withRecordingExporter(true)
          .withGatewayConfig(c -> c.getLongPolling().setEnabled(true));

  ZeebeResourcesHelper resourcesHelper;

  @BeforeEach
  void initClientAndInstances() {
    client = zeebe.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(15)).build();
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

    final int maxMessageSize = (int) zeebe.brokerConfig().getNetwork().getMaxMessageSizeInBytes();
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

    final ZeebeFuture<ActivateJobsResponse> responseFuture =
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

  private ActivateJobsCommandStep1 getCommand(final ZeebeClient client, final boolean useRest) {
    final ActivateJobsCommandStep1 activateJobsCommandStep1 = client.newActivateJobsCommand();
    return useRest ? activateJobsCommandStep1.useRest() : activateJobsCommandStep1.useGrpc();
  }

  private void sendActivateRequestsAndClose(final boolean useRest, final String jobType)
      throws InterruptedException {
    for (int i = 0; i < 3; i++) {
      final ZeebeClient tempClient = zeebe.newClientBuilder().usePlaintext().build();

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
