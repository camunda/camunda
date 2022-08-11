/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.MessageState;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.camunda.zeebe.protocol.record.RecordValueWithTenant;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

public interface MutableMessageState extends MessageState {

  void put(long messageKey, MessageRecord message);

  default void putMessageCorrelation(final long messageKey, final DirectBuffer bpmnProcessId) {
    putMessageCorrelation(
        messageKey, bpmnProcessId, BufferUtil.wrapString(RecordValueWithTenant.DEFAULT_TENANT_ID));
  }

  void putMessageCorrelation(
      long messageKey, DirectBuffer bpmnProcessId, final DirectBuffer tenantId);

  default void removeMessageCorrelation(final long messageKey, final DirectBuffer bpmnProcessId) {
    removeMessageCorrelation(
        messageKey, bpmnProcessId, BufferUtil.wrapString(RecordValueWithTenant.DEFAULT_TENANT_ID));
  }

  void removeMessageCorrelation(
      long messageKey, DirectBuffer bpmnProcessId, final DirectBuffer tenantId);

  default void putActiveProcessInstance(
      final DirectBuffer bpmnProcessId, final DirectBuffer correlationKey) {
    putActiveProcessInstance(
        bpmnProcessId,
        correlationKey,
        BufferUtil.wrapString(RecordValueWithTenant.DEFAULT_TENANT_ID));
  }

  void putActiveProcessInstance(
      DirectBuffer bpmnProcessId, DirectBuffer correlationKey, final DirectBuffer tenantId);

  default void removeActiveProcessInstance(
      final DirectBuffer bpmnProcessId, final DirectBuffer correlationKey) {
    removeActiveProcessInstance(
        bpmnProcessId,
        correlationKey,
        BufferUtil.wrapString(RecordValueWithTenant.DEFAULT_TENANT_ID));
  }

  void removeActiveProcessInstance(
      DirectBuffer bpmnProcessId, DirectBuffer correlationKey, final DirectBuffer tenantId);

  void putProcessInstanceCorrelationKey(long processInstanceKey, DirectBuffer correlationKey);

  void removeProcessInstanceCorrelationKey(long processInstanceKey);

  void remove(long messageKey);
}
