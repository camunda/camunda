/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.waitstate.transformers;

import static io.camunda.zeebe.exporter.common.waitstate.WaitStateConfigs.CONDITION_CONFIG;

import io.camunda.zeebe.exporter.common.waitstate.WaitStateEntry;
import io.camunda.zeebe.exporter.common.waitstate.WaitStateTransformer;
import io.camunda.zeebe.exporter.common.waitstate.WaitStateTransformerConfig;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ConditionalSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ConditionalSubscriptionRecordValue;
import java.util.List;

/**
 * Transforms conditional-subscription records into wait-state entries. Only intermediate catch
 * event conditions are exported. Records are skipped when:
 *
 * <ul>
 *   <li>processInstanceKey is -1 (root conditional start event — no process instance context)
 *   <li>rootProcessInstanceKey is -1 or bpmnProcessId is empty (pre-8.10 V1 records without these
 *       fields — exporting partial data would be misleading)
 *   <li>elementType is not INTERMEDIATE_CATCH_EVENT (boundary events and event-subprocess start
 *       events are not tracked as wait states)
 * </ul>
 */
public class ConditionBasedWaitStateTransformer
    implements WaitStateTransformer<ConditionalSubscriptionRecordValue> {

  @Override
  public WaitStateTransformerConfig config() {
    return CONDITION_CONFIG;
  }

  @Override
  public void extract(
      final Record<ConditionalSubscriptionRecordValue> record, final WaitStateEntry entry) {
    final var value = record.getValue();
    // empty variableEvents means "all events" in the engine — normalize to explicit list
    final var events =
        value.getVariableEvents().isEmpty()
            ? List.of("create", "update")
            : value.getVariableEvents();
    entry
        .setElementType(value.getElementType())
        .setDetails(new ConditionWaitStateDetails(value.getCondition(), events));
  }

  @Override
  public boolean triggersAdd(final Record<ConditionalSubscriptionRecordValue> r) {
    return isIntermediateCatchEventCondition(r) && WaitStateTransformer.super.triggersAdd(r);
  }

  @Override
  public boolean triggersUpdate(final Record<ConditionalSubscriptionRecordValue> r) {
    return isIntermediateCatchEventCondition(r) && WaitStateTransformer.super.triggersUpdate(r);
  }

  @Override
  public boolean triggersRemoval(final Record<ConditionalSubscriptionRecordValue> r) {
    if (!isIntermediateCatchEventCondition(r)) {
      return false;
    }
    // DELETED always removes (covered by config); TRIGGERED removes only when interrupting
    return WaitStateTransformer.super.triggersRemoval(r)
        || (r.getIntent() == ConditionalSubscriptionIntent.TRIGGERED
            && r.getValue().isInterrupting());
  }

  private static boolean isIntermediateCatchEventCondition(
      final Record<ConditionalSubscriptionRecordValue> record) {
    final var value = record.getValue();
    return value.getProcessInstanceKey() != -1L
        && value.getRootProcessInstanceKey() != -1L
        && !value.getBpmnProcessId().isEmpty()
        && value.getElementType() == BpmnElementType.INTERMEDIATE_CATCH_EVENT;
  }
}
