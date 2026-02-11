/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.compatibility;

import io.camunda.client.CamundaClient;
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
    classes = CompatibilityMultiTenancyIT.TestApplication.class,
    properties = {
      "spring.main.web-application-type=none",
      "camunda.process-test.multi-tenancy-enabled=true",
      "camunda.client.auth.method=basic",
      "camunda.client.auth.username=demo",
      "camunda.client.auth.password=demo"
    })
@CamundaSpringProcessTest
class CompatibilityMultiTenancyIT {

  private static final String PROCESS_ID = "compatibilityTenantProcess";
  private static final String JOB_TYPE = "compatibility-tenant-worker";
  private static final String BPMN_RESOURCE = "bpmn/compatibility-tenant.bpmn";
  private static final String TENANT_ID = "<default>";

  @Autowired private CamundaClient camundaClient;

  @Test
  void shouldCreateInstanceForTenant() {
    camundaClient
        .newDeployResourceCommand()
        .addResourceFromClasspath(BPMN_RESOURCE)
        .tenantId(TENANT_ID)
        .send()
        .join();

    final ProcessInstanceEvent processInstance =
        camundaClient
            .newCreateInstanceCommand()
            .bpmnProcessId(PROCESS_ID)
            .latestVersion()
            .tenantId(TENANT_ID)
            .send()
            .join();

    CamundaAssert.assertThat(processInstance).isCompleted();
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @Import({TenantWorker.class, CompatibilityTestSupportConfiguration.class})
  static class TestApplication {}

  @Component
  public static class TenantWorker {

    @JobWorker(type = JOB_TYPE, autoComplete = false, tenantIds = TENANT_ID)
    public void handleJob(final JobClient jobClient, final ActivatedJob job) {
      jobClient.newCompleteCommand(job.getKey()).send().join();
    }
  }
}
