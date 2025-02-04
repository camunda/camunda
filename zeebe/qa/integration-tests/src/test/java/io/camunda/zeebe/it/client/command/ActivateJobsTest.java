/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.client.command;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.command.ActivateJobsCommandStep1;
import io.camunda.client.api.response.ActivateJobsResponse;
import io.camunda.zeebe.it.util.ZeebeResourcesHelper;
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
class ActivateJobsTest {

  @AutoClose CamundaClient client;

  @TestZeebe
  final TestStandaloneBroker zeebe =
      new TestStandaloneBroker()
          .withRecordingExporter(true)
          .withGatewayConfig(c -> c.getLongPolling().setEnabled(false));

  ZeebeResourcesHelper resourcesHelper;

  @BeforeEach
  void initClientAndInstances() {
    client = zeebe.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(15)).build();
    resourcesHelper = new ZeebeResourcesHelper(client);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldRespondActivatedJobsWhenJobsAreAvailable(
      final boolean useRest, final TestInfo testInfo) {
    // given
    final String jobType = "job-" + testInfo.getDisplayName();
    resourcesHelper.createJobs(jobType, 2);

    // when
    final CamundaFuture<ActivateJobsResponse> responseFuture =
        getCommand(client, useRest).jobType(jobType).maxJobsToActivate(2).send();

    // then
    final ActivateJobsResponse response = responseFuture.join();
    assertThat(response.getJobs()).hasSize(2);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldRespondNoActivatedJobsWhenNoJobsAvailable(final boolean useRest) {
    // when
    final CamundaFuture<ActivateJobsResponse> responseFuture =
        getCommand(client, useRest).jobType("notExisting").maxJobsToActivate(1).send();

    // then
    final ActivateJobsResponse response = responseFuture.join();
    assertThat(response.getJobs()).isEmpty();
  }

  private ActivateJobsCommandStep1 getCommand(final CamundaClient client, final boolean useRest) {
    final ActivateJobsCommandStep1 activateJobsCommandStep1 = client.newActivateJobsCommand();
    return useRest ? activateJobsCommandStep1.useRest() : activateJobsCommandStep1.useGrpc();
  }
}
