/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.compatibility;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;

@SpringBootTest(
    classes = CompatibilityJsonMapperIT.TestApplication.class,
    properties = {"spring.main.web-application-type=none"})
@CamundaSpringProcessTest
class CompatibilityJsonMapperIT {

  private static final String PROCESS_ID = "compatibilityJsonMapperProcess";
  private static final String JOB_TYPE = "compatibility-json-mapper-worker";
  private static final String BPMN_RESOURCE = "bpmn/compatibility-json-mapper.bpmn";

  @Autowired private CamundaClient camundaClient;
  @Autowired private JsonMapperTracker tracker;

  @Test
  void shouldApplyCustomObjectMapperWhenSerializingVariables() {
    camundaClient.newDeployResourceCommand().addResourceFromClasspath(BPMN_RESOURCE).send().join();

    final ProcessInstanceEvent processInstance =
        camundaClient
            .newCreateInstanceCommand()
            .bpmnProcessId(PROCESS_ID)
            .latestVersion()
            .variables(new Payload("alphaValue", 7))
            .send()
            .join();

    CamundaAssert.assertThat(processInstance).isCompleted();

    final Map<String, Object> variables = tracker.getVariables();
    assertThat(variables).containsEntry("first_value", "alphaValue");
    assertThat(variables).containsEntry("count", 7);
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @Import({JsonMapperTracker.class, CompatibilityTestSupportConfiguration.class})
  static class TestApplication {

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    }
  }

  @Component
  public static class JsonMapperTracker {

    private final AtomicReference<Map<String, Object>> variables = new AtomicReference<>();

    @JobWorker(type = JOB_TYPE, autoComplete = false)
    public void handleJob(final JobClient jobClient, final ActivatedJob job) {
      variables.set(job.getVariablesAsMap());
      jobClient.newCompleteCommand(job.getKey()).send().join();
    }

    Map<String, Object> getVariables() {
      return variables.get();
    }
  }

  record Payload(String firstValue, int count) {}
}
