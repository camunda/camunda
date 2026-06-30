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

/**
 * The single base-projection fold: each consumed record is processed once. Every element — the root
 * {@code PROCESS} included, since a process instance is just the root element — is tracked the same
 * way: {@code ELEMENT_ACTIVATED} records its start time, and {@code ELEMENT_COMPLETED}/{@code
 * ELEMENT_TERMINATED} reads that start, derives the duration, and emits a fact:
 *
 * <ul>
 *   <li>the {@code PROCESS} element completing → a {@link ProcessInstanceExecutionTimeFact},
 *       enriched with the instance's variables (so it can be grouped by e.g. region);
 *   <li>any other element completing → an {@link ElementExecutionFact};
 *   <li>{@code VARIABLE} records accumulate the instance's variables.
 * </ul>
 *
 * <p>The start is deleted on completion, so a duplicate completion derives nothing — that is the
 * emit-once guard (no separate flag needed). The runtime fans the emitted {@link
 * ProcessExecutionFact}s out to the rollup(s) for each concrete type, so multiple metrics are
 * served from this one projection without re-reading the stream. The fold is a deterministic
 * function of the in-order, per-partition source stream.
 */
public final class ProcessExecutionProjector
    implements Projector<ZeebeRecord, ProcessExecutionFact> {

  private final BaseProjectionStore store;
  private final KeyValueStore<DbString, DbLong> elementStarts;
  private final DbString elementKey = new DbString();
  private final DbLong startTime = new DbLong();

  public ProcessExecutionProjector(final BaseProjectionStore store) {
    this(store, new InMemoryKeyValueStore<>(new DbString(), new DbLong()));
  }

  public ProcessExecutionProjector(
      final BaseProjectionStore store, final KeyValueStore<DbString, DbLong> elementStarts) {
    this.store = store;
    this.elementStarts = elementStarts;
  }

  @Override
  public void apply(final ZeebeRecord zeebeRecord, final Collector<ProcessExecutionFact> out) {
    final Record<?> record = zeebeRecord.record();

    if (record.getValueType() == ValueType.VARIABLE
        && record.getValue() instanceof final VariableRecordValue variable) {
      store.putVariable(
          variable.getProcessInstanceKey(), variable.getName(), unquote(variable.getValue()));
      return;
    }

    if (record.getValueType() != ValueType.PROCESS_INSTANCE
        || !(record.getValue() instanceof final ProcessInstanceRecordValue value)
        || !(record.getIntent() instanceof final ProcessInstanceIntent intent)) {
      return;
    }

    foldElement(zeebeRecord, value, intent, out);
  }

  /**
   * Track an element's activation time; on completion derive its fact (process or plain element).
   */
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
                    emit(zeebeRecord, value, intent, record.getTimestamp() - start.getValue(), out);
                    elementStarts.delete(elementKey);
                  });
      default -> {
        // other element lifecycle intents do not bound execution time
      }
    }
  }

  private void emit(
      final ZeebeRecord zeebeRecord,
      final ProcessInstanceRecordValue value,
      final ProcessInstanceIntent intent,
      final long duration,
      final Collector<ProcessExecutionFact> out) {
    final Record<?> record = zeebeRecord.record();
    if (value.getBpmnElementType() == BpmnElementType.PROCESS) {
      out.collect(
          new ProcessInstanceExecutionTimeFact(
              value.getProcessInstanceKey(),
              value.getProcessDefinitionKey(),
              value.getBpmnProcessId(),
              value.getVersion(),
              value.getTenantId(),
              record.getTimestamp() - duration,
              record.getTimestamp(),
              duration,
              intent == ProcessInstanceIntent.ELEMENT_COMPLETED,
              zeebeRecord.partitionId(),
              zeebeRecord.offset(),
              store.getVariables(value.getProcessInstanceKey())));
      store.deleteVariables(value.getProcessInstanceKey());
    } else {
      out.collect(
          new ElementExecutionFact(
              value.getBpmnProcessId(),
              value.getProcessDefinitionKey(),
              value.getVersion(),
              value.getTenantId(),
              value.getElementId(),
              value.getBpmnElementType().name(),
              duration,
              record.getTimestamp(),
              zeebeRecord.partitionId(),
              zeebeRecord.offset()));
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
