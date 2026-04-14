/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.replication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.write.ReplicationLsnProvider;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

final class ReplicationMonitorTest {

  private static final Duration POLLING_INTERVAL = Duration.ofSeconds(15);

  private ReplicationLsnProvider lsnProvider;
  private ArrayBlockingQueue<LsnPositionEntry> queue;
  private AtomicLong confirmedPosition;
  private ReplicationMonitor monitor;

  @BeforeEach
  void setUp() {
    lsnProvider = mock(ReplicationLsnProvider.class);
    queue = new ArrayBlockingQueue<>(1024);
    confirmedPosition = new AtomicLong(-1);
    monitor = new ReplicationMonitor(lsnProvider, queue, confirmedPosition, POLLING_INTERVAL);
  }

  @Nested
  final class CheckReplicationTest {

    @Test
    void shouldDrainEntriesUpToReplicaLsn() {
      // given
      queue.offer(new LsnPositionEntry(100, 1));
      queue.offer(new LsnPositionEntry(200, 2));
      queue.offer(new LsnPositionEntry(300, 3));
      when(lsnProvider.getReplicaLsn()).thenReturn(200L);

      // when
      final Duration nextDelay = monitor.checkReplication();

      // then
      assertThat(confirmedPosition.get()).isEqualTo(2);
      assertThat(queue).hasSize(1);
      assertThat(queue.peek().lsn()).isEqualTo(300);
      assertThat(nextDelay).isEqualTo(POLLING_INTERVAL);
    }

    @Test
    void shouldDrainAllEntriesWhenReplicaCaughtUp() {
      // given
      queue.offer(new LsnPositionEntry(100, 1));
      queue.offer(new LsnPositionEntry(200, 2));
      when(lsnProvider.getReplicaLsn()).thenReturn(500L);

      // when
      monitor.checkReplication();

      // then
      assertThat(confirmedPosition.get()).isEqualTo(2);
      assertThat(queue).isEmpty();
    }

    @Test
    void shouldNotDrainWhenReplicaBehindAllEntries() {
      // given
      queue.offer(new LsnPositionEntry(100, 1));
      queue.offer(new LsnPositionEntry(200, 2));
      when(lsnProvider.getReplicaLsn()).thenReturn(50L);

      // when
      monitor.checkReplication();

      // then
      assertThat(confirmedPosition.get()).isEqualTo(-1);
      assertThat(queue).hasSize(2);
    }

    @Test
    void shouldHandleEmptyQueue() {
      // given
      when(lsnProvider.getReplicaLsn()).thenReturn(500L);

      // when
      final Duration nextDelay = monitor.checkReplication();

      // then
      assertThat(confirmedPosition.get()).isEqualTo(-1);
      assertThat(nextDelay).isEqualTo(POLLING_INTERVAL);
    }

    @Test
    void shouldDrainExactMatchLsn() {
      // given
      queue.offer(new LsnPositionEntry(100, 1));
      when(lsnProvider.getReplicaLsn()).thenReturn(100L);

      // when
      monitor.checkReplication();

      // then
      assertThat(confirmedPosition.get()).isEqualTo(1);
      assertThat(queue).isEmpty();
    }
  }
}
