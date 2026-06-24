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
import io.camunda.client.api.command.ClientStatusException;
import io.camunda.client.api.response.ActivateJobsResponse;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * End-to-end test of physical tenant isolation through the gRPC API. A physical tenant is
 * implemented as an independent partition group / engine; a client targets one by sending the
 * {@code Camunda-Physical-Tenant} header (wired via {@link CamundaClient#newClientBuilder()}'s
 * {@code physicalTenantId}). This verifies that work done in one tenant is invisible to another: a
 * process deployed only to tenant A can be instantiated and its job completed there, while the
 * default tenant neither knows the process nor sees its jobs.
 */
@ZeebeIntegration
final class PhysicalTenantIsolationIT {

  private static final String TENANT_A = "tenanta";
  private static final String JOB_TYPE = "task";

  // the default tenant and tenant A both run broker-only (no secondary storage, M1 scope);
  // declaring tenant A starts a second, fully isolated partition group / engine for it
  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.none())
          .withTenant(TENANT_A, Storage.none())
          .build();

  @TestZeebe
  private final TestStandaloneBroker broker =
      TENANTS.configure(new TestStandaloneBroker().withUnauthenticatedAccess());

  @AutoClose private CamundaClient defaultClient;
  @AutoClose private CamundaClient tenantAClient;

  @BeforeEach
  void beforeEach() {
    defaultClient =
        TENANTS.newClientBuilder(broker, PhysicalTenantsITHelper.DEFAULT_TENANT_ID).build();
    tenantAClient = TENANTS.newClientBuilder(broker, TENANT_A).build();
  }

  @Test
  void shouldExecuteProcessInTargetedTenantWithoutAffectingOthers() {
    // given - a process deployed only to tenant A
    final String processId = "isolation-process";
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
            .endEvent()
            .done();

    // tenant A's partition group may need a moment to elect a leader after startup; retry the
    // first command until it lands (its topology is not observable via the default topology RPC)
    await("deployment to tenant A succeeds")
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(
                        tenantAClient
                            .newDeployResourceCommand()
                            .addProcessModel(process, processId + ".bpmn")
                            .send()
                            .join()
                            .getProcesses())
                    .isNotEmpty());

    // when - an instance is created in tenant A, making a job available there
    final long processInstanceKey =
        tenantAClient
            .newCreateInstanceCommand()
            .bpmnProcessId(processId)
            .latestVersion()
            .send()
            .join()
            .getProcessInstanceKey();

    // then - the default tenant cannot see that job while it is still live in tenant A: the job
    // lives in tenant A's partition group and never leaks across the tenant boundary
    final ActivateJobsResponse defaultJobs =
        defaultClient
            .newActivateJobsCommand()
            .jobType(JOB_TYPE)
            .maxJobsToActivate(10)
            .requestTimeout(Duration.ofSeconds(2))
            .send()
            .join();
    assertThat(defaultJobs.getJobs()).isEmpty();

    // and - tenant A sees the job for its own instance and can complete it
    final ActivateJobsResponse tenantAJobs =
        tenantAClient
            .newActivateJobsCommand()
            .jobType(JOB_TYPE)
            .maxJobsToActivate(10)
            .send()
            .join();
    assertThat(tenantAJobs.getJobs()).hasSize(1);
    assertThat(tenantAJobs.getJobs().get(0).getProcessInstanceKey()).isEqualTo(processInstanceKey);
    tenantAClient.newCompleteCommand(tenantAJobs.getJobs().get(0).getKey()).send().join();

    // and - the process was never deployed to the default tenant
    assertThatThrownBy(
            () ->
                defaultClient
                    .newCreateInstanceCommand()
                    .bpmnProcessId(processId)
                    .latestVersion()
                    .send()
                    .join())
        .isInstanceOf(ClientStatusException.class);
  }
}
