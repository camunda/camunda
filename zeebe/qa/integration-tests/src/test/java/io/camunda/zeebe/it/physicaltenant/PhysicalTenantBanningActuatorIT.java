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
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.qa.util.actuator.BanningActuator;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the {@code banning} actuator's {@code physicalTenant} query parameter routes a ban
 * to the given physical tenant's partition group, leaving other physical tenants' instances
 * unaffected, while omitting the parameter still targets the default physical tenant (today's
 * behavior).
 */
@ZeebeIntegration
final class PhysicalTenantBanningActuatorIT {

  private static final String TENANT_A = "tenanta";
  private static final String PROCESS_ID = "banning-process";

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.none())
          .withTenant(TENANT_A, Storage.none())
          .build();

  @TestZeebe
  private final TestStandaloneBroker broker =
      TENANTS.configure(
          new TestStandaloneBroker().withUnauthenticatedAccess().withRecordingExporter(true));

  @AutoClose private CamundaClient defaultClient;
  @AutoClose private CamundaClient tenantAClient;

  private BanningActuator actuator;

  @BeforeEach
  void beforeEach() {
    defaultClient =
        TENANTS.newClientBuilder(broker, PhysicalTenantsITHelper.DEFAULT_TENANT_ID).build();
    tenantAClient = TENANTS.newClientBuilder(broker, TENANT_A).build();
    actuator = BanningActuator.of(broker);
  }

  @Test
  void shouldBanInstanceOnTargetedPhysicalTenantOnly() {
    // given - a process instance in tenant A and one in the default tenant
    final var process = Bpmn.createExecutableProcess(PROCESS_ID).startEvent().endEvent().done();

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
                            .addProcessModel(process, PROCESS_ID + ".bpmn")
                            .send()
                            .join()
                            .getProcesses())
                    .isNotEmpty());
    defaultClient
        .newDeployResourceCommand()
        .addProcessModel(process, PROCESS_ID + ".bpmn")
        .send()
        .join();

    final long tenantAInstanceKey =
        tenantAClient
            .newCreateInstanceCommand()
            .bpmnProcessId(PROCESS_ID)
            .latestVersion()
            .send()
            .join()
            .getProcessInstanceKey();
    final long defaultInstanceKey =
        defaultClient
            .newCreateInstanceCommand()
            .bpmnProcessId(PROCESS_ID)
            .latestVersion()
            .send()
            .join()
            .getProcessInstanceKey();

    // when - banning tenant A's instance via the physicalTenant parameter
    actuator.ban(tenantAInstanceKey, TENANT_A);

    // then - tenant A's instance is banned...
    await("tenant A instance is banned")
        .untilAsserted(() -> assertThat(hasErrorEventFor(tenantAInstanceKey)).isTrue());
    // and - the default tenant's instance is unaffected by that call
    assertThat(noErrorEventFor(defaultInstanceKey)).isTrue();

    // when - banning the default tenant's instance without specifying a physicalTenant, matching
    // today's default behavior
    actuator.ban(defaultInstanceKey);

    // then - the default tenant's instance is banned too
    await("default tenant instance is banned")
        .untilAsserted(() -> assertThat(hasErrorEventFor(defaultInstanceKey)).isTrue());
  }

  private static boolean hasErrorEventFor(final long key) {
    return RecordingExporter.records()
        .filter(record -> record.getRecordType() == RecordType.EVENT)
        .filter(record -> record.getValueType() == ValueType.ERROR)
        .filter(record -> record.getKey() == key)
        .exists();
  }

  private static boolean noErrorEventFor(final long key) {
    return RecordingExporter.expectNoMatchingRecords(
        records ->
            !records
                .filter(record -> record.getRecordType() == RecordType.EVENT)
                .filter(record -> record.getValueType() == ValueType.ERROR)
                .filter(record -> record.getKey() == key)
                .exists());
  }
}
