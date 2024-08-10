/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.impl.serializer;

import static org.agrona.BitUtil.align;

import org.agrona.MutableDirectBuffer;

/**
 * Remnants of the dispatcher data framing. A 12 byte header starting with the frame length. The
 * rest of the 12 bytes are unused. This is kept for backwards-compatibility reasons because {@link
 * io.camunda.zeebe.logstreams.log.LoggedEvent} still expects this header.
 */
public final class DataFrameDescriptor {
  public static final int FRAME_LENGTH_OFFSET = 0;

  public static final int FRAME_ALIGNMENT = 8;

  public static final int HEADER_LENGTH = 12;

  public static void setFramedLength(
      final MutableDirectBuffer buffer, final int offset, final int length) {
    buffer.putInt(offset + FRAME_LENGTH_OFFSET, length);
  }

  public static int alignedLength(final int msgLength) {
    return align(msgLength, FRAME_ALIGNMENT);
  }

  public static int framedLength(final int msgLength) {
    return msgLength + HEADER_LENGTH;
  }

  public static int lengthOffset(final int offset) {
    return offset + FRAME_LENGTH_OFFSET;
  }

  public static int messageOffset(final int offset) {
    return offset + HEADER_LENGTH;
  }

  public static int messageLength(final int framedLength) {
    return framedLength - HEADER_LENGTH;
  }
}
