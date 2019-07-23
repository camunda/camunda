/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.transport.impl;

import java.nio.ByteOrder;
import org.agrona.BitUtil;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class TransportHeaderDescriptor {
  public static final short REQUEST_RESPONSE = 0;
  public static final short FULL_DUPLEX_SINGLE_MESSAGE = 1;
  public static final short CONTROL_MESSAGE = 2;

  public static final int PROTOCOL_ID_OFFSET;
  public static final int HEADER_LENGTH;

  // do not change; must be stable for backwards/forwards compatibility
  public static final ByteOrder HEADER_BYTE_ORDER = ByteOrder.LITTLE_ENDIAN;

  static {
    int offset = 0;

    PROTOCOL_ID_OFFSET = offset;
    offset += BitUtil.SIZE_OF_SHORT;

    HEADER_LENGTH = offset;
  }

  protected final UnsafeBuffer buffer = new UnsafeBuffer(0, 0);

  public static int framedLength(int messageLength) {
    return HEADER_LENGTH + messageLength;
  }

  public static int headerLength() {
    return HEADER_LENGTH;
  }

  public static int protocolIdOffset(int offset) {
    return offset + PROTOCOL_ID_OFFSET;
  }

  public TransportHeaderDescriptor wrap(DirectBuffer buffer, int offset) {
    this.buffer.wrap(buffer, offset, HEADER_LENGTH);
    return this;
  }

  public TransportHeaderDescriptor protocolId(short protocolId) {
    buffer.putShort(PROTOCOL_ID_OFFSET, protocolId, HEADER_BYTE_ORDER);
    return this;
  }

  public TransportHeaderDescriptor putProtocolSingleMessage() {
    return protocolId(FULL_DUPLEX_SINGLE_MESSAGE);
  }

  public TransportHeaderDescriptor putProtocolRequestReponse() {
    return protocolId(REQUEST_RESPONSE);
  }

  public int protocolId() {
    return buffer.getShort(PROTOCOL_ID_OFFSET, HEADER_BYTE_ORDER);
  }
}
