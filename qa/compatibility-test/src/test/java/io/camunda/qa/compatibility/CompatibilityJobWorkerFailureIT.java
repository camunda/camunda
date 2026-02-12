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
import io.camunda.client.exception.CamundaError;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import java.time.Duration;
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
    classes = CompatibilityJobWorkerFailureIT.TestApplication.class,
    properties = {"spring.main.web-application-type=none"})
@CamundaSpringProcessTest
class CompatibilityJobWorkerFailureIT {

  private static final String PROCESS_ID = "compatibilityFailureProcess";
  private static final String JOB_TYPE = "compatibility-failure-worker";
  private static final String BPMN_RESOURCE = "bpmn/compatibility-job-failure.bpmn";

  @Autowired private CamundaClient camundaClient;
  @Autowired private FailureWorkerTracker tracker;

  @Test
  void shouldCreateIncidentWhenWorkerFailsWithZeroRetries() {
    // given
    camundaClient.newDeployResourceCommand().addResourceFromClasspath(BPMN_RESOURCE).send().join();

    // when
    final ProcessInstanceEvent processInstance =
        camundaClient
            .newCreateInstanceCommand()
            .bpmnProcessId(PROCESS_ID)
            .latestVersion()
            .send()
            .join();

    // then
    CamundaAssert.assertThat(processInstance).hasActiveIncidents();
    assertThat(tracker.getFailedJobs()).isEqualTo(1);
  }

  @Component
  public static class FailureWorkerTracker {

    private final AtomicInteger failedJobs = new AtomicInteger(0);

    @JobWorker(type = JOB_TYPE, autoComplete = false)
    public void handleJob() {
      failedJobs.incrementAndGet();
      throw CamundaError.jobError("boom", Map.of(), 0, Duration.ofSeconds(10));
    }

    int getFailedJobs() {
      return failedJobs.get();
    }
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @Import({FailureWorkerTracker.class, CompatibilityTestSupportConfiguration.class})
  static class TestApplication {}
}
