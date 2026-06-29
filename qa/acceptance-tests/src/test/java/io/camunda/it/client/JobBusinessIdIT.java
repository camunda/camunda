/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.it.util.TestHelper.deployProcessAndWaitForIt;
import static io.camunda.it.util.TestHelper.startProcessInstanceWithBusinessId;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.response.Job;
import io.camunda.qa.util.compatibility.CompatibilityTest;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

/**
 * End-to-end coverage that a job inherits its owning process instance's {@code businessId} and that
 * the value is returned across both backends from the two surfaces that expose it: the {@code
 * /jobs/search} response and the {@code POST /jobs/activation} response.
 *
 * <p>Each test uses its own process/job type so the activation tests never steal another test's
 * job.
 */
@MultiDbTest
@CompatibilityTest
public class JobBusinessIdIT {

  private static CamundaClient client;

  @Test
  void shouldReturnBusinessIdOnActivatedJob() {
    // given
    final String jobType = deployServiceTaskProcess("activate");
    final ProcessInstanceEvent processInstance =
        startProcessInstanceWithBusinessId(client, "process-activate", "order-activate-1");
    waitForJob(processInstance.getProcessInstanceKey());

    // when
    final ActivatedJob job =
        client
            .newActivateJobsCommand()
            .jobType(jobType)
            .maxJobsToActivate(1)
            .send()
            .join()
            .getJobs()
            .getFirst();

    // then
    assertThat(job.getBusinessId()).isEqualTo("order-activate-1");
  }

  @Test
  void shouldReturnBusinessIdOnJobSearch() {
    // given
    deployServiceTaskProcess("search");
    final ProcessInstanceEvent processInstance =
        startProcessInstanceWithBusinessId(client, "process-search", "order-search-1");
    waitForJob(processInstance.getProcessInstanceKey());

    // when
    final Job job =
        client
            .newJobSearchRequest()
            .filter(f -> f.processInstanceKey(processInstance.getProcessInstanceKey()))
            .send()
            .join()
            .items()
            .getFirst();

    // then
    assertThat(job.getBusinessId()).isEqualTo("order-search-1");
  }

  @Test
  void shouldReturnNullBusinessIdWhenInstanceHasNone() {
    // given - a process instance started without a business ID
    final String jobType = deployServiceTaskProcess("nobusinessid");
    final ProcessInstanceEvent processInstance =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("process-nobusinessid")
            .latestVersion()
            .execute();
    waitForJob(processInstance.getProcessInstanceKey());

    // when
    final ActivatedJob activatedJob =
        client
            .newActivateJobsCommand()
            .jobType(jobType)
            .maxJobsToActivate(1)
            .send()
            .join()
            .getJobs()
            .getFirst();
    final Job searchedJob =
        client
            .newJobSearchRequest()
            .filter(f -> f.processInstanceKey(processInstance.getProcessInstanceKey()))
            .send()
            .join()
            .items()
            .getFirst();

    // then - both surfaces report no business ID
    assertThat(activatedJob.getBusinessId()).isNull();
    assertThat(searchedJob.getBusinessId()).isNull();
  }

  private static String deployServiceTaskProcess(final String suffix) {
    final String processId = "process-" + suffix;
    final String jobType = "job-" + suffix;
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(jobType))
            .endEvent()
            .done();
    deployProcessAndWaitForIt(client, model, processId + ".bpmn");
    return jobType;
  }

  private static void waitForJob(final long processInstanceKey) {
    Awaitility.await()
        .ignoreExceptions()
        .timeout(Duration.ofSeconds(30))
        .until(
            () ->
                !client
                    .newJobSearchRequest()
                    .filter(f -> f.processInstanceKey(processInstanceKey))
                    .send()
                    .join()
                    .items()
                    .isEmpty());
  }
}
