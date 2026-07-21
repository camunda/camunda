/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.physicaltenant;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.qa.util.actuator.JobStreamActuator;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.jobstream.JobStreamActuatorAssert;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.agrona.CloseHelper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Verifies per-physical-tenant isolation of gRPC job streaming: a client streaming jobs from one
 * physical tenant never receives jobs activated on another physical tenant's partitions, even when
 * both streams share the exact same job type. Also verifies that a client which does not set the
 * {@code Camunda-Physical-Tenant} header (i.e. the {@code default} physical tenant client built by
 * {@link PhysicalTenantsITHelper}) only ever receives jobs from the {@code default} physical
 * tenant.
 */
@ZeebeIntegration
final class JobStreamPhysicalTenantIsolationIT {

  private static final String TENANT_A = "tenanta";
  private static final String PROCESS_ID = "job-stream-isolation-proc";
  private static final String JOB_TYPE = "job-stream-isolation-type";

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.none())
          .withTenant(TENANT_A, Storage.none())
          .build();

  @TestZeebe(autoStart = false, purgeAfterEach = false)
  private static final TestStandaloneBroker BROKER =
      TENANTS.configure(new TestStandaloneBroker().withUnauthenticatedAccess());

  private static CamundaClient defaultClient;
  private static CamundaClient tenantAClient;

  @BeforeAll
  static void start() {
    BROKER.start();
    defaultClient =
        TENANTS
            .newClientBuilder(BROKER, PhysicalTenantsITHelper.DEFAULT_TENANT_ID)
            .preferRestOverGrpc(false)
            .build();
    tenantAClient = TENANTS.newClientBuilder(BROKER, TENANT_A).preferRestOverGrpc(false).build();

    deployServiceTaskProcess(defaultClient);
    deployServiceTaskProcess(tenantAClient);
  }

  @AfterAll
  static void close() {
    CloseHelper.quietCloseAll(defaultClient, tenantAClient);
  }

  @Test
  void shouldIsolateJobStreamsAcrossPhysicalTenants() {
    // given: a stream opened per physical tenant, both for the exact same job type, so the
    // isolation boundary being tested is the physical tenant group and not the job type
    final List<ActivatedJob> defaultJobs = new CopyOnWriteArrayList<>();
    final List<ActivatedJob> tenantAJobs = new CopyOnWriteArrayList<>();

    final var defaultStream =
        defaultClient.newStreamJobsCommand().jobType(JOB_TYPE).consumer(defaultJobs::add).send();
    final var tenantAStream =
        tenantAClient.newStreamJobsCommand().jobType(JOB_TYPE).consumer(tenantAJobs::add).send();

    try {
      awaitStreamsRegistered(2);

      // when: a job is activated on the default physical tenant's partitions
      defaultClient
          .newCreateInstanceCommand()
          .bpmnProcessId(PROCESS_ID)
          .latestVersion()
          .send()
          .join();

      // then: only the default tenant's stream receives it, stamped with the default tenant id
      Awaitility.await("job streamed for the default physical tenant")
          .atMost(Duration.ofSeconds(30))
          .untilAsserted(
              () -> {
                assertThat(defaultJobs).hasSize(1);
                assertThat(defaultJobs.getFirst().getPhysicalTenantId())
                    .isEqualTo(PhysicalTenantsITHelper.DEFAULT_TENANT_ID);
              });
      assertThat(tenantAJobs).as("tenant A must not receive the default tenant's job").isEmpty();

      // when: a job is activated on tenant A's partitions
      tenantAClient
          .newCreateInstanceCommand()
          .bpmnProcessId(PROCESS_ID)
          .latestVersion()
          .send()
          .join();

      // then: only tenant A's stream receives it, and the default tenant's stream is unaffected
      Awaitility.await("job streamed for physical tenant A")
          .atMost(Duration.ofSeconds(30))
          .untilAsserted(
              () -> {
                assertThat(tenantAJobs).hasSize(1);
                assertThat(tenantAJobs.getFirst().getPhysicalTenantId()).isEqualTo(TENANT_A);
              });
      assertThat(defaultJobs).as("the default tenant must not receive tenant A's job").hasSize(1);
    } finally {
      defaultStream.cancel(true);
      tenantAStream.cancel(true);
    }
  }

  private static void deployServiceTaskProcess(final CamundaClient client) {
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
            .endEvent()
            .done();
    Awaitility.await("process deployed to the tenant's partitions")
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var deployment =
                  client
                      .newDeployResourceCommand()
                      .addProcessModel(model, PROCESS_ID + ".bpmn")
                      .send()
                      .join();
              assertThat(deployment.getProcesses()).isNotEmpty();
            });
  }

  private static void awaitStreamsRegistered(final int expectedCount) {
    final var actuator = JobStreamActuator.of(BROKER);
    Awaitility.await(
            "until %d streams with type '%s' are registered".formatted(expectedCount, JOB_TYPE))
        .untilAsserted(
            () ->
                JobStreamActuatorAssert.assertThat(actuator)
                    .remoteStreams()
                    .haveJobType(expectedCount, JOB_TYPE));
  }
}
