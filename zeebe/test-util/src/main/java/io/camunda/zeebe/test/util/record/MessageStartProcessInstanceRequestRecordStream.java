/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.record;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.MessageStartProcessInstanceRequestRecordValue;
import java.util.stream.Stream;

public final class MessageStartProcessInstanceRequestRecordStream
    extends ExporterRecordStream<
        MessageStartProcessInstanceRequestRecordValue,
        MessageStartProcessInstanceRequestRecordStream> {

  public MessageStartProcessInstanceRequestRecordStream(
      final Stream<Record<MessageStartProcessInstanceRequestRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected MessageStartProcessInstanceRequestRecordStream supply(
      final Stream<Record<MessageStartProcessInstanceRequestRecordValue>> wrappedStream) {
    return new MessageStartProcessInstanceRequestRecordStream(wrappedStream);
  }

  public MessageStartProcessInstanceRequestRecordStream withMessageKey(final long messageKey) {
    return valueFilter(v -> v.getMessageKey() == messageKey);
  }

  public MessageStartProcessInstanceRequestRecordStream withMessageName(final String messageName) {
    return valueFilter(v -> messageName.equals(v.getMessageName()));
  }

  public MessageStartProcessInstanceRequestRecordStream withCorrelationKey(
      final String correlationKey) {
    return valueFilter(v -> correlationKey.equals(v.getCorrelationKey()));
  }

  public MessageStartProcessInstanceRequestRecordStream withBusinessId(final String businessId) {
    return valueFilter(v -> businessId.equals(v.getBusinessId()));
  }

  public MessageStartProcessInstanceRequestRecordStream withProcessDefinitionKey(
      final long processDefinitionKey) {
    return valueFilter(v -> v.getProcessDefinitionKey() == processDefinitionKey);
  }

  public MessageStartProcessInstanceRequestRecordStream withBpmnProcessId(
      final String bpmnProcessId) {
    return valueFilter(v -> bpmnProcessId.equals(v.getBpmnProcessId()));
  }

  public MessageStartProcessInstanceRequestRecordStream withStartEventId(
      final String startEventId) {
    return valueFilter(v -> startEventId.equals(v.getStartEventId()));
  }

  public MessageStartProcessInstanceRequestRecordStream withMessageStartEventSubscriptionKey(
      final long key) {
    return valueFilter(v -> v.getMessageStartEventSubscriptionKey() == key);
  }

  public MessageStartProcessInstanceRequestRecordStream withProcessInstanceKey(
      final long processInstanceKey) {
    return valueFilter(v -> v.getProcessInstanceKey() == processInstanceKey);
  }

  public MessageStartProcessInstanceRequestRecordStream withTenantId(final String tenantId) {
    return valueFilter(v -> tenantId.equals(v.getTenantId()));
  }
}
