/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.multiinstance;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.List;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class PersistedInputCollection extends MsgpackBufferValue<PersistedInputCollection>
    implements DbValue {

  final List<DirectBuffer> inputCollection = new ArrayList<>();
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public List<DirectBuffer> getInputCollection() {
    return inputCollection.stream().map(BufferUtil::cloneBuffer).toList();
  }

  public PersistedInputCollection setInputCollection(final List<DirectBuffer> inputCollection) {
    this.inputCollection.clear();
    inputCollection.forEach(element -> this.inputCollection.add(BufferUtil.cloneBuffer(element)));
    return this;
  }

  @Override
  protected MsgpackCodec<PersistedInputCollection> codec() {
    return PersistedInputCollectionCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<PersistedInputCollection> ops() {
    return PersistedInputCollectionOps.INSTANCE;
  }
}
