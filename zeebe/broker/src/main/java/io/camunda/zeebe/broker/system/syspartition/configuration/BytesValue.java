/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition.configuration;

import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/** An opaque value of the system partition's key-value store, held as a copy of its bytes. */
final class BytesValue implements BufferReader, BufferWriter {
  private byte[] bytes;

  BytesValue() {
    this(new byte[0]);
  }

  BytesValue(final byte[] bytes) {
    this.bytes = bytes;
  }

  byte[] bytes() {
    return bytes;
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    bytes = new byte[length];
    buffer.getBytes(offset, bytes);
  }

  @Override
  public int getLength() {
    return bytes.length;
  }

  @Override
  public int write(final MutableDirectBuffer buffer, final int offset) {
    buffer.putBytes(offset, bytes);
    return bytes.length;
  }
}
