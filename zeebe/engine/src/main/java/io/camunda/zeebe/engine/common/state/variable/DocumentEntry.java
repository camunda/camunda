/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.common.state.variable;

import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class DocumentEntry {

  private final DirectBuffer name = new UnsafeBuffer();
  private final DirectBuffer value = new UnsafeBuffer();

  DocumentEntry() {}

  public DocumentEntry(final DirectBuffer name, final DirectBuffer value) {
    this.name.wrap(name);
    this.value.wrap(value);
  }

  void wrap(
      final DirectBuffer buffer,
      final int nameOffset,
      final int nameLength,
      final int valueOffset,
      final int valueLength) {
    name.wrap(buffer, nameOffset, nameLength);
    value.wrap(buffer, valueOffset, valueLength);
  }

  public DirectBuffer getName() {
    return name;
  }

  public DirectBuffer getValue() {
    return value;
  }

  @Override
  public int hashCode() {
    int result = getName().hashCode();
    result = 31 * result + getValue().hashCode();
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final DocumentEntry that = (DocumentEntry) o;

    if (!getName().equals(that.getName())) {
      return false;
    }
    return getValue().equals(that.getValue());
  }

  @Override
  public String toString() {
    return "DocumentEntry{"
        + "name="
        + BufferUtil.bufferAsString(name)
        + ", value="
        + BufferUtil.bufferAsHexString(value)
        + '}';
  }
}
