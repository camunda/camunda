/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.metrics.MessageCorrelationMetrics;
import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.state.immutable.MessageState;
import io.camunda.zeebe.engine.state.immutable.MessageState.CrossPartitionStartLockVisitor;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartCorrelationKeyLockReleaseRecord;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Verifies the {@code P_K}-side release-reconciliation scheduler in isolation: it walks the
 * cross-partition lock entries, groups them by the partition each holder lives on, chunks each
 * partition's holders into one query per batch-limit slice, and skips entries whose holder is
 * local. The scheduler holds no transient bookkeeping — every tick reconciles purely from the
 * current lock state.
 */
public final class CrossPartitionMessageStartLockReleaseSchedulerTest {

  private static final Duration POLL_INTERVAL = Duration.ofSeconds(30);
  private static final int LOCAL_PARTITION = 1;

  private SubscriptionCommandSender mockCommandSender;
  private MessageState mockMessageState;
  private ProcessingScheduleService mockScheduleService;
  private ReadonlyStreamProcessorContext mockContext;
  private int batchLimit;
  private final List<Lock> locks = new ArrayList<>();
  private SimpleMeterRegistry meterRegistry;

  private CrossPartitionMessageStartLockReleaseScheduler scheduler;

  @BeforeEach
  void setUp() {
    mockCommandSender = mock(SubscriptionCommandSender.class);
    mockMessageState = mock(MessageState.class);
    mockScheduleService = mock(ProcessingScheduleService.class);
    mockContext = mock(ReadonlyStreamProcessorContext.class);
    batchLimit = 64;

    when(mockContext.getScheduleService()).thenReturn(mockScheduleService);

    // replay the configured locks to the visitor on every visit call
    doAnswer(
            invocation -> {
              final CrossPartitionStartLockVisitor visitor = invocation.getArgument(0);
              for (final var lock : locks) {
                visitor.visit(
                    new UnsafeBuffer(lock.bpmnProcessId.getBytes()),
                    new UnsafeBuffer(lock.correlationKey.getBytes()),
                    lock.holderProcessInstanceKey,
                    lock.tenantId);
              }
              return null;
            })
        .when(mockMessageState)
        .visitCrossPartitionStartLocks(any());

    meterRegistry = new SimpleMeterRegistry();
    scheduler =
        new CrossPartitionMessageStartLockReleaseScheduler(
            LOCAL_PARTITION,
            mockCommandSender,
            mockMessageState,
            () -> POLL_INTERVAL,
            () -> batchLimit,
            new MessageCorrelationMetrics(meterRegistry));
    scheduler.onRecovered(mockContext);
  }

  @Test
  void shouldScheduleAtFixedRateOnRecovered() {
    // then (onRecovered already invoked in setUp)
    verify(mockScheduleService).runAtFixedRate(eq(POLL_INTERVAL), eq(scheduler));
  }

  @Test
  void shouldNotQueryWhenNoLocks() {
    // when
    scheduler.run();

    // then
    verify(mockCommandSender, never()).sendDirectCorrelationKeyLockReleaseQuery(anyInt(), any());
  }

  @Test
  void shouldBatchHoldersByTargetPartitionIntoOneQueryPerPartition() {
    // given two holders on partition 2 and one on partition 3
    addLock("wf", "ck-a", holderKeyOnPartition(2, 1));
    addLock("wf", "ck-b", holderKeyOnPartition(2, 2));
    addLock("wf", "ck-c", holderKeyOnPartition(3, 1));

    // when
    scheduler.run();

    // then exactly one query is dispatched to partition 2 (with both holders) and one to partition
    // 3
    final var partitionCaptor = ArgumentCaptor.forClass(Integer.class);
    final var queryCaptor =
        ArgumentCaptor.forClass(MessageStartCorrelationKeyLockReleaseRecord.class);
    verify(mockCommandSender, org.mockito.Mockito.times(2))
        .sendDirectCorrelationKeyLockReleaseQuery(partitionCaptor.capture(), queryCaptor.capture());

    final var queriesByPartition = partitionCaptor.getAllValues();
    final var queries = queryCaptor.getAllValues();
    assertThat(queriesByPartition).containsExactlyInAnyOrder(2, 3);
    final int p2Index = queriesByPartition.indexOf(2);
    final int p3Index = queriesByPartition.indexOf(3);
    assertThat(queries.get(p2Index).getHolders()).hasSize(2);
    assertThat(queries.get(p3Index).getHolders()).hasSize(1);
  }

  @Test
  void shouldEncodeLocalPartitionInRequestKey() {
    // given
    addLock("wf", "ck", holderKeyOnPartition(2, 1));

    // when
    scheduler.run();

    // then the request key routes the reply back to the local partition
    final var queryCaptor =
        ArgumentCaptor.forClass(MessageStartCorrelationKeyLockReleaseRecord.class);
    verify(mockCommandSender)
        .sendDirectCorrelationKeyLockReleaseQuery(eq(2), queryCaptor.capture());
    assertThat(Protocol.decodePartitionId(queryCaptor.getValue().getRequestKey()))
        .isEqualTo(LOCAL_PARTITION);
  }

  @Test
  void shouldChunkHoldersExceedingBatchLimitIntoMultipleQueriesInSameTick() {
    // given two holders on the same partition but a batch limit of one
    batchLimit = 1;
    addLock("wf", "ck-a", holderKeyOnPartition(2, 1));
    addLock("wf", "ck-b", holderKeyOnPartition(2, 2));

    // when a single tick runs
    scheduler.run();

    // then both holders are reconciled in that same tick, split into one single-holder query each,
    // so a holder sorted past the batch limit never has to wait for a leading lock to be reaped
    final var queryCaptor =
        ArgumentCaptor.forClass(MessageStartCorrelationKeyLockReleaseRecord.class);
    verify(mockCommandSender, org.mockito.Mockito.times(2))
        .sendDirectCorrelationKeyLockReleaseQuery(eq(2), queryCaptor.capture());

    final var polledCorrelationKeys =
        queryCaptor.getAllValues().stream()
            .peek(q -> assertThat(q.getHolders()).hasSize(1))
            .map(q -> q.getHolders().getFirst().getCorrelationKey())
            .toList();
    assertThat(polledCorrelationKeys).containsExactly("ck-a", "ck-b");
  }

  @Test
  void shouldNotSendQueryWhenBatchLimitIsNonPositive() {
    // given a due lock but a misconfigured non-positive batch limit
    batchLimit = 0;
    addLock("wf", "ck", holderKeyOnPartition(2, 1));

    // when
    scheduler.run();

    // then no empty query is dispatched
    verify(mockCommandSender, never()).sendDirectCorrelationKeyLockReleaseQuery(anyInt(), any());
  }

  @Test
  void shouldReconcilePurelyFromCurrentLockStateEachTick() {
    // given a lock that has been reconciled once
    addLock("wf", "ck", holderKeyOnPartition(2, 1));
    scheduler.run();
    verify(mockCommandSender, org.mockito.Mockito.times(1))
        .sendDirectCorrelationKeyLockReleaseQuery(eq(2), any());

    // when the lock disappears (its holder completed and the lock was released elsewhere)
    locks.clear();
    scheduler.run();
    // then nothing is reconciled — the scheduler keeps no memory of the vanished lock
    verify(mockCommandSender, org.mockito.Mockito.times(1))
        .sendDirectCorrelationKeyLockReleaseQuery(eq(2), any());

    // when a lock for the same correlation key reappears (a new holder took it over)
    addLock("wf", "ck", holderKeyOnPartition(2, 2));
    scheduler.run();

    // then it is reconciled again purely from the current state, without inheriting anything from
    // the earlier entry
    verify(mockCommandSender, org.mockito.Mockito.times(2))
        .sendDirectCorrelationKeyLockReleaseQuery(eq(2), any());
  }

  @Test
  void shouldSkipHoldersOnTheLocalPartition() {
    // given a (defensive) lock whose holder key encodes the local partition
    addLock("wf", "ck", holderKeyOnPartition(LOCAL_PARTITION, 1));

    // when
    scheduler.run();

    // then no query is dispatched to ourselves
    verify(mockCommandSender, never()).sendDirectCorrelationKeyLockReleaseQuery(anyInt(), any());
  }

  @Test
  void shouldRecordQueryAndBatchSizeMetricsPerDispatchedQuery() {
    // given two holders on partition 2 and one on partition 3
    addLock("wf", "ck-a", holderKeyOnPartition(2, 1));
    addLock("wf", "ck-b", holderKeyOnPartition(2, 2));
    addLock("wf", "ck-c", holderKeyOnPartition(3, 1));

    // when
    scheduler.run();

    // then one query is counted per dispatched query (M9), and the batch-size distribution (M10)
    // records the holder count of each query
    assertThat(
            meterRegistry
                .get("zeebe.message.start.cross.partition.lock.release.queries.total")
                .counter()
                .count())
        .isEqualTo(2.0);
    final var batchSize =
        meterRegistry
            .get("zeebe.message.start.cross.partition.lock.release.query.batch.size")
            .summary();
    assertThat(batchSize.count()).isEqualTo(2L);
    assertThat(batchSize.totalAmount()).isEqualTo(3.0);
  }

  private void addLock(
      final String bpmnProcessId, final String correlationKey, final long holderKey) {
    locks.add(new Lock(bpmnProcessId, correlationKey, holderKey, "<default>"));
  }

  private static long holderKeyOnPartition(final int partition, final long sequence) {
    return Protocol.encodePartitionId(partition, sequence);
  }

  private record Lock(
      String bpmnProcessId,
      String correlationKey,
      long holderProcessInstanceKey,
      String tenantId) {}
}
