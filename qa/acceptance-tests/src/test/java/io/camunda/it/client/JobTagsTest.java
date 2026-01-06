/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.it.util.TestHelper.deployProcessAndWaitForIt;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.Process;
import io.camunda.qa.util.compatibility.CompatibilityTest;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.time.Duration;
import java.util.Set;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@CompatibilityTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
public class JobTagsTest {

  private static CamundaClient client;
  private static final Set<String> TAGS = Set.of("tag1", "tag2");

  private static Process process;

  @BeforeAll
  public static void beforeAll() {
    final var processDefinition =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("my-task", t -> t.zeebeJobType("my-job"))
            .endEvent()
            .done();

    process = deployProcessAndWaitForIt(client, processDefinition, "process.bpmn");
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
            .jobType("my-job")
            .maxJobsToActivate(1)
            .send()
            .join()
            .getJobs()
            .getFirst();

    // then
    assertThat(job.getTags()).containsExactlyInAnyOrder("businessKey:123", "priority:high");
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
