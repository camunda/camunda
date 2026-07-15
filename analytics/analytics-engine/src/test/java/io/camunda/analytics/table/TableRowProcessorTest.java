/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.table;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.analytics.dataset.CompiledDataset;
import io.camunda.analytics.dataset.CompiledTable;
import io.camunda.analytics.dataset.DatasetCompiler;
import io.camunda.analytics.dataset.DatasetDeclaration;
import io.camunda.analytics.dataset.DatasetRegistry;
import io.camunda.analytics.dataset.FilterPredicate;
import io.camunda.analytics.dataset.RegisteredDataset;
import io.camunda.analytics.dimension.DimensionKey;
import io.camunda.analytics.dimension.DimensionType;
import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.fact.Transition;
import io.camunda.analytics.meter.InMemoryMeterIdStore;
import io.camunda.analytics.meter.MeterCatalog;
import io.camunda.analytics.meter.MeterIdRegistry;
import io.camunda.analytics.serving.spi.DatasetWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The Stage-1 table node's upsert/evict flow: admitted facts matching the row filters upsert one
 * row per key; facts matching the eviction conjunction delete their key's row instead — checked
 * before the row filters, since the evicting fact (a completion) deliberately fails the filters
 * that admit rows (the activation).
 */
final class TableRowProcessorTest {

  private final RecordingWriter writer = new RecordingWriter();

  private static DatasetDeclaration openInstances() {
    return DatasetDeclaration.builder("open-rows", FactType.PROCESS_INSTANCE)
        .filterEquals("transition", Transition.ACTIVATED.name())
        .asTable("processInstanceKey")
        .evictWhen(FilterPredicate.notEquals("transition", Transition.ACTIVATED.name()))
        .dimension("bpmnProcessId", DimensionType.STRING)
        .dimension("startTime", DimensionType.LONG)
        .build();
  }

  private TableRowProcessor processor(final DatasetDeclaration declaration) {
    final RegisteredDataset registered = new DatasetRegistry().admit(declaration, Map.of(), 0L);
    final CompiledTable table =
        new DatasetCompiler(
                MeterCatalog.withDefaults(), new MeterIdRegistry(new InMemoryMeterIdStore()))
            .compileTable(registered.cubeId(), declaration);
    return new TableRowProcessor(registered, table, writer);
  }

  private static Fact fact(final Transition transition, final long key) {
    return Fact.builder(FactType.PROCESS_INSTANCE)
        .transition(transition)
        .field("processInstanceKey", key)
        .field("bpmnProcessId", "order-process")
        .field("startTime", 1_000L)
        .build();
  }

  @Test
  void shouldUpsertOnActivationAndDeleteOnEviction() {
    // given an open-instances-style table (insert on ACTIVATED, evict on anything else)
    final TableRowProcessor processor = processor(openInstances());

    // when two instances activate and one completes
    processor.process(fact(Transition.ACTIVATED, 1L));
    processor.process(fact(Transition.ACTIVATED, 2L));
    processor.process(fact(Transition.COMPLETED, 1L));

    // then both rows were upserted and the completed key's row was deleted, in that order
    assertThat(writer.ops)
        .extracting(Op::kind, Op::rowKey)
        .containsExactly(tuple("upsert", "1"), tuple("upsert", "2"), tuple("delete", "1"));
    // and the eviction never wrote a row for its own fact (COMPLETED fails the row filters)
    assertThat(writer.ops.get(2).values()).isNull();
  }

  @Test
  void shouldIgnoreAnEvictingFactWithoutTheKeyField() {
    // given the same table
    final TableRowProcessor processor = processor(openInstances());

    // when an evicting fact carries no key field
    processor.process(
        Fact.builder(FactType.PROCESS_INSTANCE).transition(Transition.COMPLETED).build());

    // then nothing was written or deleted
    assertThat(writer.ops).isEmpty();
  }

  @Test
  void shouldNeverEvictWhenNoEvictionPredicatesAreDeclared() {
    // given a plain table without eviction (the raw-completed-instances shape)
    final DatasetDeclaration declaration =
        DatasetDeclaration.builder("raw-rows", FactType.PROCESS_INSTANCE)
            .filterEquals("transition", Transition.COMPLETED.name())
            .asTable("processInstanceKey")
            .dimension("bpmnProcessId", DimensionType.STRING)
            .build();
    final TableRowProcessor processor = processor(declaration);

    // when a completion folds
    processor.process(fact(Transition.COMPLETED, 7L));

    // then it upserts as before — an empty eviction conjunction never fires
    assertThat(writer.ops).extracting(Op::kind, Op::rowKey).containsExactly(tuple("upsert", "7"));
  }

  /** One recorded writer call; {@code values} is null for a delete. */
  private record Op(String kind, String rowKey, List<Object> values) {}

  private static final class RecordingWriter implements DatasetWriter {
    private final List<Op> ops = new ArrayList<>();

    @Override
    public void upsertCell(
        final CompiledDataset dataset,
        final DimensionKey key,
        final long windowStart,
        final long windowSize,
        final byte[] compositeAccumulator) {}

    @Override
    public void upsertRow(
        final CompiledTable table, final String rowKey, final List<Object> values) {
      ops.add(new Op("upsert", rowKey, values));
    }

    @Override
    public void deleteRow(final CompiledTable table, final String rowKey) {
      ops.add(new Op("delete", rowKey, null));
    }

    @Override
    public void upsertSnapshotRow(
        final CompiledDataset dataset,
        final DimensionKey key,
        final long sampleTime,
        final byte[] compositeAccumulator) {}

    @Override
    public void flush() {}

    @Override
    public void close() {}
  }
}
