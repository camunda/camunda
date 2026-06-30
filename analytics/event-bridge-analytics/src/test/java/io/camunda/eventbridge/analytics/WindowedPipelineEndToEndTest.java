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
import io.camunda.eventbridge.analytics.projection.ProcessInstanceProjector;
import io.camunda.eventbridge.analytics.projection.StateBackedProjectionStore;
import io.camunda.eventbridge.zeebe.connector.ZeebeRecord;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.CopiedRecord;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.UUID;
import org.agrona.concurrent.UnsafeBuffer;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

/**
 * Phase-1 end-to-end (offline, no cluster): records (incl. a {@code region} variable) fold through
 * the projector and their derived facts aggregate into per-region windowed cells — verifying
 * execution time grouped by region.
 */
final class WindowedPipelineEndToEndTest {

  private static final long HOUR = 3_600_000L;
  private static final int VERSION = 1;
  private static final String TENANT = "<default>";
  private static final long DS = 1L;

  private record Inst(long pi, long def, String region, long start, long end) {}

  @Test
  void shouldGroupExecutionTimeByRegionFromRecords() {
    // given — the Phase-1 stages: projector + windowed aggregator, no fact-topic
    final ProcessInstanceProjector projector =
        new ProcessInstanceProjector(StateBackedProjectionStore.inMemory());
    final JdbcDataSource dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:win-e2e-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    final WindowedExecutionTimeAggregator aggregator =
        new WindowedExecutionTimeAggregator(dataSource, 60_000L);
    aggregator.initSchema();
    final var datasets = List.of(new AggregateDataset(DS, HOUR));

    final List<Inst> instances =
        List.of(
            new Inst(101L, 77L, "EU", 100L, 600L), // hour 0
            new Inst(102L, 77L, "EU", 200L, 1_200L), // hour 0
            new Inst(103L, 77L, "US", 300L, 3_000L), // hour 0
            new Inst(104L, 77L, "EU", HOUR + 100L, HOUR + 900L), // hour 1
            new Inst(105L, 88L, "US", 50L, 4_000L)); // hour 0

    // when — for each instance: activated, region variable, completed
    long position = 1L;
    for (final Inst i : instances) {
      projector.apply(
          piEvent(ProcessInstanceIntent.ELEMENT_ACTIVATED, i.pi(), i.def(), i.start(), position++),
          fact -> {});
      projector.apply(variableEvent(i.pi(), "region", i.region(), position++), fact -> {});
      projector.apply(
          piEvent(ProcessInstanceIntent.ELEMENT_COMPLETED, i.pi(), i.def(), i.end(), position++),
          fact -> aggregator.apply(fact, datasets));
    }

    // then — execution time grouped by region
    assertThat(aggregator.read(DS, "EU", 77L, VERSION, TENANT, 0L).orElseThrow().completedCount())
        .isEqualTo(2L);
    assertThat(aggregator.read(DS, "US", 77L, VERSION, TENANT, 0L).orElseThrow().completedCount())
        .isEqualTo(1L);
    assertThat(aggregator.read(DS, "EU", 77L, VERSION, TENANT, HOUR).orElseThrow().completedCount())
        .isEqualTo(1L);
    assertThat(aggregator.read(DS, "US", 88L, VERSION, TENANT, 0L).orElseThrow().completedCount())
        .isEqualTo(1L);
    // EU hour 0 durations 500 + 1000 -> avg 750
    assertThat(
            aggregator.read(DS, "EU", 77L, VERSION, TENANT, 0L).orElseThrow().averageDurationMs())
        .isEqualTo(750.0);
  }

  private static ZeebeRecord piEvent(
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

  private static ZeebeRecord variableEvent(
      final long processInstanceKey, final String name, final String value, final long position) {
    final VariableRecord variable =
        new VariableRecord()
            .setProcessInstanceKey(processInstanceKey)
            .setScopeKey(processInstanceKey)
            .setName(BufferUtil.wrapString(name))
            .setValue(new UnsafeBuffer(MsgPackConverter.convertToMsgPack("\"" + value + "\"")));
    final RecordMetadata metadata =
        new RecordMetadata()
            .recordType(RecordType.EVENT)
            .valueType(ValueType.VARIABLE)
            .intent(VariableIntent.CREATED);
    final Record<VariableRecord> record =
        new CopiedRecord<>(
            variable, metadata, processInstanceKey, 1, position, position - 1, position);
    return new ZeebeRecord("zeebe-records", 1, position, record);
  }
}
