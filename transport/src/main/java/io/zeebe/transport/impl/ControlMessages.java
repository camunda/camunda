/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.transport.impl;

import io.zeebe.dispatcher.impl.log.DataFrameDescriptor;
import java.nio.ByteOrder;
import org.agrona.BitUtil;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class ControlMessages {
  // do not change; must be stable for backwards/forwards compatibility
  public static final ByteOrder CONTROL_MESSAGE_BYTEORDER =
      TransportHeaderDescriptor.HEADER_BYTE_ORDER;

  public static final int KEEP_ALIVE_TYPE = 0;

  public static final DirectBuffer KEEP_ALIVE;

  static {
    final TransportHeaderDescriptor transportHeaderDescriptor = new TransportHeaderDescriptor();

    final int messageLength = BitUtil.SIZE_OF_INT;
    final int transportFramedLength = TransportHeaderDescriptor.framedLength(messageLength);

    final UnsafeBuffer buf =
        new UnsafeBuffer(new byte[DataFrameDescriptor.alignedFramedLength(transportFramedLength)]);
    final int dataFrameHeaderOffset = 0;
    final int transportHeaderOffset = dataFrameHeaderOffset + DataFrameDescriptor.HEADER_LENGTH;
    final int messageOffset = transportHeaderOffset + TransportHeaderDescriptor.HEADER_LENGTH;

    buf.putInt(
        DataFrameDescriptor.lengthOffset(dataFrameHeaderOffset),
        DataFrameDescriptor.framedLength(transportFramedLength));
    buf.putShort(
        DataFrameDescriptor.typeOffset(dataFrameHeaderOffset), DataFrameDescriptor.TYPE_MESSAGE);

    transportHeaderDescriptor
        .wrap(buf, transportHeaderOffset)
        .protocolId(TransportHeaderDescriptor.CONTROL_MESSAGE);

    buf.putInt(messageOffset, KEEP_ALIVE_TYPE, CONTROL_MESSAGE_BYTEORDER);
    KEEP_ALIVE = buf;
  }
}
