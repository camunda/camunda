/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.pipeline.stage;

import io.camunda.analytics.dataset.CompiledTable;
import io.camunda.analytics.dataset.FactBinding;
import io.camunda.analytics.dimension.DimensionColumn;
import io.camunda.analytics.dimension.DimensionType;
import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.serving.spi.DatasetWriter;
import io.camunda.eventbridge.streaming.processor.Processor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Writes each deployed process definition straight into the serving store as a built-in raw table,
 * bypassing the declared-dataset machinery — no cube, no subscription/activation, no shuffle. A
 * process definition is deployment metadata (id, version, BPMN XML) the dashboard looks up, not an
 * aggregation over facts, so it takes the shortest path: the base projection derives one {@code
 * PROCESS_DEFINITION} fact, and this terminal node ({@code Out = Void}) upserts one row keyed by
 * {@code processDefinitionKey} — idempotent under replay, so no reduce is needed.
 *
 * <p>TODO(analytics): this is wired "for now" as a built-in table with a reserved id rather than a
 * first-class process-definition store (or a declared dataset). Promote it once the dashboard read
 * path for definitions is wired; the reserved id keeps it clear of the allocated dataset range.
 */
public final class ProcessDefinitionSink implements Processor<Fact, Void> {

  /** Reserved cube id for the built-in table, kept clear of the allocated dataset id range. */
  private static final long TABLE_ID = 9_000L;

  /** The built-in process-definitions table; exposed so the stage can ensure its schema. */
  public static final CompiledTable TABLE =
      new CompiledTable(
          TABLE_ID,
          "process_definitions",
          new FactBinding(FactType.PROCESS_DEFINITION, List.of(), Map.of()),
          "processDefinitionKey",
          List.of(
              new DimensionColumn("bpmnProcessId", DimensionType.STRING),
              new DimensionColumn("processDefinitionKey", DimensionType.LONG),
              new DimensionColumn("version", DimensionType.INT),
              new DimensionColumn("tenantId", DimensionType.STRING),
              new DimensionColumn("bpmnXml", DimensionType.STRING)));

  private final DatasetWriter writer;

  public ProcessDefinitionSink(final DatasetWriter writer) {
    this.writer = writer;
  }

  @Override
  public void process(final Fact fact) {
    if (fact.factType() != FactType.PROCESS_DEFINITION) {
      return;
    }
    final Object key = fact.get(TABLE.keyField());
    if (key == null) {
      return;
    }
    final List<Object> values = new ArrayList<>(TABLE.columns().size());
    for (final DimensionColumn column : TABLE.columns()) {
      values.add(fact.get(column.name()));
    }
    writer.upsertRow(TABLE, String.valueOf(key), values);
  }
}
