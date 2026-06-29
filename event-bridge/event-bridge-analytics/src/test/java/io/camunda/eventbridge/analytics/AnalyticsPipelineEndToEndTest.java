/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.eventbridge.analytics.aggregate.ExecutionTimeAggregate;
import io.camunda.eventbridge.analytics.aggregate.ExecutionTimeAggregator;
import io.camunda.eventbridge.analytics.fact.ProcessInstanceExecutionTimeFactCodec;
import io.camunda.eventbridge.analytics.projection.InMemoryBaseProjectionStore;
import io.camunda.eventbridge.analytics.projection.ProcessInstanceProjector;
import io.camunda.eventbridge.zeebe.connector.ZeebeRecord;
import io.camunda.zeebe.protocol.impl.record.CopiedRecord;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

/**
 * End-to-end (offline, no cluster) flow through all three stages: records → base projection →
 * serialized facts on a simulated fact stream → aggregated H2 dataset.
 */
final class AnalyticsPipelineEndToEndTest {

  private static final long DEF_KEY = 77L;
  private static final int VERSION = 3;
  private static final String TENANT = "<default>";

  @Test
  void shouldFlowRecordsThroughProjectionFactStreamAndAggregate() {
    // given — the three stages, with a list standing in for the fact-topic between them
    final ProcessInstanceProjector projector =
        new ProcessInstanceProjector(new InMemoryBaseProjectionStore());
    final ProcessInstanceExecutionTimeFactCodec codec = new ProcessInstanceExecutionTimeFactCodec();
    final List<byte[]> factStream = new ArrayList<>();

    final JdbcDataSource dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:pipeline-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    final ExecutionTimeAggregator aggregator = new ExecutionTimeAggregator(dataSource);
    aggregator.initSchema();

    // three instances of the same definition: durations 500, 300, 700
    final long[][] instances = {{101L, 1000L, 1500L}, {102L, 2000L, 2300L}, {103L, 3000L, 3700L}};

    // when — Stage 1 folds the records and publishes each derived fact onto the fact stream
    long position = 10L;
    for (final long[] instance : instances) {
      final long piKey = instance[0];
      projector.apply(
          event(ProcessInstanceIntent.ELEMENT_ACTIVATED, piKey, instance[1], position++));
      projector
          .apply(event(ProcessInstanceIntent.ELEMENT_COMPLETED, piKey, instance[2], position++))
          .ifPresent(fact -> factStream.add(codec.serialize(fact)));
    }

    // and — Stage 3 consumes the fact stream
    for (final byte[] payload : factStream) {
      aggregator.apply(codec.deserialize(payload));
    }

    // then
    assertThat(factStream).hasSize(3);
    final ExecutionTimeAggregate agg = aggregator.read(DEF_KEY, VERSION, TENANT).orElseThrow();
    assertThat(agg.instanceCount()).isEqualTo(3L);
    assertThat(agg.totalDurationMs()).isEqualTo(1500L);
    assertThat(agg.minDurationMs()).isEqualTo(300L);
    assertThat(agg.maxDurationMs()).isEqualTo(700L);
    assertThat(agg.averageDurationMs()).isEqualTo(500.0);
  }

  private static ZeebeRecord event(
      final ProcessInstanceIntent intent,
      final long processInstanceKey,
      final long timestamp,
      final long position) {
    final ProcessInstanceRecord value =
        new ProcessInstanceRecord()
            .setProcessInstanceKey(processInstanceKey)
            .setProcessDefinitionKey(DEF_KEY)
            .setBpmnProcessId("order")
            .setVersion(VERSION)
            .setTenantId(TENANT)
            .setBpmnElementType(BpmnElementType.PROCESS);
    final RecordMetadata metadata =
        new RecordMetadata()
            .recordType(RecordType.EVENT)
            .valueType(ValueType.PROCESS_INSTANCE)
            .intent(intent);
    final Record<ProcessInstanceRecord> record =
        new CopiedRecord<>(
            value, metadata, processInstanceKey, 1, position, position - 1, timestamp);
    return new ZeebeRecord("zeebe-records", 1, position, record);
  }
}
