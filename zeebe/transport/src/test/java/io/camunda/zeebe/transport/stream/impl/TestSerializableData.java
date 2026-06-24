/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.transport.stream.impl;

import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.Objects;
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
  public int write(final MutableDirectBuffer buffer, final int offset) {
    buffer.putInt(offset, data);
    return Integer.BYTES;
  }

  @Override
  public int hashCode() {
    return Objects.hash(data);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final TestSerializableData data1 = (TestSerializableData) o;
    return data == data1.data;
  }
}
