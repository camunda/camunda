/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.MessageState;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import org.agrona.DirectBuffer;

public interface MutableMessageState extends MessageState, StreamProcessorLifecycleAware {

  void put(long messageKey, MessageRecord message);

  void putMessageCorrelation(long messageKey, DirectBuffer bpmnProcessId);

  void removeMessageCorrelation(long messageKey, DirectBuffer bpmnProcessId);

  void putActiveProcessInstance(DirectBuffer bpmnProcessId, DirectBuffer correlationKey);

  void removeActiveProcessInstance(DirectBuffer bpmnProcessId, DirectBuffer correlationKey);

  /**
   * Marks a process-correlation-key lock entry as cross-partition by recording the holder's {@code
   * businessId} in a parallel CF. Written on {@code P_K} when the STARTED reply from {@code P_B} is
   * applied, alongside the regular {@link #putActiveProcessInstance(DirectBuffer, DirectBuffer)}
   * call. The pull-based release loop introduced in a later increment iterates this CF to find the
   * {@code (processId, businessId)} pairs whose lock entries can be released by querying {@code P_B
   * = hash(businessId)}.
   *
   * <p>Idempotent: a repeated write for the same key with the same businessId is a no-op, which
   * matters because the cross-partition STARTED reply can be re-delivered (the dedup on {@code P_B}
   * re-replies the same processInstanceKey on retry).
   */
  void putCrossPartitionStartLockBusinessId(
      DirectBuffer bpmnProcessId, DirectBuffer correlationKey, DirectBuffer businessId);

  void putProcessInstanceCorrelationKey(long processInstanceKey, DirectBuffer correlationKey);

  void removeProcessInstanceCorrelationKey(long processInstanceKey);

  void remove(long messageKey);
}
