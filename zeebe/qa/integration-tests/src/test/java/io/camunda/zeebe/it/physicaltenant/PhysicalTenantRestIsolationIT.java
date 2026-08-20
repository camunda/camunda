/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.physicaltenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
final class PhysicalTenantRestIsolationIT {

  private static final String DEFAULT = PhysicalTenantsITHelper.DEFAULT_TENANT_ID;
  private static final String TENANT_A = "tenanta";
  private static final String JOB_TYPE = "task";
  private static final String PROCESS_ID = "rest-isolation-process";

  // both tenants use an isolated in-memory RDBMS so the REST query endpoints (which read from
  // secondary storage) can serve process definitions and instances per tenant
  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(DEFAULT, Storage.rdbmsH2("pt-default"))
          .withTenant(TENANT_A, Storage.rdbmsH2("pt-tenanta"))
          .build();

  @TestZeebe
  private final TestStandaloneBroker broker =
      TENANTS.configure(new TestStandaloneBroker().withUnauthenticatedAccess());

  @Test
  void shouldIsolateProcessDefinitionsAndInstancesAcrossPhysicalTenantsOverRest() throws Exception {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
            .endEvent()
            .done();

    final long processDefinitionKey;
    final long processInstanceKey;
    try (final CamundaClient tenantAClient = TENANTS.newClientBuilder(broker, TENANT_A).build();
        final CamundaClient defaultClient = TENANTS.newClientBuilder(broker, DEFAULT).build()) {
      processDefinitionKey =
          await("deployment to tenant A succeeds")
              .atMost(Duration.ofSeconds(30))
              .ignoreExceptions()
              .until(
                  () ->
                      tenantAClient
                          .newDeployResourceCommand()
                          .addProcessModel(process, PROCESS_ID + ".bpmn")
                          .send()
                          .join()
                          .getProcesses()
                          .get(0)
                          .getProcessDefinitionKey(),
                  key -> key > 0);

      processInstanceKey =
          tenantAClient
              .newCreateInstanceCommand()
              .bpmnProcessId(PROCESS_ID)
              .latestVersion()
              .send()
              .join()
              .getProcessInstanceKey();

      // when
      await("tenant A's process definition and instance are queryable over REST")
          .atMost(Duration.ofSeconds(60))
          .pollInterval(Duration.ofMillis(500))
          .untilAsserted(
              () -> {
                assertThat(tenantAClient.newProcessDefinitionSearchRequest().send().join().items())
                    .hasSize(1);
                assertThat(tenantAClient.newProcessInstanceSearchRequest().send().join().items())
                    .hasSize(1);
              });

      // then - the process definition is readable in tenant A but invisible to the default tenant
      assertThat(tenantAClient.newProcessDefinitionGetRequest(processDefinitionKey).send().join())
          .isNotNull();
      assertNotFound(
          () -> defaultClient.newProcessDefinitionGetRequest(processDefinitionKey).send().join());
      assertThat(defaultClient.newProcessDefinitionSearchRequest().send().join().items()).isEmpty();

      // and - the process instance is readable in tenant A but invisible to the default tenant
      assertThat(tenantAClient.newProcessInstanceGetRequest(processInstanceKey).send().join())
          .isNotNull();
      assertNotFound(
          () -> defaultClient.newProcessInstanceGetRequest(processInstanceKey).send().join());
      assertThat(defaultClient.newProcessInstanceSearchRequest().send().join().items()).isEmpty();
    }
  }

  private void assertNotFound(final ThrowingCallable callable) {
    assertThatThrownBy(callable)
        .isInstanceOf(ProblemException.class)
        .extracting(t -> ((ProblemException) t).code())
        .isEqualTo(404);
  }
}
