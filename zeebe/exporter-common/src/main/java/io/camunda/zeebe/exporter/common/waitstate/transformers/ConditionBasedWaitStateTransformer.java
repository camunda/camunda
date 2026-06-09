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
import io.camunda.zeebe.protocol.record.value.ConditionalSubscriptionRecordValue;
import java.util.List;

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
    return WaitStateTransformer.super.triggersAdd(r) && r.getValue().getProcessInstanceKey() != -1;
  }

  @Override
  public boolean triggersUpdate(final Record<ConditionalSubscriptionRecordValue> r) {
    return WaitStateTransformer.super.triggersUpdate(r)
        && r.getValue().getProcessInstanceKey() != -1;
  }

  @Override
  public boolean triggersRemoval(final Record<ConditionalSubscriptionRecordValue> r) {
    if (r.getValue().getProcessInstanceKey() == -1) {
      return false;
    }
    // DELETED always removes (covered by config); TRIGGERED removes only when interrupting
    return WaitStateTransformer.super.triggersRemoval(r)
        || (r.getIntent() == ConditionalSubscriptionIntent.TRIGGERED
            && r.getValue().isInterrupting());
  }
}
