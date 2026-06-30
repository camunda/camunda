/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.element;

import io.camunda.analytics.streaming.fold.Collector;
import io.camunda.analytics.streaming.fold.Projector;
import io.camunda.analytics.streaming.state.api.KeyValueStore;
import io.camunda.analytics.streaming.state.memory.InMemoryKeyValueStore;
import io.camunda.eventbridge.zeebe.connector.ZeebeRecord;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;

/**
 * The element-level fold: tracks each flow-node instance's activation time and, on completion or
 * termination, emits an {@link ElementExecutionFact} with its duration. The root {@code PROCESS}
 * element is skipped — that is the process-instance metric's concern.
 *
 * <p>State is the start time keyed by element-instance key; everything else (element id,
 * definition, type) is read off the completion record, so the state is just a {@code long}.
 */
public final class ElementExecutionProjector
    implements Projector<ZeebeRecord, ElementExecutionFact> {

  private final KeyValueStore<DbLong, DbLong> startTimes;
  private final DbLong elementInstanceKey = new DbLong();
  private final DbLong startTime = new DbLong();

  /**
   * Uses an in-memory start-time store (in-flight elements are short-lived and replay-rebuildable).
   */
  public ElementExecutionProjector() {
    this(new InMemoryKeyValueStore<>(new DbLong(), new DbLong()));
  }

  public ElementExecutionProjector(final KeyValueStore<DbLong, DbLong> startTimes) {
    this.startTimes = startTimes;
  }

  @Override
  public void apply(final ZeebeRecord zeebeRecord, final Collector<ElementExecutionFact> out) {
    final Record<?> record = zeebeRecord.record();
    if (record.getValueType() != ValueType.PROCESS_INSTANCE
        || !(record.getValue() instanceof final ProcessInstanceRecordValue value)
        || value.getBpmnElementType() == BpmnElementType.PROCESS
        || !(record.getIntent() instanceof final ProcessInstanceIntent intent)) {
      return;
    }

    elementInstanceKey.wrapLong(record.getKey());
    switch (intent) {
      case ELEMENT_ACTIVATED -> {
        startTime.wrapLong(record.getTimestamp());
        startTimes.put(elementInstanceKey, startTime);
      }
      case ELEMENT_COMPLETED, ELEMENT_TERMINATED ->
          startTimes
              .get(elementInstanceKey)
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
                    startTimes.delete(elementInstanceKey);
                  });
      default -> {
        // other lifecycle intents do not bound an element's execution time
      }
    }
  }
}
