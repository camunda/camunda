/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.variable;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackProperty;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.RequiredLong;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@MsgpackPojo
@NullMarked
public final class VariableInstance extends MsgpackBufferValue<VariableInstance>
    implements DbValue {

  final RequiredLong key = new RequiredLong();

  @MsgpackProperty(binary = true)
  @Nullable DirectBuffer value;

  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public long getKey() {
    return key.get();
  }

  public VariableInstance setKey(final long key) {
    this.key.set(key);
    return this;
  }

  public VariableInstance setValue(final DirectBuffer value, final int offset, final int length) {
    this.value = new UnsafeBuffer(value, offset, length);
    return this;
  }

  public DirectBuffer getValue() {
    return Decoding.required(value, "value");
  }

  @Override
  protected MsgpackCodec<VariableInstance> codec() {
    return VariableInstanceCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<VariableInstance> ops() {
    return VariableInstanceOps.INSTANCE;
  }
}
