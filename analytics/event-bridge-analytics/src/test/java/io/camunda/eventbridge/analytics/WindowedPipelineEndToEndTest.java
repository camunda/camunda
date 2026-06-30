/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.eventbridge.analytics.aggregate.AggregateDataset;
import io.camunda.eventbridge.analytics.aggregate.WindowedExecutionTimeAggregator;
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
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

/**
 * Phase-1 end-to-end (offline, no cluster): raw process-instance records fold through the projector
 * and their derived execution-time facts aggregate into the windowed dataset, verifying "N
 * instances completed per definition per hour".
 */
final class WindowedPipelineEndToEndTest {

  private static final long HOUR = 3_600_000L;
  private static final int VERSION = 1;
  private static final String TENANT = "<default>";

  @Test
  void shouldCountCompletionsPerDefinitionPerHourFromRecords() {
    // given — the Phase-1 stages: projector + windowed aggregator, no fact-topic
    final ProcessInstanceProjector projector =
        new ProcessInstanceProjector(new InMemoryBaseProjectionStore());
    final JdbcDataSource dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:win-e2e-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    final WindowedExecutionTimeAggregator aggregator =
        new WindowedExecutionTimeAggregator(dataSource, 60_000L);
    aggregator.initSchema();
    final long datasetId = 1L;
    final var datasets = java.util.List.of(new AggregateDataset(datasetId, HOUR));

    // definition 77: three instances complete in hour 0, one in hour 1; definition 88: one in hour
    // 0
    final long[][] instances = {
      {1L, 77L, 100L, 600L}, // complete at 600 (hour 0)
      {2L, 77L, 200L, 1_200L}, // hour 0
      {3L, 77L, 300L, 3_000L}, // hour 0
      {4L, 77L, HOUR + 100L, HOUR + 900L}, // hour 1
      {5L, 88L, 50L, 4_000L}, // hour 0
    };

    // when — drive activation + completion records through projector → windowed aggregator
    long position = 1L;
    for (final long[] instance : instances) {
      final long piKey = instance[0];
      final long defKey = instance[1];
      projector.apply(
          event(ProcessInstanceIntent.ELEMENT_ACTIVATED, piKey, defKey, instance[2], position++));
      projector
          .apply(
              event(
                  ProcessInstanceIntent.ELEMENT_COMPLETED, piKey, defKey, instance[3], position++))
          .ifPresent(fact -> aggregator.apply(fact, datasets));
    }

    // then — "last hour = N completed per definition"
    assertThat(aggregator.read(datasetId, 77L, VERSION, TENANT, 0L).orElseThrow().completedCount())
        .isEqualTo(3L);
    assertThat(
            aggregator.read(datasetId, 77L, VERSION, TENANT, HOUR).orElseThrow().completedCount())
        .isEqualTo(1L);
    assertThat(aggregator.read(datasetId, 88L, VERSION, TENANT, 0L).orElseThrow().completedCount())
        .isEqualTo(1L);
  }

  private static ZeebeRecord event(
      final ProcessInstanceIntent intent,
      final long processInstanceKey,
      final long processDefinitionKey,
      final long timestamp,
      final long position) {
    final ProcessInstanceRecord value =
        new ProcessInstanceRecord()
            .setProcessInstanceKey(processInstanceKey)
            .setProcessDefinitionKey(processDefinitionKey)
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
