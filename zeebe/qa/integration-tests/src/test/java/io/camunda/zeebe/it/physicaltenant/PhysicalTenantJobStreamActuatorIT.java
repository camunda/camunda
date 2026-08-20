/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.physicaltenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.zeebe.qa.util.actuator.JobStreamActuator;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.Strings;
import java.time.Duration;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the {@code jobstreams} actuator's {@code physicalTenant} query parameter filters
 * remote job streams to the given physical tenant, while omitting the parameter still returns
 * streams across every physical tenant (today's whole-cluster behavior).
 */
@ZeebeIntegration
final class PhysicalTenantJobStreamActuatorIT {

  private static final String TENANT_A = "tenanta";

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.none())
          .withTenant(TENANT_A, Storage.none())
          .build();

  @TestZeebe
  private final TestStandaloneBroker broker =
      TENANTS.configure(new TestStandaloneBroker().withUnauthenticatedAccess());

  @AutoClose private CamundaClient tenantAClient;

  private JobStreamActuator actuator;

  @BeforeEach
  void beforeEach() {
    tenantAClient = TENANTS.newClientBuilder(broker, TENANT_A).build();
    actuator = JobStreamActuator.of(broker);
  }

  @Test
  void shouldListRemoteStreamsForTargetedPhysicalTenantOnly() {
    // given - a job stream registered only on tenant A
    final var jobType = Strings.newRandomValidBpmnId();
    tenantAClient
        .newStreamJobsCommand()
        .jobType(jobType)
        .consumer(ignored -> {})
        .workerName("worker")
        .timeout(Duration.ofSeconds(1))
        .send();

    // when + then - the stream is visible when scoped to tenant A...
    await("tenant A's remote stream is registered")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              final var streams = actuator.listRemote(TENANT_A);
              assertThat(streams).hasSize(1);
              assertThat(streams.get(0).jobType()).isEqualTo(jobType);
            });

    // and - it is not visible when scoped to the default physical tenant
    assertThat(actuator.listRemote(PhysicalTenantsITHelper.DEFAULT_TENANT_ID)).isEmpty();
  }

  @Test
  void shouldListClientStreamsForTargetedPhysicalTenantOnly() {
    // given - a job stream registered only on tenant A, from the gateway's (client-side) point of
    // view
    final var jobType = Strings.newRandomValidBpmnId();
    tenantAClient
        .newStreamJobsCommand()
        .jobType(jobType)
        .consumer(ignored -> {})
        .workerName("worker")
        .timeout(Duration.ofSeconds(1))
        .send();

    // when + then - the stream is visible when scoped to tenant A...
    await("tenant A's client stream is registered")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              final var streams = actuator.listClient(TENANT_A);
              assertThat(streams).hasSize(1);
              assertThat(streams.get(0).jobType()).isEqualTo(jobType);
            });

    // and - it is not visible when scoped to the default physical tenant
    assertThat(actuator.listClient(PhysicalTenantsITHelper.DEFAULT_TENANT_ID)).isEmpty();
  }
}
