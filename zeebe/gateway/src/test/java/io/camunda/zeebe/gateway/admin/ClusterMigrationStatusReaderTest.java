/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.admin;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.zeebe.protocol.impl.encoding.MigrationStatusCode;
import io.camunda.zeebe.protocol.impl.encoding.PartitionMigrationStatus;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

final class ClusterMigrationStatusReaderTest {

  @Test
  void shouldReportOneConciseSummaryWhenEveryEntryIsMigrated() {
    // given - a large partition count would otherwise echo one near-identical detail per entry
    final var statuses =
        List.of(
            migrated("partition 1: migrated to 8.10"),
            migrated("partition 2: migrated to 8.10"),
            migrated("partition 3: migrated to 8.10"));

    // when
    final var aggregated = ClusterMigrationStatusReader.aggregate(statuses);

    // then
    assertThat(aggregated.code()).isEqualTo(MigrationStatusCode.MIGRATED);
    assertThat(aggregated.detail()).isEqualTo("All partitions migrated");
  }

  @Test
  void shouldOnlyListEntriesThatAreNotYetMigrated() {
    // given
    final var statuses =
        List.of(
            migrated("partition 1: migrated to 8.10"),
            inProgress("partition 2: not yet caught up to 8.10"),
            migrated("partition 3: migrated to 8.10"));

    // when
    final var aggregated = ClusterMigrationStatusReader.aggregate(statuses);

    // then
    assertThat(aggregated.code()).isEqualTo(MigrationStatusCode.MIGRATION_IN_PROGRESS);
    assertThat(aggregated.detail())
        .contains("partition 2")
        .doesNotContain("partition 1", "partition 3");
  }

  @Test
  void shouldOnlyListEntriesThatAreNotYetMigratedWhenSomeAreUnknown() {
    // given - UNKNOWN still takes precedence in the overall code, but the migrated entries are
    // still dropped from the detail
    final var statuses =
        List.of(
            migrated("partition 1: migrated to 8.10"),
            inProgress("partition 2: not yet caught up to 8.10"),
            unknown("partition 3: unreachable"));

    // when
    final var aggregated = ClusterMigrationStatusReader.aggregate(statuses);

    // then
    assertThat(aggregated.code()).isEqualTo(MigrationStatusCode.UNKNOWN);
    assertThat(aggregated.detail())
        .contains("partition 2", "partition 3")
        .doesNotContain("partition 1");
  }

  @Test
  void shouldReportUnknownWhenNoPartitionsAreFoundAtAll() {
    // when
    final var aggregated = ClusterMigrationStatusReader.aggregate(List.of());

    // then
    assertThat(aggregated.code()).isEqualTo(MigrationStatusCode.UNKNOWN);
  }

  @Test
  void shouldRestoreInterruptStatusWhenInterruptedWhileWaiting() throws InterruptedException {
    // given - a fetch that never completes, so resolveTenants blocks on the shared timeout's
    // get() until either the timeout elapses or the waiting thread is interrupted
    final var enteredFanOut = new CountDownLatch(1);
    final var neverCompletes = new CompletableFuture<PartitionMigrationStatus>();
    final var interruptedInsideResolveTenants = new AtomicBoolean();
    final var worker =
        new Thread(
            () -> {
              ClusterMigrationStatusReader.resolveTenants(
                  singleTenant("tenantA"),
                  Duration.ofSeconds(30),
                  tenantId -> {
                    enteredFanOut.countDown();
                    return neverCompletes;
                  },
                  LoggerFactory.getLogger(ClusterMigrationStatusReaderTest.class),
                  "test condition");
              interruptedInsideResolveTenants.set(Thread.currentThread().isInterrupted());
            });

    // when - interrupt the worker once it has dispatched its fetch and is about to wait
    worker.start();
    assertThat(enteredFanOut.await(5, TimeUnit.SECONDS)).isTrue();
    worker.interrupt();
    worker.join(Duration.ofSeconds(5).toMillis());

    // then - the interrupt status is restored rather than silently swallowed
    assertThat(interruptedInsideResolveTenants.get()).isTrue();
  }

  private static PhysicalTenantIds singleTenant(final String physicalTenantId) {
    return () -> Set.of(physicalTenantId);
  }

  private static PartitionMigrationStatus migrated(final String detail) {
    return new PartitionMigrationStatus(MigrationStatusCode.MIGRATED, detail);
  }

  private static PartitionMigrationStatus inProgress(final String detail) {
    return new PartitionMigrationStatus(MigrationStatusCode.MIGRATION_IN_PROGRESS, detail);
  }

  private static PartitionMigrationStatus unknown(final String detail) {
    return new PartitionMigrationStatus(MigrationStatusCode.UNKNOWN, detail);
  }
}
