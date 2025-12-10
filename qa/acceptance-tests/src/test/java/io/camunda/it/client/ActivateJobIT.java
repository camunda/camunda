/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

@MultiDbTest
public class ActivateJobIT {

  // Numbers to test ResponseMapper.toActivateJobsResponse() method's if check where the actual
  // response size is bigger than the max message size when the response is built with metadata.
  // When we set jobVariableSize to 144, it produces ActivatedJob of size 1020 bytes.
  // 5 jobs of 1020 bytes equals to 5100 bytes (the configured maxMessageSize).
  // But when we build the actual response it exceeds 5100 bytes and fall into our case.
  private static final DataSize MAX_MESSAGE_SIZE = DataSize.ofBytes(5100);
  private static final String JOB_TYPE = "foo";
  private static final String PROCESS_ID = JOB_TYPE;
  private static final BpmnModelInstance BPMN_MODEL_INSTANCE =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .serviceTask()
          .zeebeJobType(JOB_TYPE)
          .endEvent()
          .done();

  @MultiDbTestApplication
  private static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBasicAuth()
          .withAuthorizationsEnabled()
          .withClusterConfig(
              c -> {
                c.getNetwork().setMaxMessageSize(MAX_MESSAGE_SIZE);
              })
          .withApiConfig(c -> c.getLongPolling().setEnabled(true));

  private static CamundaClient camundaClient;

  @BeforeAll
  static void setup() {
    camundaClient
        .newDeployResourceCommand()
        .addProcessModel(BPMN_MODEL_INSTANCE, "foo.bpmn")
        .send()
        .join();
  }

  @Test
  void shouldDeferActivatedJobsWithAuthenticationClaims() {
    // given
    final int jobVariableSize = 144;
    final var byteArray = new byte[jobVariableSize];
    final var message = new String(byteArray, StandardCharsets.UTF_8);
    final int numberOfJobsToActivate = 5;
    for (int i = 0; i < numberOfJobsToActivate; i++) {
      camundaClient
          .newCreateInstanceCommand()
          .bpmnProcessId(PROCESS_ID)
          .latestVersion()
          .variables(Map.of("message_content", message))
          .send()
          .join();
    }
    Assertions.assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withType(JOB_TYPE)
                .limit(numberOfJobsToActivate))
        .describedAs("Expect that all jobs are created.")
        .hasSize(numberOfJobsToActivate);

    // when
    final var response =
        camundaClient
            .newActivateJobsCommand()
            .jobType(JOB_TYPE)
            .maxJobsToActivate(numberOfJobsToActivate)
            .send()
            .join();

    // then
    // Some jobs should be deferred due to message size limits
    final var activatedJobs = response.getJobs().size();
    Assertions.assertThat(activatedJobs)
        .describedAs("Expect that not all jobs can be activated due to message size limits")
        .isLessThan(numberOfJobsToActivate);

    // Wait for deferred jobs to be failed back
    final var failedJobs =
        RecordingExporter.jobRecords(JobIntent.FAILED)
            .withType(JOB_TYPE)
            .limit(numberOfJobsToActivate - activatedJobs)
            .toList();

    Assertions.assertThat(failedJobs)
        .describedAs("Expect that deferred jobs are failed back to broker")
        .isNotEmpty();

    // Check that failed jobs contain authorization claims
    final var failedJob = failedJobs.getFirst();
    final var failedJobAuthorizations = failedJob.getAuthorizations();
    assertThat(failedJobAuthorizations).isNotEmpty();
    assertThat(failedJobAuthorizations).containsKey(Authorization.AUTHORIZED_ANONYMOUS_USER);
  }
}
