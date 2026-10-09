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
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.zeebe.broker.Broker;
import io.camunda.zeebe.broker.system.partitions.ZeebePartition;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies that, with the physical tenant actor pool enabled, a tenant that blocks all of its actor
 * threads does not stall its neighbour. Tenant A's pool has a single CPU thread which the test
 * occupies, standing in for a long-running job such as a slow FEEL evaluation.
 *
 * <p>With the flag disabled, the same blocked thread belongs to the broker-wide scheduler that
 * every tenant shares, so the neighbour's writes stall as well.
 */
@ZeebeIntegration
final class PhysicalTenantActorPoolIsolationIT {

  private static final String TENANT_A = "tenanta";

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.none())
          .withTenant(TENANT_A, Storage.none())
          .build();

  @TestZeebe
  private final TestStandaloneBroker broker =
      TENANTS
          .configure(new TestStandaloneBroker().withUnauthenticatedAccess())
          .withUnifiedConfig(
              camunda -> camunda.getSystem().getPhysicalTenantActorPool().setEnabled(true))
          .withPtConfig(
              TENANT_A,
              camunda -> camunda.getSystem().getPhysicalTenantActorPool().setCpuThreadCount(1));

  @AutoClose private CamundaClient defaultClient;
  @AutoClose private CamundaClient tenantAClient;

  @BeforeEach
  void beforeEach() {
    defaultClient =
        TENANTS.newClientBuilder(broker, PhysicalTenantsITHelper.DEFAULT_TENANT_ID).build();
    tenantAClient = TENANTS.newClientBuilder(broker, TENANT_A).build();

    awaitWritesAccepted(defaultClient);
    awaitWritesAccepted(tenantAClient);
  }

  @Test
  void shouldNotStallNeighbourWhenTenantBlocksItsActorThreads() throws InterruptedException {
    // given - tenant A's only CPU thread is blocked
    final var release = new CountDownLatch(1);
    final var blockedThread = new AtomicReference<String>();
    final var tenantAPartition = awaitPartition(TENANT_A);
    tenantAPartition.run(
        () -> {
          blockedThread.set(Thread.currentThread().getName());
          try {
            release.await();
          } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        });

    try {
      await("tenant A's actor thread is blocked")
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(() -> assertThat(blockedThread).hasValue("tenanta-zb-actors-0"));

      // when - then: the default tenant keeps accepting and processing writes
      for (int i = 0; i < 20; i++) {
        assertThatCode(() -> publishMessage(defaultClient)).doesNotThrowAnyException();
      }
    } finally {
      release.countDown();
    }

    // and tenant A recovers once its thread is released
    awaitWritesAccepted(tenantAClient);
  }

  private ZeebePartition awaitPartition(final String tenantId) {
    final var reference = new AtomicReference<ZeebePartition>();
    await("partition of " + tenantId + " exists")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              final var partitions =
                  broker
                      .bean(Broker.class)
                      .getBrokerContext()
                      .getPartitionManagers()
                      .get(tenantId)
                      .getZeebePartitions();
              assertThat(partitions).isNotEmpty();
              reference.set(partitions.iterator().next());
            });
    return reference.get();
  }

  private static void awaitWritesAccepted(final CamundaClient client) {
    await("tenant accepts writes")
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .untilAsserted(
            () -> assertThatCode(() -> publishMessage(client)).doesNotThrowAnyException());
  }

  private static void publishMessage(final CamundaClient client) {
    client
        .newPublishMessageCommand()
        .messageName("actor-pool-msg")
        .correlationKey(UUID.randomUUID().toString())
        .requestTimeout(Duration.ofSeconds(5))
        .send()
        .join();
  }
}
