/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.projection;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.dataset.DimensionSpec;
import io.camunda.analytics.dimension.DimensionColumn;
import io.camunda.analytics.dimension.DimensionKey;
import io.camunda.analytics.dimension.DimensionKeySelector;
import io.camunda.analytics.dimension.DimensionSchema;
import io.camunda.analytics.dimension.DimensionType;
import io.camunda.analytics.dimension.FactRow;
import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.fact.Transition;
import io.camunda.analytics.meter.BoundMeter;
import io.camunda.analytics.meter.Meter;
import io.camunda.analytics.meter.MeterCatalog;
import io.camunda.analytics.metric.ExecutionTimeSummaryResult;
import io.camunda.analytics.metric.LifecycleSummaryResult;
import io.camunda.eventbridge.streaming.aggregate.AggregateFunction;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * End-to-end Phase 2 proof: a scripted source stream flows through the generic {@link
 * AnalyticsFactProjector} into generic facts, which are then aggregated through the declared {@link
 * MeterCatalog} meters over a declared grain — exercising projector → fact → meter core together
 * and asserting the numbers. No live pipeline is touched.
 */
final class AnalyticsFactPipelineTest {

  private final BaseProjectionStore store = StateBackedProjectionStore.inMemory();
  private final AnalyticsFactProjector projector = new AnalyticsFactProjector(store);
  private final MeterCatalog catalog = MeterCatalog.withDefaults();
  private final List<Fact> facts = new ArrayList<>();

  private void run() {
    long position = 1;
    // instance 1 (EU) completes in 500ms
    apply(process(1L, ProcessInstanceIntent.ELEMENT_ACTIVATED, 1000L, position++));
    apply(variable(1L, "region", "EU", position++));
    apply(process(1L, ProcessInstanceIntent.ELEMENT_COMPLETED, 1500L, position++));
    // instance 2 (US) completes in 800ms
    apply(process(2L, ProcessInstanceIntent.ELEMENT_ACTIVATED, 1000L, position++));
    apply(variable(2L, "region", "US", position++));
    apply(process(2L, ProcessInstanceIntent.ELEMENT_COMPLETED, 1800L, position++));
    // instance 3 is terminated after 300ms (no region variable)
    apply(process(3L, ProcessInstanceIntent.ELEMENT_ACTIVATED, 1000L, position++));
    apply(process(3L, ProcessInstanceIntent.ELEMENT_TERMINATED, 1300L, position++));
  }

  @Test
  void shouldAggregateLifecycleSummaryOverProjectedFacts() {
    // given the scripted stream projected into generic facts
    run();
    final DimensionSchema byProcess =
        DimensionSchema.of(new DimensionColumn("bpmnProcessId", DimensionType.STRING));

    // when the lifecycle-summary meter aggregates all process-instance facts, grouped by process
    final Map<DimensionKey, ?> cells =
        resultsByKey(
            new DimensionKeySelector(byProcess),
            catalog.bind(Meter.of("lifecycle", MeterCatalog.LIFECYCLE_SUMMARY, "durationMs")),
            f -> f.factType() == FactType.PROCESS_INSTANCE);

    // then one cell holds every count and the duration family from a single fold
    final LifecycleSummaryResult result =
        (LifecycleSummaryResult) cells.get(DimensionKey.of(byProcess, "order"));
    assertThat(result.activated()).isEqualTo(3L);
    assertThat(result.completed()).isEqualTo(2L);
    assertThat(result.terminated()).isEqualTo(1L);
    assertThat(result.duration().count()).isEqualTo(3L); // 500, 800, 300
    assertThat(result.duration().minMs()).isEqualTo(300L);
    assertThat(result.duration().maxMs()).isEqualTo(800L);
  }

  @Test
  void shouldGroupCompletedInstancesByVariableDimension() {
    // given the projected facts (variables enriched at completion, namespaced var.region)
    run();
    final DimensionSchema byRegion =
        DimensionSchema.of(
            new DimensionColumn(DimensionSpec.VARIABLE_PREFIX + "region", DimensionType.STRING));

    // when the execution-time-summary meter aggregates completed instances grouped by region
    final Map<DimensionKey, ?> cells =
        resultsByKey(
            new DimensionKeySelector(byRegion),
            catalog.bind(Meter.of("duration", MeterCatalog.EXECUTION_TIME_SUMMARY, "durationMs")),
            f ->
                f.factType() == FactType.PROCESS_INSTANCE
                    && Transition.COMPLETED.name().equals(f.get(Fact.TRANSITION)));

    // then the variable dimension carried through projector → fact → key splits the cells
    final ExecutionTimeSummaryResult eu =
        (ExecutionTimeSummaryResult) cells.get(DimensionKey.of(byRegion, "EU"));
    final ExecutionTimeSummaryResult us =
        (ExecutionTimeSummaryResult) cells.get(DimensionKey.of(byRegion, "US"));
    assertThat(eu.count()).isEqualTo(1L);
    assertThat(eu.averageMs()).isEqualTo(500.0);
    assertThat(us.count()).isEqualTo(1L);
    assertThat(us.averageMs()).isEqualTo(800.0);
  }

  private void apply(final SourceRecord record) {
    projector.apply(record, facts::add);
  }

  private <ACC, OUT> Map<DimensionKey, OUT> resultsByKey(
      final DimensionKeySelector selector,
      final BoundMeter<ACC, OUT> bound,
      final Predicate<Fact> filter) {
    final AggregateFunction<FactRow, ACC, OUT> aggregate = bound.aggregate();
    final Map<DimensionKey, ACC> cells = new HashMap<>();
    for (final Fact fact : facts) {
      if (filter.test(fact)) {
        cells.merge(
            selector.getKey(fact),
            aggregate.add(fact, aggregate.createAccumulator()),
            aggregate::merge);
      }
    }
    final Map<DimensionKey, OUT> results = new HashMap<>();
    cells.forEach((key, acc) -> results.put(key, aggregate.getResult(acc)));
    return results;
  }

  private static SourceRecord process(
      final long instanceKey,
      final ProcessInstanceIntent intent,
      final long timestamp,
      final long position) {
    final ProcessInstanceRecord value =
        new ProcessInstanceRecord()
            .setProcessInstanceKey(instanceKey)
            .setProcessDefinitionKey(77L)
            .setBpmnProcessId("order")
            .setVersion(3)
            .setTenantId("<default>")
            .setElementId("order")
            .setBpmnElementType(BpmnElementType.PROCESS);
    final RecordMetadata metadata =
        new RecordMetadata()
            .recordType(RecordType.EVENT)
            .valueType(ValueType.PROCESS_INSTANCE)
            .intent(intent);
    final Record<ProcessInstanceRecord> record =
        new CopiedRecord<>(value, metadata, instanceKey, 1, position, position - 1, timestamp);
    return new SourceRecord(1, position, record);
  }

  private static SourceRecord variable(
      final long instanceKey, final String name, final String value, final long position) {
    final VariableRecord variable =
        new VariableRecord()
            .setProcessInstanceKey(instanceKey)
            .setScopeKey(instanceKey)
            .setName(BufferUtil.wrapString(name))
            .setValue(new UnsafeBuffer(MsgPackConverter.convertToMsgPack("\"" + value + "\"")));
    final RecordMetadata metadata =
        new RecordMetadata()
            .recordType(RecordType.EVENT)
            .valueType(ValueType.VARIABLE)
            .intent(VariableIntent.CREATED);
    final Record<VariableRecord> record =
        new CopiedRecord<>(variable, metadata, instanceKey, 1, position, position - 1, position);
    return new SourceRecord(1, position, record);
  }
}
