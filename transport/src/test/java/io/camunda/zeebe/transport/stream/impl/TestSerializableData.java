/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.transport.stream.impl;

import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

class TestSerializableData implements BufferReader, BufferWriter {

  private int data;

  public TestSerializableData() {}

  public TestSerializableData(final int data) {
    this.data = data;
  }

  public TestSerializableData data(final int data) {
    this.data = data;
    return this;
  }

  public int data() {
    return data;
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    data = buffer.getInt(0);
  }

  @Override
  public int getLength() {
    return Integer.BYTES;
  }

  @Override
  public void write(final MutableDirectBuffer buffer, final int offset) {
    buffer.putInt(offset, data);
  }
}
