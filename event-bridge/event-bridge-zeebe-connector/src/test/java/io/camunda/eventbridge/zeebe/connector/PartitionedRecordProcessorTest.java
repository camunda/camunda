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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
