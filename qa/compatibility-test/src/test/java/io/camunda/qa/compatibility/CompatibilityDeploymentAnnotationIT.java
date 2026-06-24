/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.compatibility;

import io.camunda.client.CamundaClient;
import io.camunda.client.annotation.Deployment;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.worker.JobClient;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;

@SpringBootTest(
    classes = CompatibilityDeploymentAnnotationIT.TestApplication.class,
    properties = {"spring.main.web-application-type=none"})
@CamundaSpringProcessTest
class CompatibilityDeploymentAnnotationIT {

  private static final String PROCESS_ID = "compatibilityDeploymentProcess";
  private static final String JOB_TYPE = "compatibility-deployment-worker";

  @Autowired private CamundaClient camundaClient;

  @Test
  void shouldDeployProcessViaAnnotationOnStartup() {
    final ProcessInstanceEvent processInstance =
        camundaClient
            .newCreateInstanceCommand()
            .bpmnProcessId(PROCESS_ID)
            .latestVersion()
            .send()
            .join();

    CamundaAssert.assertThat(processInstance).isCompleted();
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @Deployment(resources = "classpath*:/bpmn/compatibility-deployment.bpmn")
  @Import({DeploymentWorker.class, CompatibilityTestSupportConfiguration.class})
  static class TestApplication {}

  @Component
  public static class DeploymentWorker {

    @JobWorker(type = JOB_TYPE, autoComplete = false)
    public void handleJob(final JobClient jobClient, final ActivatedJob job) {
      jobClient.newCompleteCommand(job.getKey()).send().join();
    }
  }
}
