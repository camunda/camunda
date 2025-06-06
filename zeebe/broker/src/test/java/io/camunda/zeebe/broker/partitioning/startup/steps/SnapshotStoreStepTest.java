/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.startup.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.broker.partitioning.startup.PartitionStartupContext;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.SchedulingHints;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStore;
import io.camunda.zeebe.snapshots.transfer.SnapshotTransfer;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SnapshotStoreStepTest {

  @TempDir Path partitionDirectory;
  private SnapshotStoreStep step;
  private PartitionStartupContext context;

  @AutoClose
  private CompositeMeterRegistry registry =
      new CompositeMeterRegistry().add(new SimpleMeterRegistry());

  private FileBasedSnapshotStore snapshotStore;

  @BeforeEach
  public void setUp() throws Exception {
    step = spy(new SnapshotStoreStep());
    context = mock(PartitionStartupContext.class);
    final var concurrency = new TestConcurrencyControl();
    final BrokerCfg brokerconfig = new BrokerCfg();
    brokerconfig.getCluster().setNodeId(3);
    final var schedulingService = mock(ActorSchedulingService.class);
    when(context.schedulingService()).thenReturn(schedulingService);
    when(context.brokerConfig()).thenReturn(brokerconfig);
    when(context.partitionId()).thenReturn(4);
    when(context.partitionMeterRegistry()).thenReturn(registry);
    when(context.partitionDirectory()).thenReturn(partitionDirectory);
    when(schedulingService.submitActor(any(), any()))
        .thenReturn(CompletableActorFuture.completed());
    when(context.concurrencyControl()).thenReturn(concurrency);
    when(context.snapshotStore(any())).thenReturn(context);
    snapshotStore = mock(FileBasedSnapshotStore.class);
    when(context.snapshotStore()).thenReturn(snapshotStore);
  }

  @Test
  public void shouldStartupNormally() {
    // when
    final var startupFuture = step.startup(context);

    // then
    assertThat(startupFuture).succeedsWithin(Duration.ofSeconds(10));
    verify(context).snapshotStore(any());
    verify(context.schedulingService()).submitActor(any(), eq(SchedulingHints.IO_BOUND));
    verify(context, never()).brokerClient();
  }

  @Test
  public void shouldGetSnapshotWhenConfigured() throws InterruptedException {
    // given
    when(context.isInitializeFromSnapshot()).thenReturn(true);

    final var snapshotTransfer = mock(SnapshotTransfer.class);
    when(context.snapshotTransfer()).thenReturn(snapshotTransfer);
    when(snapshotTransfer.getLatestSnapshot(eq(context.partitionId())))
        .thenReturn(CompletableActorFuture.completed(mock(PersistedSnapshot.class)));

    when(snapshotStore.restore(any())).thenReturn(CompletableActorFuture.completed());

    // when
    final var startup = step.startup(context);

    // then
    assertThat(startup).succeedsWithin(Duration.ofSeconds(10));
    verify(context.snapshotStore()).restore(any());
    verify(context.snapshotTransfer()).getLatestSnapshot(eq(context.partitionId()));
  }

  @Test
  public void shouldShutdownTheStore() {
    // given
    when(snapshotStore.closeAsync()).thenReturn(CompletableActorFuture.completed());
    step.startup(context).join();
    clearInvocations(context);

    // when
    final var shutdown = step.shutdown(context);

    // then
    assertThat(shutdown).succeedsWithin(Duration.ofSeconds(10));
    verify(context).snapshotStore(isNull());
    verify(context.snapshotStore()).closeAsync();
  }
}
