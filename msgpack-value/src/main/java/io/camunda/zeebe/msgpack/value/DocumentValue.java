/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.msgpack.value;

import io.zeebe.msgpack.spec.MsgPackCodes;
import io.zeebe.msgpack.spec.MsgPackFormat;
import io.zeebe.msgpack.spec.MsgPackHelper;
import io.zeebe.msgpack.spec.MsgPackType;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class DocumentValue extends BinaryValue {
  public static final DirectBuffer EMPTY_DOCUMENT = new UnsafeBuffer(MsgPackHelper.EMTPY_OBJECT);

  public DocumentValue() {}

  public DocumentValue(final DirectBuffer initialValue, final int offset, final int length) {
    super(initialValue, offset, length);
  }

  @Override
  public void wrap(DirectBuffer buff, int offset, int length) {
    final boolean documentIsNil =
        length == 0 || (length == 1 && buff.getByte(offset) == MsgPackCodes.NIL);

    if (documentIsNil) {
      buff = EMPTY_DOCUMENT;
      offset = 0;
      length = EMPTY_DOCUMENT.capacity();
    }

    final byte firstByte = buff.getByte(offset);
    final MsgPackFormat format = MsgPackFormat.valueOf(firstByte);
    final boolean isValid = format.getType() == MsgPackType.MAP;

    if (!isValid) {
      throw new IllegalArgumentException(
          String.format(
              "Expected document to be a root level object, but was '%s'",
              format.getType().name()));
    }

    super.wrap(buff, offset, length);
  }
}
