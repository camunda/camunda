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
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

@MultiDbTest
public class ActivateJobIT {

  // Each variable value is a string of literal `"` characters. The broker stores variables
  // as MsgPack (~7 KB per JobRecord) but the gRPC gateway re-encodes them as JSON in the
  // ActivateJobsResponse proto, escaping each `"` to `\"` and doubling the byte count
  // (~14 KB per ActivatedJob). With maxMessageSize = 64 KB:
  //   - the broker can fit all 5 jobs into a single JobBatchRecord
  //     (5 * 7.5 KB + 8 KB safety buffer <= 64 KB)
  //   - but the gateway response (5 * ~14 KB) cannot fit within 64 KB, so at least one job
  //     is deferred and FAILed through RoundRobinActivateJobsHandler.toFailJobRequest.
  // The gRPC path is required: the REST gateway's defer check compares against the broker
  // record size, not the REST response size, so it cannot trigger this path under unified
  // config that ties broker and gateway maxMessageSize together.
  private static final DataSize MAX_MESSAGE_SIZE = DataSize.ofKilobytes(64);
  private static final int QUOTE_PAYLOAD_LENGTH = 7000;
  private static final int JOBS_TO_CREATE = 5;
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
          .withRecordingExporter(true)
          .withBrokerConfig(c -> c.getNetwork().setMaxMessageSize(MAX_MESSAGE_SIZE));

  private static CamundaClient grpcClient;

  @BeforeAll
  static void setup() {
    grpcClient = BROKER.newClientBuilder().preferRestOverGrpc(false).build();
    grpcClient
        .newDeployResourceCommand()
        .addProcessModel(BPMN_MODEL_INSTANCE, "foo.bpmn")
        .send()
        .join();
  }

  @AfterAll
  static void tearDown() {
    if (grpcClient != null) {
      grpcClient.close();
    }
  }

  @Test
  void shouldFailDeferredJobsWithAnonymousAuthorizationClaims() {
    // given
    final var quoteHeavyPayload = "\"".repeat(QUOTE_PAYLOAD_LENGTH);
    for (int i = 0; i < JOBS_TO_CREATE; i++) {
      grpcClient
          .newCreateInstanceCommand()
          .bpmnProcessId(PROCESS_ID)
          .latestVersion()
          .variables(Map.of("message_content", quoteHeavyPayload))
          .send()
          .join();
    }
    assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withType(JOB_TYPE)
                .limit(JOBS_TO_CREATE))
        .as("all jobs are created before activation")
        .hasSize(JOBS_TO_CREATE);

    // when
    grpcClient
        .newActivateJobsCommand()
        .jobType(JOB_TYPE)
        .maxJobsToActivate(JOBS_TO_CREATE)
        .send()
        .join();

    // then — the gateway response cannot include all activated jobs (5 * ~14 KB > 64 KB),
    // so at least one is FAILed back to the broker via toFailJobRequest. The fix in
    // RoundRobinActivateJobsHandler attaches the anonymous authorization claim so the
    // broker bypasses authorization for the gateway-internal FAIL command; the resulting
    // FAILED event carries the same claim and is what we assert on.
    final var failedJob =
        RecordingExporter.jobRecords(JobIntent.FAILED)
            .withType(JOB_TYPE)
            .limit(1)
            .findFirst()
            .orElseThrow();
    assertThat(failedJob.getAuthorizations())
        .as("gateway-issued FAIL command carries the anonymous authorization claim")
        .containsEntry(Authorization.AUTHORIZED_ANONYMOUS_USER, true);
  }
}
