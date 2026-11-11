/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.deployment;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackProperty;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

/**
 * Plain data; the msgpack codec is generated, see {@link MsgpackPojo}. The field name is the stored
 * format.
 */
@MsgpackPojo
public final class Digest extends MsgpackBufferValue<Digest> implements DbValue {

  @MsgpackProperty(binary = true)
  DirectBuffer digest = Decoding.EMPTY;

  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public Digest() {}

  public DirectBuffer get() {
    return digest;
  }

  public Digest set(final DirectBuffer digest) {
    this.digest = BufferUtil.cloneBuffer(digest);
    return this;
  }
}
