/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.physicaltenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.time.Duration;
import org.agrona.CloseHelper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Verifies that a client scoped to a physical tenant is routed to that tenant's own partition
 * group, over both gRPC and REST. The physical tenant is stamped from the gRPC {@code
 * Camunda-Physical-Tenant} header and, for REST, from the client-applied {@code
 * /physical-tenants/<id>} path prefix. A command accepted on one physical tenant's partitions is
 * therefore rejected when issued against a different physical tenant, because the state it depends
 * on does not exist there.
 */
@ZeebeIntegration
final class PhysicalTenantRoutingIT {

  private static final String TENANT_A = "tenanta";
  private static final String TENANT_B = "tenantb";
  private static final String PROCESS_ID = "routing-proc";

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.none())
          .withTenant(TENANT_A, Storage.none())
          .withTenant(TENANT_B, Storage.none())
          .build();

  @TestZeebe(autoStart = false, purgeAfterEach = false)
  private static final TestStandaloneBroker BROKER =
      TENANTS.configure(new TestStandaloneBroker().withUnauthenticatedAccess());

  private static CamundaClient tenantAGrpcClient;
  private static CamundaClient tenantBGrpcClient;
  private static CamundaClient tenantARestClient;
  private static CamundaClient tenantBRestClient;

  @BeforeAll
  static void start() {
    BROKER.start();
    tenantAGrpcClient =
        TENANTS.newClientBuilder(BROKER, TENANT_A).preferRestOverGrpc(false).build();
    tenantBGrpcClient =
        TENANTS.newClientBuilder(BROKER, TENANT_B).preferRestOverGrpc(false).build();
    tenantARestClient = TENANTS.newClientBuilder(BROKER, TENANT_A).preferRestOverGrpc(true).build();
    tenantBRestClient = TENANTS.newClientBuilder(BROKER, TENANT_B).preferRestOverGrpc(true).build();
  }

  @AfterAll
  static void close() {
    CloseHelper.quietCloseAll(
        tenantAGrpcClient, tenantBGrpcClient, tenantARestClient, tenantBRestClient);
  }

  @Test
  void shouldRouteGrpcCommandsToScopedPhysicalTenant() {
    // given: a process deployed to tenant A's partitions over gRPC, and an instance created there
    final String processId = PROCESS_ID + "-grpc";
    deployProcess(tenantAGrpcClient, processId);
    assertThat(createInstance(tenantAGrpcClient, processId)).isPositive();

    // when/then: the same command against a different physical tenant is rejected, since the
    // process definition was never routed to tenant B's partitions
    assertThatThrownBy(() -> createInstance(tenantBGrpcClient, processId))
        .as("process deployed only to tenant A must not be visible on tenant B over gRPC")
        .hasRootCauseInstanceOf(StatusRuntimeException.class)
        .rootCause()
        .satisfies(
            e ->
                assertThat(((StatusRuntimeException) e).getStatus().getCode())
                    .isEqualTo(Status.Code.NOT_FOUND));
  }

  @Test
  void shouldRouteRestCommandsToScopedPhysicalTenant() {
    // given: a process deployed to tenant A's partitions over REST, and an instance created there
    final String processId = PROCESS_ID + "-rest";
    deployProcess(tenantARestClient, processId);
    assertThat(createInstance(tenantARestClient, processId)).isPositive();

    // when/then: the same command against a different physical tenant is rejected, since the
    // process definition was never routed to tenant B's partitions
    assertThatThrownBy(() -> createInstance(tenantBRestClient, processId))
        .as("process deployed only to tenant A must not be visible on tenant B over REST")
        .isInstanceOf(ProblemException.class)
        .satisfies(e -> assertThat(((ProblemException) e).code()).isEqualTo(404));
  }

  private static void deployProcess(final CamundaClient client, final String processId) {
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess(processId).startEvent().endEvent().done();
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

  private static long createInstance(final CamundaClient client, final String processId) {
    return client
        .newCreateInstanceCommand()
        .bpmnProcessId(processId)
        .latestVersion()
        .send()
        .join()
        .getProcessInstanceKey();
  }
}
