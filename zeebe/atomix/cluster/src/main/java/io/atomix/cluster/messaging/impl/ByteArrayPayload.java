/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.cluster.messaging.impl;

import io.atomix.cluster.messaging.ManagedPayload;
import io.netty.buffer.ByteBuf;
import java.util.List;

public class ByteArrayPayload implements ManagedPayload {

  private final byte[] bytes;

  public ByteArrayPayload(final byte[] bytes) {
    this.bytes = bytes == null ? new byte[0] : bytes;
  }

  public byte[] getBytes() {
    return bytes;
  }

  @Override
  public int length() {
    return bytes.length;
  }

  @Override
  public void encode(final ByteBuf buffer, final List<Object> out) {
    if (bytes.length > 0) {
      buffer.writeBytes(bytes);
    }
  }
}
