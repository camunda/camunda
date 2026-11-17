/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.conditional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.record.value.ConditionalStartedProcessInstanceValue;
import org.jspecify.annotations.NullMarked;

@JsonIgnoreProperties({
  /* encodedLength and empty were inherited from ObjectValue, length is inherited from
  MsgpackBufferValue; they have no purpose in exported JSON records*/
  "encodedLength",
  "empty",
  "length"
})
@MsgpackPojo
@NullMarked
public final class ConditionalStartedProcessInstance
    extends MsgpackBufferValue<ConditionalStartedProcessInstance>
    implements ConditionalStartedProcessInstanceValue {

  long processDefinitionKey = -1L;
  long processInstanceKey = -1L;
  int storageOrdinal = 0;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public ConditionalStartedProcessInstance setProcessDefinitionKey(
      final long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public ConditionalStartedProcessInstance setProcessInstanceKey(final long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  @Override
  public int getStorageOrdinal() {
    return storageOrdinal;
  }

  public ConditionalStartedProcessInstance setStorageOrdinal(final int storageOrdinal) {
    this.storageOrdinal = storageOrdinal;
    return this;
  }

  /**
   * Copies the values from another ConditionalStartedProcessInstance.
   *
   * @param other the record to copy from
   */
  public void copy(final ConditionalStartedProcessInstance other) {
    setProcessDefinitionKey(other.getProcessDefinitionKey());
    setProcessInstanceKey(other.getProcessInstanceKey());
    setStorageOrdinal(other.getStorageOrdinal());
  }

  @Override
  protected MsgpackCodec<ConditionalStartedProcessInstance> codec() {
    return ConditionalStartedProcessInstanceCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<ConditionalStartedProcessInstance> ops() {
    return ConditionalStartedProcessInstanceOps.INSTANCE;
  }
}
