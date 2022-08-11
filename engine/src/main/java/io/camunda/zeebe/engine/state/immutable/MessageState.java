/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.engine.state.message.StoredMessage;
import io.camunda.zeebe.protocol.record.RecordValueWithTenant;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

public interface MessageState {

  default boolean existMessageCorrelation(final long messageKey, final DirectBuffer bpmnProcessId) {
    return existMessageCorrelation(
        messageKey, bpmnProcessId, BufferUtil.wrapString(RecordValueWithTenant.DEFAULT_TENANT_ID));
  }

  boolean existMessageCorrelation(
      long messageKey, DirectBuffer bpmnProcessId, final DirectBuffer tenantId);

  default boolean existActiveProcessInstance(
      final DirectBuffer bpmnProcessId, final DirectBuffer correlationKey) {
    return existActiveProcessInstance(
        bpmnProcessId,
        correlationKey,
        BufferUtil.wrapString(RecordValueWithTenant.DEFAULT_TENANT_ID));
  }

  boolean existActiveProcessInstance(
      DirectBuffer bpmnProcessId, DirectBuffer correlationKey, final DirectBuffer tenantId);

  DirectBuffer getProcessInstanceCorrelationKey(long processInstanceKey);

  void visitMessages(
      DirectBuffer name,
      DirectBuffer correlationKey,
      DirectBuffer tenantId,
      MessageVisitor visitor);

  default void visitMessages(
      final DirectBuffer name, final DirectBuffer correlationKey, final MessageVisitor visitor) {
    visitMessages(
        name,
        correlationKey,
        BufferUtil.wrapString(RecordValueWithTenant.DEFAULT_TENANT_ID),
        visitor);
  }

  StoredMessage getMessage(long messageKey);

  void visitMessagesWithDeadlineBefore(long timestamp, MessageVisitor visitor);

  default boolean exist(
      final DirectBuffer name, final DirectBuffer correlationKey, final DirectBuffer messageId) {
    return exist(
        name,
        correlationKey,
        messageId,
        BufferUtil.wrapString(RecordValueWithTenant.DEFAULT_TENANT_ID));
  }

  boolean exist(
      DirectBuffer name,
      DirectBuffer correlationKey,
      DirectBuffer messageId,
      final DirectBuffer tenantId);

  @FunctionalInterface
  interface MessageVisitor {
    boolean visit(StoredMessage message);
  }
}
