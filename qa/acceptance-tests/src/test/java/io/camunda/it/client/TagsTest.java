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
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@MultiDbTest
public class TagsTest {

  private static CamundaClient client;

  @BeforeEach
  public void setUp() {
    final var process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("my-task", t -> t.zeebeJobType("my-job"))
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

    assertThat(foundProcessInstance.getTags()).containsOnly("priority:medium", "businessKey:123");
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
    assertThat(foundProcessInstance.getTags()).containsOnly("priority:medium", "businessKey:123");
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

    waitForJobs(processInstance.getProcessDefinitionKey(), 1);

    final var job =
        client
            .newActivateJobsCommand()
            .jobType("my-job")
            .maxJobsToActivate(1)
            .send()
            .join()
            .getJobs()
            .getFirst();

    // then
    assertThat(job.getTags()).containsOnly("businessKey:123", "priority:high");
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

    waitForJobs(processInstance.getProcessDefinitionKey(), 2);

    final var jobs =
        client
            .newActivateJobsCommand()
            .jobType("my-job")
            .maxJobsToActivate(2)
            .tags("priority:high")
            .send()
            .join()
            .getJobs();

    // then
    assertThat(jobs.size()).isEqualTo(1);

    final var job = jobs.getFirst();
    assertThat(job.getTags()).containsOnly("priority:high", "businessKey:124");
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

  private void waitForJobs(final long processDefinitionKey, final int count) {
    Awaitility.await()
        .ignoreExceptions()
        .timeout(Duration.ofSeconds(30))
        .until(
            () ->
                client
                        .newJobSearchRequest()
                        .filter(f -> f.processDefinitionKey(processDefinitionKey))
                        .send()
                        .join()
                        .items()
                        .size()
                    >= count);
  }
}
