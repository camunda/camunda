/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.startup.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.cluster.PartitionId;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.zeebe.broker.partitioning.startup.PartitionStartupContext;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.db.PrimaryStorageType;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentMatchers;

/**
 * The snapshot store step is where a broker first meets the snapshots on disk. A snapshot of the
 * other storage backend has to be reported there with a message that names both backends. Reading
 * it with the configured backend's file info provider would fail with an error of the backend's
 * own, which the snapshot store only logs before it skips the snapshot.
 */
final class SnapshotStoreStepTest {

  private static final int PARTITION_ID = 1;
  private static final TestConcurrencyControl CONCURRENCY_CONTROL = new TestConcurrencyControl();

  @TempDir Path partitionDirectory;

  private final SnapshotStoreStep step = new SnapshotStoreStep(PARTITION_ID);
  private final ActorSchedulingService schedulingService = mock(ActorSchedulingService.class);

  @Test
  void shouldFailWithClearMessageForSqliteSnapshotOnRocksDbBroker() throws Exception {
    // given
    writeSnapshotFile("zeebe.sqlite");

    // when
    final var result = step.startup(context(PrimaryStorageType.ROCKSDB));

    // then
    assertThat(result.isCompletedExceptionally()).isTrue();
    assertThat(result.getException())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(partitionDirectory.toString())
        .hasMessageContaining("hold rocksdb state")
        .hasMessageContaining("holds sqlite state");
    verifyNoInteractions(schedulingService);
  }

  @Test
  void shouldFailWithClearMessageForRocksDbSnapshotOnSqliteBroker() throws Exception {
    // given
    writeSnapshotFile("CURRENT");

    // when
    final var result = step.startup(context(PrimaryStorageType.SQLITE));

    // then
    assertThat(result.isCompletedExceptionally()).isTrue();
    assertThat(result.getException())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("hold sqlite state")
        .hasMessageContaining("holds rocksdb state");
    verifyNoInteractions(schedulingService);
  }

  @Test
  void shouldStartTheSnapshotStoreForSnapshotsOfTheConfiguredBackend() throws Exception {
    // given
    writeSnapshotFile("zeebe.sqlite");
    when(schedulingService.submitActor(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(CONCURRENCY_CONTROL.completedFuture(null));

    // when
    step.startup(context(PrimaryStorageType.SQLITE));

    // then
    verify(schedulingService).submitActor(ArgumentMatchers.any(), ArgumentMatchers.any());
  }

  private void writeSnapshotFile(final String fileName) throws Exception {
    final var snapshot =
        Files.createDirectories(partitionDirectory.resolve("snapshots").resolve("1-1-1-1-0-abc"));
    Files.createFile(snapshot.resolve(fileName));
  }

  private PartitionStartupContext context(final PrimaryStorageType type) {
    final var brokerCfg = new BrokerCfg();
    brokerCfg.getData().setPrimaryStorageType(type);

    final var partitionMetadata = mock(PartitionMetadata.class);
    when(partitionMetadata.id())
        .thenReturn(new PartitionId(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, PARTITION_ID));

    final var context = mock(PartitionStartupContext.class);
    when(context.brokerConfig()).thenReturn(brokerCfg);
    when(context.concurrencyControl()).thenReturn(CONCURRENCY_CONTROL);
    when(context.schedulingService()).thenReturn(schedulingService);
    when(context.partitionDirectory()).thenReturn(partitionDirectory);
    when(context.partitionMetadata()).thenReturn(partitionMetadata);
    when(context.partitionMeterRegistry()).thenReturn(new CompositeMeterRegistry());
    return context;
  }
}
