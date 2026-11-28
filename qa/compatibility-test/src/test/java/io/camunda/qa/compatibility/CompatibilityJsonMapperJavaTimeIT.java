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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;

/**
 * Sends {@code java.time} variables through the client of an application that does not declare an
 * {@code ObjectMapper} of its own. The client is expected to use a copy of the {@code ObjectMapper}
 * that Spring Boot's Jackson auto-configuration provides, which writes {@code java.time} values as
 * ISO-8601 strings. Unlike {@link CompatibilityJsonMapperIT}, which brings its own mapper, this
 * depends on the order in which the starter and Spring Boot's Jackson auto-configuration are
 * evaluated.
 */
@SpringBootTest(
    classes = CompatibilityJsonMapperJavaTimeIT.TestApplication.class,
    properties = {"spring.main.web-application-type=none"})
@CamundaSpringProcessTest
class CompatibilityJsonMapperJavaTimeIT {

  private static final String PROCESS_ID = "compatibilityJsonMapperJavaTimeProcess";
  private static final String JOB_TYPE = "compatibility-json-mapper-java-time-worker";
  private static final String BPMN_RESOURCE = "bpmn/compatibility-json-mapper-java-time.bpmn";

  @Autowired private CamundaClient camundaClient;
  @Autowired private JavaTimeTracker tracker;

  @Test
  void shouldSerializeJavaTimeVariablesWithSpringObjectMapper() {
    // given
    camundaClient.newDeployResourceCommand().addResourceFromClasspath(BPMN_RESOURCE).send().join();

    final Map<String, Object> variables = new LinkedHashMap<>();
    variables.put("date", LocalDate.of(1990, 1, 1));
    variables.put("duration", Duration.ofMinutes(90));
    variables.put("instant", Instant.parse("2024-02-29T10:15:30Z"));

    // when
    final ProcessInstanceEvent processInstance =
        camundaClient
            .newCreateInstanceCommand()
            .bpmnProcessId(PROCESS_ID)
            .latestVersion()
            .variables(variables)
            .send()
            .join();

    // then
    CamundaAssert.assertThat(processInstance).isCompleted();

    assertThat(tracker.getVariables())
        .containsEntry("date", "1990-01-01")
        .containsEntry("duration", "PT1H30M")
        .containsEntry("instant", "2024-02-29T10:15:30Z");
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @Import({JavaTimeTracker.class, CompatibilityTestSupportConfiguration.class})
  static class TestApplication {}

  @Component
  public static class JavaTimeTracker {

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
}
