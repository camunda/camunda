/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.compatibility;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;

@SpringBootTest(
    classes = CompatibilityJobWorkerIT.TestApplication.class,
    properties = {"spring.main.web-application-type=none"})
@CamundaSpringProcessTest
class CompatibilityJobWorkerIT {

  private static final String PROCESS_ID = "compatibilityProcess";
  private static final String JOB_TYPE = "compatibility-worker";
  private static final String BPMN_RESOURCE = "bpmn/compatibility-job-worker.bpmn";

  @Autowired private CamundaClient camundaClient;
  @Autowired private JobWorkerTracker tracker;

  @Test
  void shouldDeployProcessAndCompleteJobUsingSpringWorker() {
    // given
    final var deployment =
        camundaClient
            .newDeployResourceCommand()
            .addResourceFromClasspath(BPMN_RESOURCE)
            .send()
            .join();

    // when
    final ProcessInstanceEvent processInstance =
        camundaClient
            .newCreateInstanceCommand()
            .bpmnProcessId(PROCESS_ID)
            .latestVersion()
            .send()
            .join();

    // then
    assertThat(deployment.getProcesses()).hasSize(1);
    CamundaAssert.assertThat(processInstance).isCompleted();
    assertThat(tracker.getCompletedJobs()).isEqualTo(1);
  }

  @Component
  public static class JobWorkerTracker {

    private final AtomicInteger completedJobs = new AtomicInteger(0);

    @JobWorker(type = JOB_TYPE)
    public Map<String, Object> handleJob() {
      completedJobs.incrementAndGet();
      return Map.of("status", "done");
    }

    int getCompletedJobs() {
      return completedJobs.get();
    }
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @Import({JobWorkerTracker.class, CompatibilityTestSupportConfiguration.class})
  static class TestApplication {}
}
