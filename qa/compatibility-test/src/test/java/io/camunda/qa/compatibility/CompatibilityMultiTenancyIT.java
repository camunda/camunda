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
import io.camunda.client.api.response.ProcessInstanceEvent;
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
      "io.camunda.process.test.multi-tenancy-enabled=true",
      "io.camunda.process.test.camunda-env-vars."
          + "CAMUNDA_SECURITY_INITIALIZATION_TENANTS_0_TENANTID=tenant1",
      "io.camunda.process.test.camunda-env-vars."
          + "CAMUNDA_SECURITY_INITIALIZATION_TENANTS_0_NAME=Tenant 1",
      "io.camunda.process.test.camunda-env-vars."
          + "CAMUNDA_SECURITY_INITIALIZATION_TENANTS_0_USERS=demo"
    })
@CamundaSpringProcessTest
class CompatibilityMultiTenancyIT {

  private static final String PROCESS_ID = "compatibilityTenantProcess";
  private static final String JOB_TYPE = "compatibility-tenant-worker";
  private static final String BPMN_RESOURCE = "bpmn/compatibility-tenant.bpmn";
  private static final String TENANT_ID = "tenant1";

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

  @Component
  public static class TenantWorker {

    @JobWorker(type = JOB_TYPE, tenantIds = TENANT_ID)
    public void handleJob() {
      // do nothing, just complete the job
    }
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @Import({TenantWorker.class, CompatibilityTestSupportConfiguration.class})
  static class TestApplication {}
}
