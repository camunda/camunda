/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it;

import static io.camunda.it.util.TestHelper.deployProcessAndWaitForIt;
import static io.camunda.it.util.TestHelper.startProcessInstance;
import static io.camunda.it.util.TestHelper.waitForJobs;
import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.UpdateJobPriorityResponse;
import io.camunda.client.api.search.response.Job;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
public class JobPriorityUpdateIT {

  @MultiDbTestApplication
  private static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker().withUnauthenticatedAccess();

  private static CamundaClient client;
  private static CamundaClient grpcClient;

  @BeforeAll
  static void setUp() {
    grpcClient = BROKER.newClientBuilder().preferRestOverGrpc(false).build();
  }

  @AfterAll
  static void tearDown() {
    if (grpcClient != null) {
      grpcClient.close();
    }
  }

  @Test
  void shouldUpdateJobPriorityViaRest() {
    // given
    final int newPriority = 7;
    final long processInstanceKey = deployAndStartProcess(client, "prio-upd-rest-process");
    final Job job = waitForJobAvailable(client, processInstanceKey);

    // when
    final UpdateJobPriorityResponse response =
        client
            .newUpdateJobPriorityCommand(job.getJobKey())
            .useRest()
            .priority(newPriority)
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertPriorityUpdated(client, processInstanceKey, newPriority);
  }

  @Test
  void shouldUpdateJobPriorityViaGrpc() {
    // given
    final int newPriority = 5;
    final long processInstanceKey = deployAndStartProcess(grpcClient, "prio-upd-grpc-process");
    final Job job = waitForJobAvailable(grpcClient, processInstanceKey);

    // when
    final UpdateJobPriorityResponse response =
        grpcClient.newUpdateJobPriorityCommand(job.getJobKey()).priority(newPriority).send().join();

    // then
    assertThat(response).isNotNull();
    assertPriorityUpdated(grpcClient, processInstanceKey, newPriority);
  }

  @Test
  void shouldUpdateJobPriorityViaUpdateJobCommand() {
    // given
    final int newPriority = 3;
    final long processInstanceKey = deployAndStartProcess(client, "prio-upd-cmd-process");
    final Job job = waitForJobAvailable(client, processInstanceKey);

    // when
    client.newUpdateJobCommand(job.getJobKey()).updatePriority(newPriority).send().join();

    // then
    assertPriorityUpdated(client, processInstanceKey, newPriority);
  }

  @Test
  void shouldUpdateActivatedJobPriority() {
    // given
    final int newPriority = 9;
    final String jobType = "prio-upd-activated-job";
    final long processInstanceKey =
        deployAndStartProcess(client, "prio-upd-activated-process", jobType);
    waitForJobAvailable(client, processInstanceKey);

    final ActivatedJob activatedJob = activateJob(client, jobType);

    // when
    client.newUpdateJobCommand(activatedJob).updatePriority(newPriority).send().join();

    // then
    assertPriorityUpdated(client, processInstanceKey, newPriority);
  }

  private static long deployAndStartProcess(final CamundaClient c, final String processId) {
    return deployAndStartProcess(c, processId, "priority-update-job");
  }

  private static long deployAndStartProcess(
      final CamundaClient c, final String processId, final String jobType) {
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(jobType))
            .endEvent()
            .done();
    deployProcessAndWaitForIt(c, process, processId + ".bpmn");
    return startProcessInstance(c, processId).getProcessInstanceKey();
  }

  private static Job waitForJobAvailable(final CamundaClient c, final long processInstanceKey) {
    return waitForJobs(c, List.of(processInstanceKey)).getFirst();
  }

  private static ActivatedJob activateJob(final CamundaClient c, final String jobType) {
    return c.newActivateJobsCommand()
        .jobType(jobType)
        .maxJobsToActivate(1)
        .send()
        .join()
        .getJobs()
        .getFirst();
  }

  private static void assertPriorityUpdated(
      final CamundaClient c, final long processInstanceKey, final int expectedPriority) {
    Awaitility.await("job priority should be updated in search index")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(
                        c.newJobSearchRequest()
                            .filter(f -> f.processInstanceKey(processInstanceKey))
                            .send()
                            .join()
                            .items())
                    .hasSize(1)
                    .allSatisfy(job -> assertThat(job.getPriority()).isEqualTo(expectedPriority)));
  }
}
