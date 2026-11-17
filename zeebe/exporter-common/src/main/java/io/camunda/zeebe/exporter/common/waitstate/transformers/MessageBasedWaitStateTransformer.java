/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.waitstate.transformers;

import static io.camunda.zeebe.exporter.common.waitstate.WaitStateConfigs.MESSAGE_CONFIG;

import io.camunda.zeebe.exporter.common.waitstate.WaitStateEntry;
import io.camunda.zeebe.exporter.common.waitstate.WaitStateTransformer;
import io.camunda.zeebe.exporter.common.waitstate.WaitStateTransformerConfig;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.MessageSubscriptionRecordValue;

public class MessageBasedWaitStateTransformer
    implements WaitStateTransformer<MessageSubscriptionRecordValue> {

  @Override
  public WaitStateTransformerConfig config() {
    return MESSAGE_CONFIG;
  }

  @Override
  public void extract(
      final Record<MessageSubscriptionRecordValue> record, final WaitStateEntry entry) {
    final MessageSubscriptionRecordValue value = record.getValue();
    entry
        .setElementType(value.getElementType())
        .setDetails(new MessageWaitStateDetails(value.getMessageName(), value.getCorrelationKey()));
  }

  @Override
  public boolean triggersAdd(final Record<MessageSubscriptionRecordValue> record) {
    return WaitStateTransformer.super.triggersAdd(record) && isSupportedElementType(record);
  }

  @Override
  public boolean triggersUpdate(final Record<MessageSubscriptionRecordValue> record) {
    return WaitStateTransformer.super.triggersUpdate(record) && isSupportedElementType(record);
  }

  @Override
  public boolean triggersRemoval(final Record<MessageSubscriptionRecordValue> record) {
    return WaitStateTransformer.super.triggersRemoval(record) && isSupportedElementType(record);
  }

  private static boolean isSupportedElementType(
      final Record<MessageSubscriptionRecordValue> record) {
    final BpmnElementType elementType = record.getValue().getElementType();
    return elementType == BpmnElementType.RECEIVE_TASK
        || elementType == BpmnElementType.INTERMEDIATE_CATCH_EVENT;
  }
}
