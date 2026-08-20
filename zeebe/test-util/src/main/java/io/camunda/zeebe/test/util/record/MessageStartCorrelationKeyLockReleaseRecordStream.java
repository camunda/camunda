/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.record;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.MessageStartCorrelationKeyLockReleaseRecordValue;
import java.util.stream.Stream;

public final class MessageStartCorrelationKeyLockReleaseRecordStream
    extends ExporterRecordStream<
        MessageStartCorrelationKeyLockReleaseRecordValue,
        MessageStartCorrelationKeyLockReleaseRecordStream> {

  public MessageStartCorrelationKeyLockReleaseRecordStream(
      final Stream<Record<MessageStartCorrelationKeyLockReleaseRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected MessageStartCorrelationKeyLockReleaseRecordStream supply(
      final Stream<Record<MessageStartCorrelationKeyLockReleaseRecordValue>> wrappedStream) {
    return new MessageStartCorrelationKeyLockReleaseRecordStream(wrappedStream);
  }

  public MessageStartCorrelationKeyLockReleaseRecordStream withRequestKey(final long requestKey) {
    return valueFilter(v -> v.getRequestKey() == requestKey);
  }

  /** Keeps records that carry a holder for the given holder process instance key. */
  public MessageStartCorrelationKeyLockReleaseRecordStream withProcessInstanceKey(
      final long processInstanceKey) {
    return valueFilter(
        v ->
            v.getHolders().stream().anyMatch(h -> h.getProcessInstanceKey() == processInstanceKey));
  }

  /** Keeps records that carry a holder for the given bpmn process id. */
  public MessageStartCorrelationKeyLockReleaseRecordStream withBpmnProcessId(
      final String bpmnProcessId) {
    return valueFilter(
        v -> v.getHolders().stream().anyMatch(h -> bpmnProcessId.equals(h.getBpmnProcessId())));
  }

  /** Keeps records that carry a holder for the given correlation key. */
  public MessageStartCorrelationKeyLockReleaseRecordStream withCorrelationKey(
      final String correlationKey) {
    return valueFilter(
        v -> v.getHolders().stream().anyMatch(h -> correlationKey.equals(h.getCorrelationKey())));
  }

  /** Keeps records that carry a holder for the given tenant id. */
  public MessageStartCorrelationKeyLockReleaseRecordStream withTenantId(final String tenantId) {
    return valueFilter(
        v -> v.getHolders().stream().anyMatch(h -> tenantId.equals(h.getTenantId())));
  }
}
