/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.msgpack;

import io.zeebe.msgpack.spec.MsgPackReader;
import io.zeebe.msgpack.spec.MsgPackWriter;
import io.zeebe.msgpack.value.ObjectValue;
import io.zeebe.util.buffer.BufferReader;
import io.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public class UnpackedObject extends ObjectValue implements Recyclable, BufferReader, BufferWriter {

  protected final MsgPackReader reader = new MsgPackReader();
  protected final MsgPackWriter writer = new MsgPackWriter();

  public void wrap(final DirectBuffer buff) {
    wrap(buff, 0, buff.capacity());
  }

  @Override
  public void wrap(final DirectBuffer buff, final int offset, final int length) {
    reader.wrap(buff, offset, length);
    try {
      read(reader);
    } catch (final Exception e) {
      throw new RuntimeException(
          "Could not deserialize object. Deserialization stuck at offset "
              + reader.getOffset()
              + " of length "
              + length,
          e);
    }
  }

  @Override
  public int getLength() {
    return getEncodedLength();
  }

  @Override
  public void write(final MutableDirectBuffer buffer, final int offset) {
    writer.wrap(buffer, offset);
    write(writer);
  }
}
