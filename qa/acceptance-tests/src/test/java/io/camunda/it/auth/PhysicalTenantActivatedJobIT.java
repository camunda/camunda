/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import org.agrona.CloseHelper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Verifies that a job activated through a physical-tenant-scoped gRPC client reports the physical
 * tenant it was routed to via {@link
 * io.camunda.client.api.response.ActivatedJob#getPhysicalTenantId()}. Exercises the full chain:
 * {@code Camunda-Physical-Tenant} header → gateway routing → response mapping → Java client, on a
 * live multi-physical-tenant cluster.
 */
@ZeebeIntegration
final class PhysicalTenantActivatedJobIT {

  private static final String TENANT_A = "tenanta";
  private static final String PROCESS_ID = "job-proc";
  private static final String JOB_TYPE = "job-type";

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.none())
          .withTenant(TENANT_A, Storage.none())
          .build();

  @TestZeebe(autoStart = false, purgeAfterEach = false)
  private static final TestStandaloneBroker BROKER =
      TENANTS.configure(new TestStandaloneBroker().withUnauthenticatedAccess());

  private static CamundaClient tenantAClient;

  @BeforeAll
  static void start() {
    BROKER.start();
    tenantAClient = TENANTS.newClientBuilder(BROKER, TENANT_A).preferRestOverGrpc(false).build();
  }

  @AfterAll
  static void close() {
    CloseHelper.quietCloseAll(tenantAClient);
  }

  @Test
  void shouldStampPhysicalTenantIdOnActivatedJob() {
    // given: a process with a service task deployed to tenant A's partitions
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
            .endEvent()
            .done();
    Awaitility.await("process deployed to tenant A's partitions")
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var deployment =
                  tenantAClient
                      .newDeployResourceCommand()
                      .addProcessModel(model, PROCESS_ID + ".bpmn")
                      .send()
                      .join();
              assertThat(deployment.getProcesses()).isNotEmpty();
            });

    // and: an instance that creates a job of the given type
    tenantAClient
        .newCreateInstanceCommand()
        .bpmnProcessId(PROCESS_ID)
        .latestVersion()
        .send()
        .join();

    // when/then: the activated job reports tenant A as its physical tenant
    Awaitility.await("job activated on tenant A")
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var response =
                  tenantAClient
                      .newActivateJobsCommand()
                      .jobType(JOB_TYPE)
                      .maxJobsToActivate(1)
                      .send()
                      .join();
              assertThat(response.getJobs()).isNotEmpty();
              assertThat(response.getJobs().getFirst().getPhysicalTenantId()).isEqualTo(TENANT_A);
            });
  }
}
