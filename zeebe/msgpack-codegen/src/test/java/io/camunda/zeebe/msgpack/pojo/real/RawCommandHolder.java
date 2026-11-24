/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.pojo.real;

import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.msgpack.pojo.annotation.*;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Holds an encoded value of a type that is only known at runtime, see {@link RawCommandValueTest}.
 */
@MsgpackPojo
public final class RawCommandHolder extends MsgpackBufferValue<RawCommandHolder> {
  private static final DirectBuffer EMPTY_MAP = new UnsafeBuffer(new byte[] {(byte) 0x80});

  int intent = -1;

  @Raw DirectBuffer commandValue = EMPTY_MAP;

  final UnknownProperties unknownProperties = new UnknownProperties();

  void setCommandValue(final BufferWriter value) {
    final var buffer = new UnsafeBuffer(new byte[value.getLength()]);
    value.write(buffer, 0);
    commandValue = buffer;
  }

  @Override
  protected MsgpackCodec<RawCommandHolder> codec() {
    return RawCommandHolderCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<RawCommandHolder> ops() {
    return RawCommandHolderOps.INSTANCE;
  }
}
