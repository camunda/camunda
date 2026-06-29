/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.zeebe.connector;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.impl.record.CopiedRecord;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ZeebeRecordProducerTest {

  @Test
  void shouldGroupRecordsByPartitionPreservingOrder() {
    // given — records interleaved across two partitions
    final Record<?> p1a = jobRecord(1, "a");
    final Record<?> p2a = jobRecord(2, "b");
    final Record<?> p1b = jobRecord(1, "c");
    final Record<?> p2b = jobRecord(2, "d");

    // when
    final Map<Integer, List<Record<?>>> grouped =
        ZeebeRecordProducer.groupByPartition(List.of(p1a, p2a, p1b, p2b), Record::getPartitionId);

    // then — one batch per partition, original order kept within each
    assertThat(grouped).containsOnlyKeys(1, 2);
    assertThat(grouped.get(1)).containsExactly(p1a, p1b);
    assertThat(grouped.get(2)).containsExactly(p2a, p2b);
  }

  private static Record<?> jobRecord(final int partitionId, final String type) {
    final JobRecord value = new JobRecord().setType(type);
    final RecordMetadata metadata =
        new RecordMetadata()
            .recordType(RecordType.EVENT)
            .valueType(ValueType.JOB)
            .intent(JobIntent.CREATED);
    return new CopiedRecord<>(value, metadata, 1L, partitionId, 1L, -1L, 1L);
  }
}
