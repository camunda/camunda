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
import io.camunda.analytics.streaming.state.api.KeyValueStore;
import io.camunda.analytics.streaming.state.memory.InMemoryKeyValueStore;
import io.camunda.eventbridge.analytics.element.ElementExecutionFact;
import io.camunda.eventbridge.analytics.fact.ProcessExecutionFact;
import io.camunda.eventbridge.analytics.fact.ProcessInstanceExecutionTimeFact;
import io.camunda.eventbridge.zeebe.connector.ZeebeRecord;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import java.util.HashMap;
import java.util.Map;

/**
 * The single base-projection fold: each consumed record is processed once and updates the shared
 * read model of process execution — per-instance state (start/end/variables) and per-element start
 * times. From that one fold it derives the facts and emits them into the collector:
 *
 * <ul>
 *   <li>the root {@code PROCESS} element completing → a {@link ProcessInstanceExecutionTimeFact};
 *   <li>any other element completing → an {@link ElementExecutionFact};
 *   <li>{@code VARIABLE} records enrich the instance (so a fact can be grouped by e.g. region).
 * </ul>
 *
 * <p>The runtime fans the emitted {@link ProcessExecutionFact}s out to the rollup(s) for each
 * concrete type — so multiple metrics are served from this one projection without re-reading the
 * stream. The fold is a deterministic function of the in-order, per-partition source stream and
 * does not track source offsets (the runtime owns that).
 */
public final class ProcessExecutionProjector
    implements Projector<ZeebeRecord, ProcessExecutionFact> {

  private final BaseProjectionStore instances;
  private final KeyValueStore<DbString, DbLong> elementStarts;
  private final DbString elementKey = new DbString();
  private final DbLong startTime = new DbLong();

  public ProcessExecutionProjector(final BaseProjectionStore instances) {
    this(instances, new InMemoryKeyValueStore<>(new DbString(), new DbLong()));
  }

  public ProcessExecutionProjector(
      final BaseProjectionStore instances, final KeyValueStore<DbString, DbLong> elementStarts) {
    this.instances = instances;
    this.elementStarts = elementStarts;
  }

  @Override
  public void apply(final ZeebeRecord zeebeRecord, final Collector<ProcessExecutionFact> out) {
    final Record<?> record = zeebeRecord.record();

    if (record.getValueType() == ValueType.VARIABLE
        && record.getValue() instanceof final VariableRecordValue variable) {
      captureVariable(variable);
      return;
    }

    if (record.getValueType() != ValueType.PROCESS_INSTANCE
        || !(record.getValue() instanceof final ProcessInstanceRecordValue value)
        || !(record.getIntent() instanceof final ProcessInstanceIntent intent)) {
      return;
    }

    if (value.getBpmnElementType() == BpmnElementType.PROCESS) {
      foldInstance(zeebeRecord, value, intent, out);
    } else {
      foldElement(zeebeRecord, value, intent, out);
    }
  }

  /** Root process element: maintain the instance projection, emit the execution-time fact once. */
  private void foldInstance(
      final ZeebeRecord zeebeRecord,
      final ProcessInstanceRecordValue value,
      final ProcessInstanceIntent intent,
      final Collector<ProcessExecutionFact> out) {
    final Record<?> record = zeebeRecord.record();
    final long key = value.getProcessInstanceKey();
    final ProcessInstanceProjection previous = instances.get(key).orElse(null);
    long start = previous != null ? previous.startTime() : ProcessInstanceProjection.UNSET;
    long end = previous != null ? previous.endTime() : ProcessInstanceProjection.UNSET;
    boolean terminated = previous != null && previous.terminated();
    boolean factEmitted = previous != null && previous.factEmitted();
    final Map<String, String> variables = previous != null ? previous.variables() : Map.of();

    switch (intent) {
      case ELEMENT_ACTIVATED -> start = record.getTimestamp();
      case ELEMENT_COMPLETED -> {
        end = record.getTimestamp();
        terminated = false;
      }
      case ELEMENT_TERMINATED -> {
        end = record.getTimestamp();
        terminated = true;
      }
      default -> {
        return;
      }
    }

    if (start != ProcessInstanceProjection.UNSET
        && end != ProcessInstanceProjection.UNSET
        && !factEmitted) {
      out.collect(
          new ProcessInstanceExecutionTimeFact(
              key,
              value.getProcessDefinitionKey(),
              value.getBpmnProcessId(),
              value.getVersion(),
              value.getTenantId(),
              start,
              end,
              end - start,
              !terminated,
              zeebeRecord.partitionId(),
              zeebeRecord.offset(),
              Map.copyOf(variables)));
      factEmitted = true;
    }

    instances.put(
        new ProcessInstanceProjection(
            key,
            value.getProcessDefinitionKey(),
            value.getBpmnProcessId(),
            value.getVersion(),
            value.getTenantId(),
            start,
            end,
            terminated,
            factEmitted,
            variables));
  }

  /** Non-root element: track its activation time, emit its execution fact on completion. */
  private void foldElement(
      final ZeebeRecord zeebeRecord,
      final ProcessInstanceRecordValue value,
      final ProcessInstanceIntent intent,
      final Collector<ProcessExecutionFact> out) {
    final Record<?> record = zeebeRecord.record();
    // record key is not carried in the consumed stream, so key on (instance, element)
    elementKey.wrapString(value.getProcessInstanceKey() + ":" + value.getElementId());
    switch (intent) {
      case ELEMENT_ACTIVATED -> {
        startTime.wrapLong(record.getTimestamp());
        elementStarts.put(elementKey, startTime);
      }
      case ELEMENT_COMPLETED, ELEMENT_TERMINATED ->
          elementStarts
              .get(elementKey)
              .ifPresent(
                  start -> {
                    final long duration = record.getTimestamp() - start.getValue();
                    out.collect(
                        new ElementExecutionFact(
                            value.getBpmnProcessId(),
                            value.getProcessDefinitionKey(),
                            value.getVersion(),
                            value.getTenantId(),
                            value.getElementId(),
                            value.getBpmnElementType().name(),
                            duration,
                            record.getTimestamp()));
                    elementStarts.delete(elementKey);
                  });
      default -> {
        // other element lifecycle intents do not bound execution time
      }
    }
  }

  private void captureVariable(final VariableRecordValue variable) {
    final long key = variable.getProcessInstanceKey();
    final ProcessInstanceProjection previous = instances.get(key).orElse(null);
    final Map<String, String> variables =
        previous != null ? new HashMap<>(previous.variables()) : new HashMap<>();
    variables.put(variable.getName(), unquote(variable.getValue()));
    if (previous == null) {
      instances.put(ProcessInstanceProjection.withVariablesOnly(key, variables));
    } else {
      instances.put(
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
