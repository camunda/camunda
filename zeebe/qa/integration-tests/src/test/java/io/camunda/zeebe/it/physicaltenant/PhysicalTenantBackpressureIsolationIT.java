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
import io.camunda.client.api.command.ClientStatusException;
import io.camunda.zeebe.broker.Broker;
import io.camunda.zeebe.broker.system.configuration.FlowControlCfg;
import io.camunda.zeebe.broker.system.configuration.backpressure.RateLimitCfg;
import io.camunda.zeebe.broker.system.partitions.ZeebePartition;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.grpc.Status.Code;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * End-to-end test of physical tenant isolation with respect to backpressure: {@code FlowControl} is
 * instantiated per (physical tenant, partition), unlike the shared, broker-wide disk space monitor
 * (see {@link PhysicalTenantDiskUsageIsolationIT}). Backpressuring one physical tenant's engine
 * must therefore not degrade another physical tenant's engine.
 *
 * <p>Writes are exercised with {@code publishMessage}, which is routed to a single partition by its
 * correlation key, so the broker's exact rejection reason is surfaced to the client (a
 * gateway-round-robined command such as {@code createProcessInstance} would collapse it into a
 * generic "all partitions failed" error).
 */
@ZeebeIntegration
final class PhysicalTenantBackpressureIsolationIT {

  private static final String TENANT_A = "tenanta";

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.none())
          .withTenant(TENANT_A, Storage.none())
          .build();

  @TestZeebe
  private final TestStandaloneBroker broker =
      TENANTS.configure(new TestStandaloneBroker().withUnauthenticatedAccess());

  @AutoClose private CamundaClient defaultClient;
  @AutoClose private CamundaClient tenantAClient;

  private ZeebePartition tenantAPartition;

  @BeforeEach
  void beforeEach() {
    defaultClient =
        TENANTS.newClientBuilder(broker, PhysicalTenantsITHelper.DEFAULT_TENANT_ID).build();
    tenantAClient = TENANTS.newClientBuilder(broker, TENANT_A).build();

    tenantAPartition = awaitTenantAPartitionReady();
    awaitWritesAccepted(defaultClient);
    awaitWritesAccepted(tenantAClient);
  }

  @Test
  void shouldNotDegradeDefaultTenantWhenOtherTenantIsBackpressured() {
    // given - tenant A's write throughput is throttled to reject (almost) every user command. A
    // FIXED request-concurrency limit cannot express this: LimitCfg rejects a limit of 0 outright,
    // and a limit of 1 never rejects sequential (non-overlapping) requests since each permit is
    // released before the next is acquired. A write-rate limit of 1 permit/second with no ramp-up
    // does reject deterministically: its underlying Guava RateLimiter starts "full", so the first
    // write succeeds but every write within the following second is rejected once the single
    // permit is exhausted - the same mechanism FlowControlTest#setup relies on.
    tenantAPartition.getAdminAccess().configureFlowControl(restrictiveWriteRateLimit()).join();

    // when - tenant A's writes are repeatedly attempted; the first may succeed, but subsequent ones
    // within the same second are rejected once the single permit is exhausted
    final var tenantARejection = captureFirstRejection(tenantAClient);

    // then - tenant A's write is backpressured with RESOURCE_EXHAUSTED (the write-rate limiter's
    // rejection message, as opposed to the request-limiter's "Reached maximum capacity...")
    assertThat(tenantARejection)
        .isInstanceOf(ClientStatusException.class)
        .hasStackTraceContaining("write limit is exhausted")
        .satisfies(
            t ->
                assertThat(((ClientStatusException) t).getStatusCode())
                    .isEqualTo(Code.RESOURCE_EXHAUSTED));

    // ...while the exact same command against the default tenant succeeds: FlowControl is a
    // per-(physical tenant, partition) instance, so throttling tenant A's engine does not affect
    // the default engine's independent instance - this is the core isolation property under test
    assertThatCode(() -> publishMessage(defaultClient)).doesNotThrowAnyException();

    // and - white-box: the two tenants have independent partition managers and FlowControl state
    final var partitionManagers =
        broker.bean(Broker.class).getBrokerContext().getPartitionManagers();
    assertThat(partitionManagers.get(TENANT_A))
        .isNotSameAs(partitionManagers.get(PhysicalTenantsITHelper.DEFAULT_TENANT_ID));

    final var defaultLimits =
        partitionManagers
            .get(PhysicalTenantsITHelper.DEFAULT_TENANT_ID)
            .getZeebePartitions()
            .iterator()
            .next()
            .getAdminAccess()
            .getFlowControlConfiguration()
            .join();
    assertThat(defaultLimits.writeRateLimit())
        .satisfiesAnyOf(
            rateLimit -> assertThat(rateLimit).isNull(),
            rateLimit -> assertThat(rateLimit.enabled()).isFalse());
  }

  @Test
  void shouldRecoverWhenTenantIsNoLongerBackpressured() {
    // given - tenant A is backpressured and rejecting writes
    tenantAPartition.getAdminAccess().configureFlowControl(restrictiveWriteRateLimit()).join();
    assertThat(captureFirstRejection(tenantAClient)).isInstanceOf(ClientStatusException.class);

    // when - tenant A's write-rate limit is explicitly lifted (a disabled limit must be set: an
    // empty FlowControlCfg leaves getWrite() null, and configureFlowControl only touches a limit
    // when its config is non-null, so it would not clear the existing limit)
    final var lift = new FlowControlCfg();
    final var disabledWriteLimit = new RateLimitCfg();
    disabledWriteLimit.setEnabled(false);
    lift.setWrite(disabledWriteLimit);
    tenantAPartition.getAdminAccess().configureFlowControl(lift).join();

    // then - tenant A accepts writes again
    awaitWritesAccepted(tenantAClient);
  }

  private static FlowControlCfg restrictiveWriteRateLimit() {
    final var flowControl = new FlowControlCfg();
    final var writeLimit = new RateLimitCfg();
    writeLimit.setEnabled(true);
    writeLimit.setLimit(1);
    flowControl.setWrite(writeLimit);
    return flowControl;
  }

  // repeatedly attempts writes (polling faster than the ~1s write-rate permit accrues) until one is
  // rejected, and returns that rejection
  private static Throwable captureFirstRejection(final CamundaClient client) {
    final var rejection = new AtomicReference<Throwable>();
    await("write is rejected once the write-rate limit is exhausted")
        .atMost(Duration.ofSeconds(10))
        .pollInterval(Duration.ofMillis(10))
        .untilAsserted(
            () -> {
              try {
                publishMessage(client);
              } catch (final Exception e) {
                rejection.set(e);
              }
              assertThat(rejection.get()).isNotNull();
            });
    return rejection.get();
  }

  private static void awaitWritesAccepted(final CamundaClient client) {
    await("tenant accepts writes")
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .untilAsserted(
            () -> assertThatCode(() -> publishMessage(client)).doesNotThrowAnyException());
  }

  // publishMessage (MessageIntent.PUBLISH) is not whitelisted in FlowControl - see
  // WhiteListedCommands - so it is subject to backpressure; it is also routed to a single partition
  // by its correlation key, so the broker's rejection reason is preserved on the client exception
  private static void publishMessage(final CamundaClient client) {
    client
        .newPublishMessageCommand()
        .messageName("backpressure-msg")
        .correlationKey(UUID.randomUUID().toString())
        .send()
        .join();
  }

  // tenant A's ZeebePartition exists as soon as the partition manager starts, but its admin access
  // is only usable once the log stream is installed on the (elected) leader; poll a lightweight
  // admin call until it stops failing rather than relying on the default topology RPC, which
  // cannot represent non-default physical tenants' partitions
  private ZeebePartition awaitTenantAPartitionReady() {
    final var reference = new AtomicReference<ZeebePartition>();
    await("tenant A's partition is leader-ready for admin access")
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var partitions =
                  broker
                      .bean(Broker.class)
                      .getBrokerContext()
                      .getPartitionManagers()
                      .get(TENANT_A)
                      .getZeebePartitions();
              assertThat(partitions).isNotEmpty();
              final var partition = partitions.iterator().next();
              partition.getAdminAccess().getFlowControlConfiguration().join();
              reference.set(partition);
            });
    return reference.get();
  }
}
