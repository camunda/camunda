/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.record.value.RuntimeInstructionType;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class RuntimeInstructionValue extends MsgpackBufferValue<RuntimeInstructionValue>
    implements DbValue {

  DirectBuffer afterElementId = Decoding.EMPTY;
  RuntimeInstructionType type = RuntimeInstructionType.TERMINATE_PROCESS_INSTANCE;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public String getAfterElementId() {
    return BufferUtil.bufferAsString(afterElementId);
  }

  public void setAfterElementId(final String afterElementId) {
    this.afterElementId = BufferUtil.wrapString(afterElementId);
  }

  public RuntimeInstructionType getType() {
    return type;
  }

  public void setType(final RuntimeInstructionType type) {
    this.type = type;
  }

  @Override
  protected MsgpackCodec<RuntimeInstructionValue> codec() {
    return RuntimeInstructionValueCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<RuntimeInstructionValue> ops() {
    return RuntimeInstructionValueOps.INSTANCE;
  }
}
