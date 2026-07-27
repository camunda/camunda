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
 * Verifies that a job activated through a physical-tenant-scoped client reports the physical tenant
 * it was routed to via {@link io.camunda.client.api.response.ActivatedJob#getPhysicalTenantId()},
 * for polling over gRPC and REST and for gRPC job streaming. Exercises the full chain:
 * physical-tenant routing (gRPC {@code Camunda-Physical-Tenant} header / REST tenant-scoped path) →
 * gateway → response mapping → Java client, on a live multi-physical-tenant cluster.
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

  private static CamundaClient tenantAGrpcClient;
  private static CamundaClient tenantARestClient;

  @BeforeAll
  static void start() {
    BROKER.start();
    tenantAGrpcClient =
        TENANTS.newClientBuilder(BROKER, TENANT_A).preferRestOverGrpc(false).build();
    tenantARestClient = TENANTS.newClientBuilder(BROKER, TENANT_A).preferRestOverGrpc(true).build();
  }

  @AfterAll
  static void close() {
    CloseHelper.quietCloseAll(tenantAGrpcClient, tenantARestClient);
  }

  @Test
  void shouldStampPhysicalTenantIdOnActivatedJobOverGrpc() {
    assertActivatedJobReportsPhysicalTenant(tenantAGrpcClient, "grpc");
  }

  @Test
  void shouldStampPhysicalTenantIdOnActivatedJobOverRest() {
    assertActivatedJobReportsPhysicalTenant(tenantARestClient, "rest");
  }

  @Test
  void shouldStampPhysicalTenantIdOnStreamedJob() {
    // given: a process deployed to tenant A and a job stream opened by tenant A's client
    final String processId = PROCESS_ID + "-stream";
    final String jobType = JOB_TYPE + "-stream";
    final List<ActivatedJob> streamedJobs = new CopyOnWriteArrayList<>();
    deployServiceTaskProcess(tenantAGrpcClient, processId, jobType);

    final var stream =
        tenantAGrpcClient
            .newStreamJobsCommand()
            .jobType(jobType)
            .consumer(streamedJobs::add)
            .send();
    try {
      awaitStreamRegistered(jobType);

      // when: an instance that creates a job of the given type is started on tenant A
      tenantAGrpcClient
          .newCreateInstanceCommand()
          .bpmnProcessId(processId)
          .latestVersion()
          .send()
          .join();

      // then: the streamed job reports tenant A as its physical tenant
      Awaitility.await("job streamed for tenant A")
          .atMost(Duration.ofSeconds(30))
          .untilAsserted(
              () -> {
                assertThat(streamedJobs).isNotEmpty();
                assertThat(streamedJobs.getFirst().getPhysicalTenantId()).isEqualTo(TENANT_A);
              });
    } finally {
      stream.cancel(true);
    }
  }

  private static void assertActivatedJobReportsPhysicalTenant(
      final CamundaClient client, final String qualifier) {
    // given: a process with a service task deployed to tenant A's partitions, and an instance
    final String processId = PROCESS_ID + "-" + qualifier;
    final String jobType = JOB_TYPE + "-" + qualifier;
    deployServiceTaskProcess(client, processId, jobType);
    client.newCreateInstanceCommand().bpmnProcessId(processId).latestVersion().send().join();

    // when/then: the activated job reports tenant A as its physical tenant
    Awaitility.await("job activated on tenant A")
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var response =
                  client
                      .newActivateJobsCommand()
                      .jobType(jobType)
                      .maxJobsToActivate(1)
                      .send()
                      .join();
              assertThat(response.getJobs()).isNotEmpty();
              assertThat(response.getJobs().getFirst().getPhysicalTenantId()).isEqualTo(TENANT_A);
            });
  }

  private static void deployServiceTaskProcess(
      final CamundaClient client, final String processId, final String jobType) {
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(jobType))
            .endEvent()
            .done();
    Awaitility.await("process deployed to tenant A's partitions")
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var deployment =
                  client
                      .newDeployResourceCommand()
                      .addProcessModel(model, processId + ".bpmn")
                      .send()
                      .join();
              assertThat(deployment.getProcesses()).isNotEmpty();
            });
  }

  private static void awaitStreamRegistered(final String jobType) {
    final var actuator = JobStreamActuator.of(BROKER);
    Awaitility.await("until stream with type '%s' is registered".formatted(jobType))
        .untilAsserted(
            () ->
                JobStreamActuatorAssert.assertThat(actuator)
                    .remoteStreams()
                    .haveJobType(1, jobType));
  }
}
