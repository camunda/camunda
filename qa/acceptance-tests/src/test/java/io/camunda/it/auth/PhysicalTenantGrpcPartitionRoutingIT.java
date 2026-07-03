/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
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
 * Verifies that the physical-tenant id stamped from the {@code Camunda-Physical-Tenant} gRPC header
 * is used by the gateway's {@code EndpointManager} to route partition-scoped commands to the
 * targeted physical tenant's own partition group: a command accepted on one physical tenant's
 * partitions is rejected when issued against a different physical tenant, because the state it
 * depends on does not exist there.
 */
@ZeebeIntegration
final class PhysicalTenantGrpcPartitionRoutingIT {

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

  private static CamundaClient tenantAClient;
  private static CamundaClient tenantBClient;

  @BeforeAll
  static void start() {
    BROKER.start();
    tenantAClient = TENANTS.newClientBuilder(BROKER, TENANT_A).preferRestOverGrpc(false).build();
    tenantBClient = TENANTS.newClientBuilder(BROKER, TENANT_B).preferRestOverGrpc(false).build();
  }

  @AfterAll
  static void close() {
    CloseHelper.quietCloseAll(tenantAClient, tenantBClient);
  }

  @Test
  void shouldRoutePartitionCommandToStampedPhysicalTenant() {
    // given
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess(PROCESS_ID).startEvent().endEvent().done();
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
              assertThat(deployment.getProcesses().get(0).getProcessDefinitionKey()).isPositive();
            });

    // when/then: create-instance for the same PT lands in the same partition group as the deploy
    final var instanceEvent =
        tenantAClient
            .newCreateInstanceCommand()
            .bpmnProcessId(PROCESS_ID)
            .latestVersion()
            .send()
            .join();
    assertThat(instanceEvent.getProcessInstanceKey()).isPositive();

    // when/then: the same command against a different physical tenant is rejected, since the
    // process definition was never routed to tenant B's partitions
    assertThatThrownBy(
            () ->
                tenantBClient
                    .newCreateInstanceCommand()
                    .bpmnProcessId(PROCESS_ID)
                    .latestVersion()
                    .send()
                    .join())
        .as("process deployed only to tenant A must not be visible on tenant B's partitions")
        .hasRootCauseInstanceOf(StatusRuntimeException.class)
        .rootCause()
        .satisfies(
            e ->
                assertThat(((StatusRuntimeException) e).getStatus().getCode())
                    .isEqualTo(Status.Code.NOT_FOUND));
  }
}
