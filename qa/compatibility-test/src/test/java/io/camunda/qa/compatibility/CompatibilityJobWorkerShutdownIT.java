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
import io.camunda.client.jobhandling.JobWorkerManager;
import io.camunda.client.spring.event.CamundaClientClosingSpringEvent;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;

@SpringBootTest(
    classes = CompatibilityJobWorkerShutdownIT.TestApplication.class,
    properties = {"spring.main.web-application-type=none"})
@CamundaSpringProcessTest
class CompatibilityJobWorkerShutdownIT {

  private static final String JOB_TYPE = "compatibility-shutdown-worker";

  @Autowired private CamundaClient camundaClient;
  @Autowired private ApplicationEventPublisher publisher;
  @Autowired private JobWorkerManager jobWorkerManager;

  @Test
  void shouldCloseJobWorkersOnClientShutdown() {
    assertThat(jobWorkerManager.getJobWorkers()).containsKey(JOB_TYPE);

    publisher.publishEvent(new CamundaClientClosingSpringEvent(this, camundaClient));

    assertThat(jobWorkerManager.getJobWorkers()).doesNotContainKey(JOB_TYPE);
  }

  @Component
  public static class ShutdownWorker {

    @JobWorker(type = JOB_TYPE, autoComplete = false)
    public void handleJob() {}
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @Import({ShutdownWorker.class, CompatibilityTestSupportConfiguration.class})
  static class TestApplication {}
}
