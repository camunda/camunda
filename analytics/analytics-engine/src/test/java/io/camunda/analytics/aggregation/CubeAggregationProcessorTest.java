/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.aggregation;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.dataset.CompiledDataset;
import io.camunda.analytics.dataset.DatasetCompiler;
import io.camunda.analytics.dataset.DatasetDeclaration;
import io.camunda.analytics.dataset.DatasetRegistry;
import io.camunda.analytics.dataset.FilterPredicate;
import io.camunda.analytics.dataset.RegisteredDataset;
import io.camunda.analytics.dimension.DimensionKey;
import io.camunda.analytics.dimension.DimensionKeySelector;
import io.camunda.analytics.dimension.DimensionKeyValue;
import io.camunda.analytics.dimension.DimensionType;
import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.meter.BoundMeter;
import io.camunda.analytics.meter.CompositeAccumulatorValue;
import io.camunda.analytics.meter.CompositeAggregateFunction;
import io.camunda.analytics.meter.InMemoryMeterIdStore;
import io.camunda.analytics.meter.Meter;
import io.camunda.analytics.meter.MeterCatalog;
import io.camunda.analytics.meter.MeterIdRegistry;
import io.camunda.analytics.projection.ProjectionMetrics;
import io.camunda.eventbridge.streaming.aggregate.SegmentSealingAggregation;
import io.camunda.eventbridge.streaming.aggregate.Segments;
import io.camunda.eventbridge.streaming.aggregate.SourceCoordinate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class CubeAggregationProcessorTest {

  private static final SourceCoordinate<Fact> COORDINATE =
      new SourceCoordinate<>() {
        @Override
        public int partition(final Fact fact) {
          return fact.sourcePartition();
        }

        @Override
        public long position(final Fact fact) {
          return fact.sourcePosition();
        }
      };

  @Test
  void shouldFireTheDatasetEmptyAlarmWhenFiltersMatchNothing() {
    // given a cube whose declared filter requires a field this test's facts never set
    final DatasetDeclaration declaration =
        DatasetDeclaration.builder("cube-a", FactType.PROCESS_INSTANCE)
            .dimension("bpmnProcessId", DimensionType.STRING)
            .meter(Meter.of("count", MeterCatalog.COUNT))
            .window(60_000L)
            .lateness(300_000L)
            .build();
    final DatasetRegistry registry = new DatasetRegistry();
    final RegisteredDataset registered = registry.admit(declaration, Map.of(), 0L);
    final DatasetCompiler compiler =
        new DatasetCompiler(
            MeterCatalog.withDefaults(), new MeterIdRegistry(new InMemoryMeterIdStore()));
    final CompiledDataset compiled = compiler.compile(registered.cubeId(), declaration);
    final List<BoundMeter<?, ?>> bounds = compiled.meterBounds();
    final ForwardingSegmentSink<Object[]> sink =
        new ForwardingSegmentSink<>(
            compiled.streamId(),
            new DimensionKeyValue(compiled.grain()),
            new CompositeAccumulatorValue(bounds));
    final SegmentSealingAggregation<Fact, DimensionKey, Object[]> aggregation =
        new SegmentSealingAggregation<>(
            new CompositeAggregateFunction(bounds),
            new DimensionKeySelector(compiled.grain()),
            COORDINATE,
            Fact::eventTime,
            compiled.finestTier().windows(),
            Segments.ofStride(10_000),
            sink);
    final CountingProjectionMetrics metrics = new CountingProjectionMetrics();
    final CubeAggregationProcessor processor =
        new CubeAggregationProcessor(
            FactType.PROCESS_INSTANCE,
            registered,
            List.of(FilterPredicate.equals("neverSetField", "anything")),
            aggregation,
            sink,
            metrics);

    // when this cube inspects many admitted facts, none of which pass the filter
    for (long i = 0; i < CubeAggregationProcessor.SILENT_FACT_THRESHOLD; i++) {
      processor.process(
          Fact.builder(FactType.PROCESS_INSTANCE)
              .field("bpmnProcessId", "order")
              .eventTime(0L)
              .source(1, i)
              .build());
    }

    // then it stays silently empty, and the commit-boundary check fires the alarm exactly once
    assertThat(processor.silent()).isTrue();
    processor.warnIfSilent();
    processor.warnIfSilent(); // idempotent: a repeated commit-boundary check does not re-fire
    assertThat(metrics.emptyAlarms).containsExactly("cube-a");
  }

  @Test
  void shouldNotFireTheAlarmWhenFiltersMatch() {
    // given a cube whose filter matches every fact this test folds
    final DatasetDeclaration declaration =
        DatasetDeclaration.builder("cube-b", FactType.PROCESS_INSTANCE)
            .dimension("bpmnProcessId", DimensionType.STRING)
            .meter(Meter.of("count", MeterCatalog.COUNT))
            .window(60_000L)
            .lateness(300_000L)
            .build();
    final DatasetRegistry registry = new DatasetRegistry();
    final RegisteredDataset registered = registry.admit(declaration, Map.of(), 0L);
    final DatasetCompiler compiler =
        new DatasetCompiler(
            MeterCatalog.withDefaults(), new MeterIdRegistry(new InMemoryMeterIdStore()));
    final CompiledDataset compiled = compiler.compile(registered.cubeId(), declaration);
    final List<BoundMeter<?, ?>> bounds = compiled.meterBounds();
    final ForwardingSegmentSink<Object[]> sink =
        new ForwardingSegmentSink<>(
            compiled.streamId(),
            new DimensionKeyValue(compiled.grain()),
            new CompositeAccumulatorValue(bounds));
    final SegmentSealingAggregation<Fact, DimensionKey, Object[]> aggregation =
        new SegmentSealingAggregation<>(
            new CompositeAggregateFunction(bounds),
            new DimensionKeySelector(compiled.grain()),
            COORDINATE,
            Fact::eventTime,
            compiled.finestTier().windows(),
            Segments.ofStride(10_000),
            sink);
    final CountingProjectionMetrics metrics = new CountingProjectionMetrics();
    final CubeAggregationProcessor processor =
        new CubeAggregationProcessor(
            FactType.PROCESS_INSTANCE, registered, List.of(), aggregation, sink, metrics);

    // when every fact folds
    for (long i = 0; i < CubeAggregationProcessor.SILENT_FACT_THRESHOLD; i++) {
      processor.process(
          Fact.builder(FactType.PROCESS_INSTANCE)
              .field("bpmnProcessId", "order")
              .eventTime(0L)
              .source(1, i)
              .build());
    }

    // then the alarm never fires
    processor.warnIfSilent();
    assertThat(metrics.emptyAlarms).isEmpty();
  }

  /** A {@link ProjectionMetrics} fake recording every {@code datasetEmptyAlarm} call. */
  private static final class CountingProjectionMetrics implements ProjectionMetrics {

    private final List<String> emptyAlarms = new ArrayList<>();

    @Override
    public void duplicateSkipped() {}

    @Override
    public void foldRowMissing() {}

    @Override
    public void factDropped() {}

    @Override
    public void factEmitted(final FactType factType) {}

    @Override
    public void datasetEmptyAlarm(final String datasetName) {
      emptyAlarms.add(datasetName);
    }
  }
}
