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
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.worker.JobClient;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;

@SpringBootTest(
    classes = CompatibilityJobWorkerHeadersIT.TestApplication.class,
    properties = {"spring.main.web-application-type=none"})
@CamundaSpringProcessTest
class CompatibilityJobWorkerHeadersIT {

  private static final String PROCESS_ID = "compatibilityHeadersProcess";
  private static final String JOB_TYPE = "compatibility-headers-worker";
  private static final String BPMN_RESOURCE = "bpmn/compatibility-headers.bpmn";

  @Autowired private CamundaClient camundaClient;
  @Autowired private HeadersJobWorker worker;

  @Test
  void shouldExposeCustomHeadersInActivatedJob() {
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
    CamundaAssert.assertThat(processInstance).isCompleted().hasVariable("headersReceived", true);
    assertThat(worker.getLastHeaders())
        .isNotNull()
        .containsEntry("x-trace-id", "trace-123")
        .containsEntry("x-source", "compatibility");
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @Import({HeadersJobWorker.class, CompatibilityTestSupportConfiguration.class})
  static class TestApplication {}

  @Component
  public static class HeadersJobWorker {

    private final AtomicReference<Map<String, String>> lastHeaders = new AtomicReference<>();

    @JobWorker(type = JOB_TYPE, autoComplete = false)
    public void handleJob(final JobClient jobClient, final ActivatedJob job) {
      lastHeaders.set(job.getCustomHeaders());
      jobClient
          .newCompleteCommand(job.getKey())
          .variables(Map.of("headersReceived", true))
          .send()
          .join();
    }

    Map<String, String> getLastHeaders() {
      return lastHeaders.get();
    }
  }
}
