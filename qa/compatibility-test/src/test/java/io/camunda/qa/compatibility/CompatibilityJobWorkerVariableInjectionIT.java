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
import io.camunda.client.annotation.Variable;
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
    classes = CompatibilityJobWorkerVariableInjectionIT.TestApplication.class,
    properties = {"spring.main.web-application-type=none"})
@CamundaSpringProcessTest
class CompatibilityJobWorkerVariableInjectionIT {

  private static final String PROCESS_ID = "compatibilityVariableInjectionProcess";
  private static final String JOB_TYPE = "compatibility-variable-injection-worker";
  private static final String BPMN_RESOURCE = "bpmn/compatibility-variable-injection.bpmn";

  @Autowired private CamundaClient camundaClient;
  @Autowired private VariableInjectionWorker worker;

  @Test
  void shouldInjectVariablesIntoWorkerMethodParameters() {
    // given
    camundaClient.newDeployResourceCommand().addResourceFromClasspath(BPMN_RESOURCE).send().join();

    // when
    final ProcessInstanceEvent processInstance =
        camundaClient
            .newCreateInstanceCommand()
            .bpmnProcessId(PROCESS_ID)
            .latestVersion()
            .variables(Map.of("count", 5, "payload", Map.of("name", "camunda")))
            .send()
            .join();

    // then
    CamundaAssert.assertThat(processInstance)
        .isCompleted()
        .hasVariable("injected", true)
        .hasVariable("payloadName", "camunda");
    assertThat(worker.getLastPayloadName()).isEqualTo("camunda");
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @Import({VariableInjectionWorker.class, CompatibilityTestSupportConfiguration.class})
  static class TestApplication {}

  @Component
  public static class VariableInjectionWorker {

    private final AtomicReference<String> lastPayloadName = new AtomicReference<>();

    @JobWorker(type = JOB_TYPE, autoComplete = false)
    public void handleJob(
        final JobClient jobClient,
        final ActivatedJob job,
        @Variable(name = "count") final int count,
        @Variable(name = "payload") final Map<String, Object> payload) {
      lastPayloadName.set((String) payload.get("name"));

      jobClient
          .newCompleteCommand(job.getKey())
          .variables(Map.of("injected", count == 5, "payloadName", payload.get("name")))
          .send()
          .join();
    }

    String getLastPayloadName() {
      return lastPayloadName.get();
    }
  }
}
