/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.state;

import io.camunda.zeebe.db.DbValue;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/** Raw commit entry JSON bytes. */
final class OutboxDbValue implements DbValue {

  private byte[] bytes = new byte[0];

  void set(final byte[] bytes) {
    this.bytes = bytes;
  }

  byte[] bytes() {
    return bytes;
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

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    bytes = new byte[length];
    buffer.getBytes(offset, bytes);
  }
}
