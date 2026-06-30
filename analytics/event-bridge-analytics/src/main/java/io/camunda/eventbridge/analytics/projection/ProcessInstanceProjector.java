/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.projection;

import io.camunda.analytics.streaming.fold.Collector;
import io.camunda.analytics.streaming.fold.Projector;
import io.camunda.eventbridge.analytics.fact.ProcessInstanceExecutionTimeFact;
import io.camunda.eventbridge.zeebe.connector.ZeebeRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import java.util.HashMap;
import java.util.Map;

/**
 * Stage 1 — the base-projection fold, as a library {@link Projector}. Reads the consumed record
 * stream and maintains one {@link ProcessInstanceProjection} per process instance. When an
 * instance's root process element reaches {@code ELEMENT_COMPLETED}/{@code ELEMENT_TERMINATED} and
 * a start time is known, it emits the execution-time fact exactly once into the {@link Collector}.
 *
 * <p>The fold is a deterministic function of the (in-order, per-partition) source stream — every
 * replica that applies the same records reaches the same state and emits the same facts. It reads
 * the source and updates its own state only; it never tracks source offsets (the runtime owns that)
 * and never writes back to the consumed stream.
 */
public final class ProcessInstanceProjector
    implements Projector<ZeebeRecord, ProcessInstanceExecutionTimeFact> {

  private final BaseProjectionStore store;

  public ProcessInstanceProjector(final BaseProjectionStore store) {
    this.store = store;
  }

  /** Folds one record into the projection, emitting a fact when an instance first completes. */
  @Override
  public void apply(
      final ZeebeRecord zeebeRecord, final Collector<ProcessInstanceExecutionTimeFact> out) {
    final Record<?> record = zeebeRecord.record();

    // Accumulate variables so a derived fact can be enriched with them (e.g. group by region).
    if (record.getValueType() == ValueType.VARIABLE
        && record.getValue() instanceof final VariableRecordValue variable) {
      captureVariable(variable);
      return;
    }

    if (record.getValueType() != ValueType.PROCESS_INSTANCE
        || !(record.getValue() instanceof final ProcessInstanceRecordValue value)
        || value.getBpmnElementType() != BpmnElementType.PROCESS
        || !(record.getIntent() instanceof final ProcessInstanceIntent intent)) {
      return;
    }

    final long key = value.getProcessInstanceKey();
    final ProcessInstanceProjection previous = store.get(key).orElse(null);
    long startTime = previous != null ? previous.startTime() : ProcessInstanceProjection.UNSET;
    long endTime = previous != null ? previous.endTime() : ProcessInstanceProjection.UNSET;
    boolean terminated = previous != null && previous.terminated();
    boolean factEmitted = previous != null && previous.factEmitted();
    final Map<String, String> variables = previous != null ? previous.variables() : Map.of();

    switch (intent) {
      case ELEMENT_ACTIVATED -> startTime = record.getTimestamp();
      case ELEMENT_COMPLETED -> {
        endTime = record.getTimestamp();
        terminated = false;
      }
      case ELEMENT_TERMINATED -> {
        endTime = record.getTimestamp();
        terminated = true;
      }
      default -> {
        // other process-instance lifecycle intents do not affect execution time
        return;
      }
    }

    if (startTime != ProcessInstanceProjection.UNSET
        && endTime != ProcessInstanceProjection.UNSET
        && !factEmitted) {
      out.collect(
          new ProcessInstanceExecutionTimeFact(
              key,
              value.getProcessDefinitionKey(),
              value.getBpmnProcessId(),
              value.getVersion(),
              value.getTenantId(),
              startTime,
              endTime,
              endTime - startTime,
              !terminated,
              zeebeRecord.partitionId(),
              zeebeRecord.offset(),
              Map.copyOf(variables)));
      factEmitted = true;
    }

    store.put(
        new ProcessInstanceProjection(
            key,
            value.getProcessDefinitionKey(),
            value.getBpmnProcessId(),
            value.getVersion(),
            value.getTenantId(),
            startTime,
            endTime,
            terminated,
            factEmitted,
            variables));
  }

  private void captureVariable(final VariableRecordValue variable) {
    final long key = variable.getProcessInstanceKey();
    final ProcessInstanceProjection previous = store.get(key).orElse(null);
    final Map<String, String> variables =
        previous != null ? new HashMap<>(previous.variables()) : new HashMap<>();
    variables.put(variable.getName(), unquote(variable.getValue()));
    if (previous == null) {
      store.put(ProcessInstanceProjection.withVariablesOnly(key, variables));
    } else {
      store.put(
          new ProcessInstanceProjection(
              previous.processInstanceKey(),
              previous.processDefinitionKey(),
              previous.bpmnProcessId(),
              previous.version(),
              previous.tenantId(),
              previous.startTime(),
              previous.endTime(),
              previous.terminated(),
              previous.factEmitted(),
              variables));
    }
  }

  /** Variable values arrive as JSON; strip the quotes from a JSON string so {@code "EU"} → EU. */
  private static String unquote(final String jsonValue) {
    if (jsonValue.length() >= 2 && jsonValue.startsWith("\"") && jsonValue.endsWith("\"")) {
      return jsonValue.substring(1, jsonValue.length() - 1);
    }
    return jsonValue;
  }
}
