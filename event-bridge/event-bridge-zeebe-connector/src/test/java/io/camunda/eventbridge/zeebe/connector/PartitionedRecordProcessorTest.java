/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.zeebe.connector;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.eventbridge.client.TopicPartition;
import io.camunda.zeebe.protocol.impl.record.CopiedRecord;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

final class PartitionedRecordProcessorTest {

  private static final String TOPIC = "zeebe-records";

  @Test
  void shouldPreservePerPartitionOrderAcrossWorkers() throws Exception {
    // given — a handler that records the sequence it sees per partition
    final int perPartition = 50;
    final int partitions = 3;
    final Map<Integer, List<Integer>> seen = new ConcurrentHashMap<>();
    final CountDownLatch processed = new CountDownLatch(perPartition * partitions);

    final RecordDispatcher dispatcher = new RecordDispatcher();
    dispatcher.onAny(
        record -> {
          final int partition = record.getPartitionId();
          final int seq = Integer.parseInt(((JobRecordValue) record.getValue()).getType());
          seen.computeIfAbsent(partition, p -> new CopyOnWriteArrayList<>()).add(seq);
          processed.countDown();
        });

    // when — submit records interleaved across partitions, on a multi-thread pool
    try (var processor = new PartitionedRecordProcessor(dispatcher, 4, 1000)) {
      for (int seq = 0; seq < perPartition; seq++) {
        for (int partition = 1; partition <= partitions; partition++) {
          processor.submit(record(partition, seq));
        }
      }

      assertThat(processed.await(10, TimeUnit.SECONDS)).isTrue();

      // then — every partition saw all its records, strictly in submit order
      for (int partition = 1; partition <= partitions; partition++) {
        assertThat(seen.get(partition))
            .as("partition %d order", partition)
            .hasSize(perPartition)
            .isSorted();
      }

      // and the latest processed offset per partition is tracked for committing
      final Map<TopicPartition, ZeebeRecord> completed = processor.completedOffsets();
      for (int partition = 1; partition <= partitions; partition++) {
        assertThat(completed.get(new TopicPartition(TOPIC, partition)).offset())
            .isEqualTo(perPartition - 1L);
      }
    }
  }

  @Test
  void shouldPreservePerPartitionOrderWhenSubmittingBatchesOfInterleavedRuns() throws Exception {
    // given — a handler that records the sequence it sees per partition
    final int perPartition = 40;
    final int partitions = 3;
    final Map<Integer, List<Integer>> seen = new ConcurrentHashMap<>();
    final CountDownLatch processed = new CountDownLatch(perPartition * partitions);

    final RecordDispatcher dispatcher = new RecordDispatcher();
    dispatcher.onAny(
        record -> {
          final int partition = record.getPartitionId();
          final int seq = Integer.parseInt(((JobRecordValue) record.getValue()).getType());
          seen.computeIfAbsent(partition, p -> new CopyOnWriteArrayList<>()).add(seq);
          processed.countDown();
        });

    // when — submit one batch whose consecutive same-partition runs interleave across partitions
    try (var processor = new PartitionedRecordProcessor(dispatcher, 4, 1000)) {
      final List<ZeebeRecord> batch = new ArrayList<>();
      for (int seq = 0; seq < perPartition; seq += 2) {
        for (int partition = 1; partition <= partitions; partition++) {
          batch.add(record(partition, seq));
          batch.add(record(partition, seq + 1));
        }
      }
      processor.submit(batch);

      assertThat(processed.await(10, TimeUnit.SECONDS)).isTrue();

      // then — every partition saw all its records, strictly in submit order
      final List<Integer> expected = IntStream.range(0, perPartition).boxed().toList();
      for (int partition = 1; partition <= partitions; partition++) {
        assertThat(seen.get(partition))
            .as("partition %d order", partition)
            .containsExactlyElementsOf(expected);
      }

      // and the latest processed offset per partition is tracked for committing
      final Map<TopicPartition, ZeebeRecord> completed = processor.completedOffsets();
      for (int partition = 1; partition <= partitions; partition++) {
        assertThat(completed.get(new TopicPartition(TOPIC, partition)).offset())
            .isEqualTo(perPartition - 1L);
      }
    }
  }

  @Test
  void shouldStopLaterCommitsOnlyForTheFailedPartitionWhenARunFails() throws Exception {
    // given: one batch with a single run per partition — partition 1's handler throws mid-run at
    // seq 2, partition 2 succeeds; its trailing sentinel parks the lane once everything before it
    // completed, giving a fixed point to assert at
    final List<Integer> dispatchedOnPartition1 = new CopyOnWriteArrayList<>();
    final CountDownLatch partition1Failed = new CountDownLatch(1);
    final CountDownLatch partition2Settled = new CountDownLatch(1);
    final CountDownLatch release = new CountDownLatch(1);

    final RecordDispatcher dispatcher = new RecordDispatcher();
    dispatcher.onAny(
        record -> {
          final int seq = Integer.parseInt(((JobRecordValue) record.getValue()).getType());
          if (record.getPartitionId() == 1) {
            dispatchedOnPartition1.add(seq);
            if (seq == 2) {
              partition1Failed.countDown();
              throw new IllegalStateException("boom");
            }
          } else if (seq == 5) {
            partition2Settled.countDown();
            awaitUninterruptibly(release);
          }
        });

    // when: the batch is submitted as coalesced runs (5 records on partition 1, 6 on partition 2)
    try (var processor = new PartitionedRecordProcessor(dispatcher, 2, 100)) {
      final List<ZeebeRecord> batch = new ArrayList<>();
      for (int seq = 0; seq < 5; seq++) {
        batch.add(record(1, seq));
      }
      for (int seq = 0; seq < 6; seq++) {
        batch.add(record(2, seq));
      }
      processor.submit(batch);
      assertThat(partition1Failed.await(10, TimeUnit.SECONDS)).isTrue();
      assertThat(partition2Settled.await(10, TimeUnit.SECONDS)).isTrue();

      // then: the failure stopped the rest of partition 1's run — its committable offset froze at
      // the last success and the records after the failure were never dispatched
      final Map<TopicPartition, ZeebeRecord> completed = processor.completedOffsets();
      assertThat(completed.get(new TopicPartition(TOPIC, 1)).offset()).isEqualTo(1L);
      assertThat(dispatchedOnPartition1).containsExactly(0, 1, 2);

      // while partition 2's run kept processing and recording its offsets
      assertThat(completed.get(new TopicPartition(TOPIC, 2)).offset()).isEqualTo(4L);
      release.countDown();
    }
  }

  @Test
  void shouldNotAdvanceAPartitionsCompletedOffsetPastAFailedRecord() throws Exception {
    // given: partition 1's handler throws at seq 2; partition 2 always succeeds. A sentinel record
    // on partition 2 parks its lane once everything before it completed, giving a fixed point to
    // assert at (a partition's records run in order, so all earlier offsets are recorded by then).
    final List<Integer> dispatchedOnPartition1 = new CopyOnWriteArrayList<>();
    final CountDownLatch partition1Failed = new CountDownLatch(1);
    final CountDownLatch partition2Settled = new CountDownLatch(1);
    final CountDownLatch release = new CountDownLatch(1);

    final RecordDispatcher dispatcher = new RecordDispatcher();
    dispatcher.onAny(
        record -> {
          final int seq = Integer.parseInt(((JobRecordValue) record.getValue()).getType());
          if (record.getPartitionId() == 1) {
            dispatchedOnPartition1.add(seq);
            if (seq == 2) {
              partition1Failed.countDown();
              throw new IllegalStateException("boom");
            }
          } else if (seq == 5) {
            partition2Settled.countDown();
            awaitUninterruptibly(release);
          }
        });

    // when: five records per partition plus the sentinel, interleaved across partitions
    try (var processor = new PartitionedRecordProcessor(dispatcher, 2, 100)) {
      for (int seq = 0; seq < 5; seq++) {
        processor.submit(record(1, seq));
        processor.submit(record(2, seq));
      }
      processor.submit(record(2, 5));
      assertThat(partition1Failed.await(10, TimeUnit.SECONDS)).isTrue();
      assertThat(partition2Settled.await(10, TimeUnit.SECONDS)).isTrue();

      // then: partition 1's committable offset froze at the last success before the failure —
      // neither the failed record nor anything after it may ever be committed
      final Map<TopicPartition, ZeebeRecord> completed = processor.completedOffsets();
      assertThat(completed.get(new TopicPartition(TOPIC, 1)).offset()).isEqualTo(1L);

      // and: partition 1's records after the failure were never dispatched
      assertThat(dispatchedOnPartition1).containsExactly(0, 1, 2);

      // while partition 2 kept processing and recording its offsets
      assertThat(completed.get(new TopicPartition(TOPIC, 2)).offset()).isEqualTo(4L);
      release.countDown();
    }
  }

  private static void awaitUninterruptibly(final CountDownLatch latch) {
    try {
      latch.await();
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  private static ZeebeRecord record(final int partition, final int seq) {
    final JobRecord value = new JobRecord().setType(Integer.toString(seq));
    final RecordMetadata metadata =
        new RecordMetadata()
            .recordType(RecordType.EVENT)
            .valueType(ValueType.JOB)
            .intent(JobIntent.CREATED);
    final var record = new CopiedRecord<>(value, metadata, 1L, partition, seq, -1L, 1L);
    return new ZeebeRecord(TOPIC, partition, seq, record);
  }
}
