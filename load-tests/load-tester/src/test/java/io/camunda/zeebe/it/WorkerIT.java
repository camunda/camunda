/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.process.test.impl.containers.CamundaContainer;
import io.camunda.zeebe.LoadTesterApplication;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration test for the Worker component. Verifies that the worker can connect to a Camunda
 * cluster, subscribe to jobs via {@code @JobWorker}, and complete them.
 *
 * <p>Unlike the Starter, the Worker does not block Spring Boot startup. The {@code @JobWorker}
 * annotation registers the job handler automatically, so we can deploy a process and create
 * instances in the test method and verify the worker completes the jobs.
 */
@Testcontainers
@SpringBootTest(
    classes = LoadTesterApplication.class,
    properties = {
      "load-tester.worker.completion-delay=0ms",
      "load-tester.worker.payload-path=bpmn/small_payload.json",
      "load-tester.monitor-data-availability=false",
      "load-tester.perform-read-benchmarks=false",
    })
@ActiveProfiles({"worker", "it"})
class WorkerIT {

  @Container
  static final CamundaContainer CAMUNDA = CamundaContainerProvider.createCamundaContainer();

  @DynamicPropertySource
  static void configure(final DynamicPropertyRegistry registry) {
    CamundaContainerProvider.registerClientProperties(CAMUNDA, registry);
  }

  @Autowired private CamundaClient client;

  @Test
  void shouldCompleteJobs() {
    // given - deploy a process with a service task of type "benchmark-task"
    client.newDeployResourceCommand().addResourceFromClasspath("bpmn/one_task.bpmn").send().join();

    // when - create a process instance
    final var instanceEvent =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("benchmark")
            .latestVersion()
            .variables("{\"businessKey\": 1}")
            .send()
            .join();

    final long processInstanceKey = instanceEvent.getProcessInstanceKey();

    // then - the @JobWorker in Worker completes the job; verify via process instance get request
    await()
        .atMost(Duration.ofSeconds(60))
        .pollInterval(Duration.ofSeconds(2))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var pi = client.newProcessInstanceGetRequest(processInstanceKey).execute();
              assertThat(pi.getState())
                  .describedAs("Process instance should be completed by the worker")
                  .isEqualTo(ProcessInstanceState.COMPLETED);
            });
  }
}
