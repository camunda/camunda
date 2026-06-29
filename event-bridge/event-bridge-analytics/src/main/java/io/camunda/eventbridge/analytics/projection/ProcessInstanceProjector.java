/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.projection;

import io.camunda.eventbridge.analytics.fact.ProcessInstanceExecutionTimeFact;
import io.camunda.eventbridge.zeebe.connector.ZeebeRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import java.util.Optional;

/**
 * Stage 1 — the base-projection fold. Reads the consumed record stream and maintains one {@link
 * ProcessInstanceProjection} per process instance. When an instance's root process element reaches
 * {@code ELEMENT_COMPLETED}/{@code ELEMENT_TERMINATED} and a start time is known, it derives the
 * execution-time fact exactly once.
 *
 * <p>The fold is a deterministic function of the (in-order, per-partition) source stream — every
 * replica that applies the same records reaches the same state and derives the same facts. It only
 * reads the source and updates its own state; it never writes back to the consumed stream.
 */
public final class ProcessInstanceProjector {

  private final BaseProjectionStore store;

  public ProcessInstanceProjector(final BaseProjectionStore store) {
    this.store = store;
  }

  /**
   * Folds one record into the projection, returning the derived fact if this record completed an
   * instance for the first time.
   */
  public Optional<ProcessInstanceExecutionTimeFact> apply(final ZeebeRecord zeebeRecord) {
    final Record<?> record = zeebeRecord.record();
    if (record.getValueType() != ValueType.PROCESS_INSTANCE
        || !(record.getValue() instanceof final ProcessInstanceRecordValue value)
        || value.getBpmnElementType() != BpmnElementType.PROCESS
        || !(record.getIntent() instanceof final ProcessInstanceIntent intent)) {
      return Optional.empty();
    }

    final long key = value.getProcessInstanceKey();
    final ProcessInstanceProjection previous = store.get(key).orElse(null);
    long startTime = previous != null ? previous.startTime() : ProcessInstanceProjection.UNSET;
    long endTime = previous != null ? previous.endTime() : ProcessInstanceProjection.UNSET;
    boolean terminated = previous != null && previous.terminated();
    boolean factEmitted = previous != null && previous.factEmitted();

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
        store.setConsumedPosition(zeebeRecord.offset());
        return Optional.empty();
      }
    }

    Optional<ProcessInstanceExecutionTimeFact> fact = Optional.empty();
    if (startTime != ProcessInstanceProjection.UNSET
        && endTime != ProcessInstanceProjection.UNSET
        && !factEmitted) {
      fact =
          Optional.of(
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
                  zeebeRecord.offset()));
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
            factEmitted));
    store.setConsumedPosition(zeebeRecord.offset());
    return fact;
  }
}
