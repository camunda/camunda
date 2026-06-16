/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.waitstate.transformers;

import static io.camunda.zeebe.exporter.common.waitstate.WaitStateConfigs.SIGNAL_CONFIG;

import io.camunda.zeebe.exporter.common.waitstate.WaitStateEntry;
import io.camunda.zeebe.exporter.common.waitstate.WaitStateTransformer;
import io.camunda.zeebe.exporter.common.waitstate.WaitStateTransformerConfig;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.SignalSubscriptionRecordValue;

public class SignalBasedWaitStateTransformer
    implements WaitStateTransformer<SignalSubscriptionRecordValue> {

  @Override
  public WaitStateTransformerConfig config() {
    return SIGNAL_CONFIG;
  }

  @Override
  public void extract(
      final Record<SignalSubscriptionRecordValue> record, final WaitStateEntry entry) {
    entry
        .setElementType(BpmnElementType.INTERMEDIATE_CATCH_EVENT)
        .setDetails(new SignalWaitStateDetails(record.getValue().getSignalName()));
  }

  @Override
  public boolean triggersAdd(final Record<SignalSubscriptionRecordValue> record) {
    return config().triggersAdd(record) && isInstanceSubscription(record);
  }

  @Override
  public boolean triggersUpdate(final Record<SignalSubscriptionRecordValue> record) {
    return config().triggersUpdate(record) && isInstanceSubscription(record);
  }

  @Override
  public boolean triggersRemoval(final Record<SignalSubscriptionRecordValue> record) {
    return config().triggersRemoval(record) && isInstanceSubscription(record);
  }

  /**
   * Returns true only for intermediate catch event subscriptions, skipping start and boundary
   * events.
   */
  private static boolean isInstanceSubscription(
      final Record<SignalSubscriptionRecordValue> record) {
    return BpmnElementType.INTERMEDIATE_CATCH_EVENT
        .name()
        .equals(record.getValue().getBpmnElementType());
  }
}
