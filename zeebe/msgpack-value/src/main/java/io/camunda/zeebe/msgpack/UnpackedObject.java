/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack;

import io.camunda.zeebe.msgpack.spec.MsgPackReader;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public class UnpackedObject extends ObjectValue implements Recyclable, BufferReader, BufferWriter {

  private MsgPackReader reader;
  private MsgPackWriter writer;

  /**
   * Creates a new UnpackedObject
   *
   * @param expectedDeclaredProperties a size hint for the number of declared properties. Providing
   *     the correct number helps to avoid allocations and memory copies.
   */
  public UnpackedObject(final int expectedDeclaredProperties) {
    super(expectedDeclaredProperties);
  }

  public void wrap(final DirectBuffer buff) {
    wrap(buff, 0, buff.capacity());
  }

  @Override
  public void wrap(final DirectBuffer buff, final int offset, final int length) {
    reset();
    if (reader == null) {
      reader = new MsgPackReader();
    }
    reader.wrap(buff, offset, length);
    try {
      read(reader);
    } catch (final Exception e) {
      throw new RuntimeException(
          "Could not deserialize object ["
              + getClass().getSimpleName()
              + "]. Deserialization stuck at offset "
              + reader.getOffset()
              + " of length "
              + length,
          e);
    }
  }

  @Override
  public int getLength() {
    return getEncodedLength();
  }

  @Override
  public int write(final MutableDirectBuffer buffer, final int offset) {
    if (writer == null) {
      writer = new MsgPackWriter();
    }
    writer.wrap(buffer, offset);
    return write(writer);
  }

  /**
   * Copies all declared properties from this object into {@code target} using property-level {@link
   * io.camunda.zeebe.msgpack.value.BaseValue#copyFrom} — zero msgpack serialization for value types
   * that override it.
   */
  public void copyTo(final UnpackedObject target) {
    if (!target.getClass().isAssignableFrom(getClass())) {
      throw new IllegalArgumentException(
          "Target class %s is not assignable from this class %s"
              .formatted(target.getClass(), getClass()));
    }
    target.copyPropertiesFrom(this);
  }

  public UnpackedObject createNewInstance() {
    try {
      final var ctor = getClass().getDeclaredConstructor();
      ctor.setAccessible(true);
      return (UnpackedObject) ctor.newInstance();
    } catch (final Exception e) {
      throw new RuntimeException("Failed to create new instance of " + getClass().getName(), e);
    }
  }
}
