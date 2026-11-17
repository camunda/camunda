/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.physicaltenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientStatusException;
import io.camunda.zeebe.broker.Broker;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.grpc.Status.Code;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * End-to-end test of physical tenant isolation with respect to disk space: the {@code
 * DiskSpaceUsageMonitor} is a single broker-wide component shared by every physical tenant's
 * partition group, unlike per-tenant flow control (see {@link
 * PhysicalTenantBackpressureIsolationIT}). Running out of disk space is therefore a shared-fate
 * event: it must reject writes on every physical tenant, and recovering disk space must restore
 * writes on every physical tenant.
 *
 * <p>Writes are exercised with {@code publishMessage}, which is routed to a single partition by its
 * correlation key. Unlike {@code createProcessInstance}, whose command the gateway attempts on
 * every partition in turn (collapsing the per-partition rejection into a generic "all partitions
 * failed" error), a partition-pinned command surfaces the broker's exact rejection reason to the
 * client - the same reason {@code DiskSpaceRecoveryTest} asserts on.
 */
@ZeebeIntegration
final class PhysicalTenantDiskUsageIsolationIT {

  private static final String TENANT_A = "tenanta";

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.none())
          .withTenant(TENANT_A, Storage.none())
          .build();

  @TestZeebe
  private final TestStandaloneBroker broker =
      TENANTS.configure(
          new TestStandaloneBroker()
              .withUnauthenticatedAccess()
              // a short monitoring interval so the shared disk space monitor re-checks the
              // (test-overridden) free space supplier quickly, keeping the test fast
              .withDataConfig(
                  data ->
                      data.getPrimaryStorage()
                          .getDisk()
                          .setMonitoringInterval(Duration.ofMillis(100))));

  @AutoClose private CamundaClient defaultClient;
  @AutoClose private CamundaClient tenantAClient;

  @BeforeEach
  void beforeEach() {
    defaultClient =
        TENANTS.newClientBuilder(broker, PhysicalTenantsITHelper.DEFAULT_TENANT_ID).build();
    tenantAClient = TENANTS.newClientBuilder(broker, TENANT_A).build();

    // each physical tenant's partition group may need a moment to elect a leader after startup;
    // wait until each tenant accepts a write before the disk space is manipulated (a non-default
    // tenant's leadership is not observable via the default topology RPC)
    awaitWritesAccepted(defaultClient);
    awaitWritesAccepted(tenantAClient);
  }

  @Test
  void shouldRejectWritesForAllPhysicalTenantsWhenDiskFull() {
    // given - the broker's single, shared disk space monitor reports the disk as full
    final var diskSpaceMonitor =
        broker.bean(Broker.class).getBrokerContext().getDiskSpaceUsageMonitor();
    diskSpaceMonitor.setFreeDiskSpaceSupplier(() -> 0L);

    // when/then - both the default tenant's and tenant A's writes are rejected: disk space is a
    // broker-wide resource, so the outage is not scoped to a single physical tenant
    await("default tenant rejects writes once the shared disk space monitor sees the disk as full")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(() -> assertRejectedForOutOfDiskSpace(defaultClient));
    await("tenant A rejects writes once the shared disk space monitor sees the disk as full")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(() -> assertRejectedForOutOfDiskSpace(tenantAClient));
  }

  @Test
  void shouldRecoverWritesForAllPhysicalTenantsWhenDiskSpaceAvailableAgain() {
    // given - the disk is full and both tenants are rejecting writes
    final var diskSpaceMonitor =
        broker.bean(Broker.class).getBrokerContext().getDiskSpaceUsageMonitor();
    diskSpaceMonitor.setFreeDiskSpaceSupplier(() -> 0L);
    await("default tenant rejects writes once the shared disk space monitor sees the disk as full")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(() -> assertRejectedForOutOfDiskSpace(defaultClient));
    await("tenant A rejects writes once the shared disk space monitor sees the disk as full")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(() -> assertRejectedForOutOfDiskSpace(tenantAClient));

    // when - disk space becomes available again
    diskSpaceMonitor.setFreeDiskSpaceSupplier(() -> Long.MAX_VALUE);

    // then - both the default tenant and tenant A recover and accept writes again
    awaitWritesAccepted(defaultClient);
    awaitWritesAccepted(tenantAClient);
  }

  private static void awaitWritesAccepted(final CamundaClient client) {
    await("tenant accepts writes")
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .untilAsserted(
            () -> assertThatCode(() -> publishMessage(client)).doesNotThrowAnyException());
  }

  private static void assertRejectedForOutOfDiskSpace(final CamundaClient client) {
    assertThatThrownBy(() -> publishMessage(client))
        .isInstanceOf(ClientStatusException.class)
        .hasStackTraceContaining("Broker is out of disk space")
        .satisfies(
            t ->
                assertThat(((ClientStatusException) t).getStatusCode())
                    .isEqualTo(Code.RESOURCE_EXHAUSTED));
  }

  // publishMessage is routed to a single partition by its correlation key, so the broker's
  // per-partition rejection reason is preserved on the client exception; each call uses a unique
  // correlation key so buffered messages never accumulate against a fixed key
  private static void publishMessage(final CamundaClient client) {
    client
        .newPublishMessageCommand()
        .messageName("disk-full-msg")
        .correlationKey(UUID.randomUUID().toString())
        .send()
        .join();
  }
}
