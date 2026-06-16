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

  @TestZeebe
  private final TestStandaloneBroker broker =
      new TestStandaloneBroker()
          .withUnauthenticatedAccess()
          // the default tenant uses no secondary storage (broker-only, M1 scope) ...
          .withProperty("camunda.data.secondary-storage.type", "none")
          // ... and declaring tenant A with a matching storage type starts a second, fully
          // isolated partition group / engine for it
          .withProperty(
              "camunda.physical-tenants." + TENANT_A + ".data.secondary-storage.type", "none")
          // each explicitly-configured physical tenant must provide its own initialization block
          .withProperty(
              "camunda.physical-tenants."
                  + TENANT_A
                  + ".security.initialization.default-roles.admin.users[0]",
              TENANT_A + "-admin");

  @AutoClose private CamundaClient defaultClient;
  @AutoClose private CamundaClient tenantAClient;

  @BeforeEach
  void beforeEach() {
    defaultClient = broker.newClientBuilder().build();
    tenantAClient = broker.newClientBuilder().physicalTenantId(TENANT_A).build();
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

    // when - an instance is created and activated in tenant A
    final long processInstanceKey =
        tenantAClient
            .newCreateInstanceCommand()
            .bpmnProcessId(processId)
            .latestVersion()
            .send()
            .join()
            .getProcessInstanceKey();

    final ActivateJobsResponse tenantAJobs =
        tenantAClient
            .newActivateJobsCommand()
            .jobType(JOB_TYPE)
            .maxJobsToActivate(10)
            .send()
            .join();

    // then - tenant A sees and can complete the job for its own instance
    assertThat(tenantAJobs.getJobs()).hasSize(1);
    assertThat(tenantAJobs.getJobs().get(0).getProcessInstanceKey()).isEqualTo(processInstanceKey);
    tenantAClient.newCompleteCommand(tenantAJobs.getJobs().get(0).getKey()).send().join();

    // and - the default tenant is unaffected: the process was never deployed there
    assertThatThrownBy(
            () ->
                defaultClient
                    .newCreateInstanceCommand()
                    .bpmnProcessId(processId)
                    .latestVersion()
                    .send()
                    .join())
        .isInstanceOf(ClientStatusException.class);

    // ... and none of tenant A's jobs leaked into the default tenant
    final ActivateJobsResponse defaultJobs =
        defaultClient
            .newActivateJobsCommand()
            .jobType(JOB_TYPE)
            .maxJobsToActivate(10)
            .requestTimeout(Duration.ofSeconds(2))
            .send()
            .join();
    assertThat(defaultJobs.getJobs()).isEmpty();
  }
}
