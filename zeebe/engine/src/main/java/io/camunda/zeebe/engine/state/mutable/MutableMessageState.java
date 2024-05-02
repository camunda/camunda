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

  void putProcessInstanceCorrelationKey(long processInstanceKey, DirectBuffer correlationKey);

  void removeProcessInstanceCorrelationKey(long processInstanceKey);

  void remove(long messageKey);
}
