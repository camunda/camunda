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
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.time.Duration;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@MultiDbTest
public class TagsTest {

  private static CamundaClient client;
  private String jobType = "my-job";

  @BeforeEach
  public void setUp() {
    // Make sure jobs are unique for each test
    jobType = UUID.randomUUID().toString();

    final var process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("my-task", t -> t.zeebeJobType(jobType))
            .endEvent()
            .done();

    final var deployment =
        client.newDeployResourceCommand().addProcessModel(process, "process.bpmn").send().join();
  }

  @Test
  void shouldCreateProcessInstanceWithTags() {
    // when
    final var processInstance =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .tags("businessKey:123", "priority:medium")
            .send()
            .join();

    waitForProcessInstance(processInstance.getProcessInstanceKey());

    final var foundProcessInstance =
        client.newProcessInstanceGetRequest(processInstance.getProcessInstanceKey()).send().join();

    assertThat(foundProcessInstance.getTags())
        .containsExactlyInAnyOrder("priority:medium", "businessKey:123");
  }

  @Test
  void shouldSearchProcessInstancesByTag() {
    // when
    final var processInstance =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .tags("businessKey:123", "priority:medium")
            .send()
            .join();

    final var otherProcessInstance =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .tags("businessKey:124", "priority:high")
            .send()
            .join();

    waitForProcessInstance(processInstance.getProcessInstanceKey());
    waitForProcessInstance(otherProcessInstance.getProcessInstanceKey());

    final var foundProcessInstances =
        client
            .newProcessInstanceSearchRequest()
            //            .filter(f -> f.tag("businessKey:123"))
            //            .filter(f -> f.tags("businessKey:123"))
            .filter(f -> f.processInstanceKey(processInstance.getProcessInstanceKey()))
            .send()
            .join()
            .items();

    assertThat(foundProcessInstances.size()).isEqualTo(1);

    final var foundProcessInstance = foundProcessInstances.getFirst();
    assertThat(foundProcessInstance.getTags())
        .containsExactlyInAnyOrder("priority:medium", "businessKey:123");
  }

  @Test
  void shouldForwardProcessInstanceTagsToJobWorker() {
    // when
    final var processInstance =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .tags("businessKey:123", "priority:high")
            .send()
            .join();

    waitForJobs(processInstance.getProcessInstanceKey());

    final var job =
        client
            .newActivateJobsCommand()
            .jobType(jobType)
            .maxJobsToActivate(1)
            .send()
            .join()
            .getJobs()
            .getFirst();

    // then
    assertThat(job.getTags()).containsExactlyInAnyOrder("businessKey:123", "priority:high");
    //    final String businessKey = job.getTags().getByKey("businessKey");
  }

  @Test
  void shouldAllowFilteringJobsOnActivation() {
    // when
    final var processInstance =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .tags("businessKey:123", "priority:medium")
            .send()
            .join();

    final var otherProcessInstance =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .tags("businessKey:124", "priority:high")
            .send()
            .join();

    waitForJobs(
        processInstance.getProcessInstanceKey(), otherProcessInstance.getProcessInstanceKey());

    final var jobs =
        client
            .newActivateJobsCommand()
            .jobType(jobType)
            .maxJobsToActivate(2)
            .tags("priority:high")
            .send()
            .join()
            .getJobs();

    // then
    assertThat(jobs.size()).isEqualTo(1);

    final var job = jobs.getFirst();
    assertThat(job.getTags()).containsExactlyInAnyOrder("priority:high", "businessKey:124");
    //    final String businessKey = job.getTags().getByKey("businessKey");
  }

  private void waitForProcessInstance(final long processInstanceKey) {
    Awaitility.await()
        .ignoreExceptions()
        .timeout(Duration.ofSeconds(30))
        .until(
            () ->
                !client
                    .newProcessInstanceSearchRequest()
                    .filter(f -> f.processInstanceKey(processInstanceKey))
                    .send()
                    .join()
                    .items()
                    .isEmpty());
  }

  private void waitForJobs(final Long... processInstanceKeys) {
    Awaitility.await()
        .ignoreExceptions()
        .timeout(Duration.ofSeconds(30))
        .until(
            () ->
                client
                        .newJobSearchRequest()
                        .filter(f -> f.processInstanceKey(k -> k.in(processInstanceKeys)))
                        .send()
                        .join()
                        .items()
                        .size()
                    >= processInstanceKeys.length);
  }
}
