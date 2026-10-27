/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.waitstate.transformers;

import static io.camunda.zeebe.exporter.common.waitstate.WaitStateConfigs.TIMER_CONFIG;

import io.camunda.zeebe.exporter.common.waitstate.WaitStateEntry;
import io.camunda.zeebe.exporter.common.waitstate.WaitStateTransformer;
import io.camunda.zeebe.exporter.common.waitstate.WaitStateTransformerConfig;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.TimerRecordValue;

/**
 * Transforms timer records into wait-state entries. Only intermediate catch event timers are
 * exported. Records are skipped when:
 *
 * <ul>
 *   <li>processInstanceKey is -1 (timer start event subscriptions — no process instance context)
 *   <li>rootProcessInstanceKey is -1 or bpmnProcessId is empty (pre-8.10 V1 records written before
 *       these fields existed — exporting partial data would be misleading)
 *   <li>elementType is not INTERMEDIATE_CATCH_EVENT (boundary events are not tracked as wait states
 *       because they represent interrupts, not explicit waits)
 * </ul>
 */
public class TimerBasedWaitStateTransformer implements WaitStateTransformer<TimerRecordValue> {

  @Override
  public WaitStateTransformerConfig config() {
    return TIMER_CONFIG;
  }

  @Override
  public void extract(final Record<TimerRecordValue> record, final WaitStateEntry entry) {
    final TimerRecordValue value = record.getValue();
    entry
        .setElementType(value.getElementType())
        .setDetails(new TimerWaitStateDetails(value.getDueDate(), value.getRepetitions()));
  }

  @Override
  public boolean triggersAdd(final Record<TimerRecordValue> record) {
    return isProcessInstanceTimer(record) && WaitStateTransformer.super.triggersAdd(record);
  }

  @Override
  public boolean triggersUpdate(final Record<TimerRecordValue> record) {
    return isProcessInstanceTimer(record) && WaitStateTransformer.super.triggersUpdate(record);
  }

  @Override
  public boolean triggersRemoval(final Record<TimerRecordValue> record) {
    return isProcessInstanceTimer(record) && WaitStateTransformer.super.triggersRemoval(record);
  }

  private static boolean isProcessInstanceTimer(final Record<TimerRecordValue> record) {
    final TimerRecordValue value = record.getValue();
    return value.getProcessInstanceKey() != -1L
        && value.getRootProcessInstanceKey() != -1L
        && !value.getBpmnProcessId().isEmpty()
        && value.getElementType() == BpmnElementType.INTERMEDIATE_CATCH_EVENT;
  }
}
