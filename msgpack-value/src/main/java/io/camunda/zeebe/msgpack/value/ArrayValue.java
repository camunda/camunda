/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.msgpack.value;

import io.zeebe.msgpack.spec.MsgPackReader;
import io.zeebe.msgpack.spec.MsgPackWriter;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import org.agrona.ExpandableArrayBuffer;

public final class ArrayValue<T extends BaseValue> extends BaseValue
    implements Iterator<T>, Iterable<T> {
  private final MsgPackWriter writer = new MsgPackWriter();
  private final MsgPackReader reader = new MsgPackReader();

  // buffer
  private final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();
  // inner value
  private final T innerValue;
  private int elementCount;
  private int bufferLength;
  private int oldInnerValueLength;
  private InnerValueState innerValueState;

  // iterator
  private int cursorOffset;
  private int cursorIndex;

  public ArrayValue(final T innerValue) {
    this.innerValue = innerValue;
    reset();
  }

  @Override
  public void reset() {
    elementCount = 0;
    bufferLength = 0;

    resetIterator();
    resetInnerValue();
  }

  private void resetIterator() {
    cursorIndex = 0;
    cursorOffset = 0;
  }

  private void resetInnerValue() {
    innerValue.reset();
    oldInnerValueLength = 0;

    innerValueState = InnerValueState.Uninitialized;
  }

  @Override
  public void writeJSON(final StringBuilder builder) {
    flushAndResetInnerValue();

    builder.append("[");

    boolean firstElement = true;

    for (final T element : this) {
      if (!firstElement) {
        builder.append(",");
      } else {
        firstElement = false;
      }

      element.writeJSON(builder);
    }

    builder.append("]");
  }

  @Override
  public void write(final MsgPackWriter writer) {
    flushAndResetInnerValue();

    writer.writeArrayHeader(elementCount);
    writer.writeRaw(buffer, 0, bufferLength);
  }

  @Override
  public void read(final MsgPackReader reader) {
    reset();

    elementCount = reader.readArrayHeader();

    writer.wrap(buffer, 0);

    for (int i = 0; i < elementCount; i++) {
      innerValue.read(reader);
      innerValue.write(writer);
    }

    resetInnerValue();

    bufferLength = writer.getOffset();
  }

  @Override
  public int getEncodedLength() {
    flushAndResetInnerValue();
    return MsgPackWriter.getEncodedArrayHeaderLenght(elementCount) + bufferLength;
  }

  @Override
  public Iterator<T> iterator() {
    flushAndResetInnerValue();

    resetIterator();
    resetInnerValue();

    return this;
  }

  @Override
  public boolean hasNext() {
    return cursorIndex < elementCount;
  }

  @Override
  public T next() {
    if (!hasNext()) {
      throw new NoSuchElementException("No more elements left");
    }

    final int innerValueLength = getInnerValueLength();

    flushAndResetInnerValue();

    cursorIndex += 1;
    cursorOffset += innerValueLength;

    readInnerValue();

    return innerValue;
  }

  @Override
  public void remove() {
    if (innerValueState != InnerValueState.Modify) {
      throw new IllegalStateException("No element available to remove, call next() before");
    }

    elementCount -= 1;
    cursorIndex -= 1;

    moveValuesLeft(cursorOffset + oldInnerValueLength, oldInnerValueLength);

    innerValueState = InnerValueState.Uninitialized;
  }

  public T add() {
    final boolean elementUpdated = innerValueState == InnerValueState.Modify;
    final int innerValueLength = getInnerValueLength();

    flushAndResetInnerValue();

    elementCount += 1;

    if (elementUpdated) {
      // if the previous element was return by iterator the new element should be added after it
      cursorOffset += innerValueLength;
      cursorIndex += 1;
    }

    innerValueState = InnerValueState.Insert;

    return innerValue;
  }

  @Override
  public int hashCode() {
    return Objects.hash(buffer, elementCount, bufferLength);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof ArrayValue)) {
      return false;
    }

    final ArrayValue<?> that = (ArrayValue<?>) o;
    return elementCount == that.elementCount
        && bufferLength == that.bufferLength
        && Objects.equals(buffer, that.buffer);
  }

  private int getInnerValueLength() {
    switch (innerValueState) {
      case Insert:
      case Modify:
        return innerValue.getEncodedLength();
      case Uninitialized:
      default:
        return 0;
    }
  }

  private void readInnerValue() {
    reader.wrap(buffer, cursorOffset, bufferLength - cursorOffset);

    innerValueState = InnerValueState.Modify;
    innerValue.read(reader);
    oldInnerValueLength = innerValue.getEncodedLength();
  }

  private void flushAndResetInnerValue() {
    switch (innerValueState) {
      case Insert:
        insertInnerValue();
        break;
      case Modify:
        updateInnerValue();
        break;
      case Uninitialized:
      default:
        break;
    }

    resetInnerValue();
  }

  private void insertInnerValue() {
    final int innerValueLength = innerValue.getEncodedLength();
    moveValuesRight(cursorOffset, innerValueLength);

    writeInnerValue();

    cursorOffset += innerValueLength;
    cursorIndex += 1;
  }

  private void updateInnerValue() {
    final int innerValueLength = innerValue.getEncodedLength();

    if (oldInnerValueLength < innerValueLength) {
      // the inner value length increased
      // move bytes back to have space for updated value
      final int difference = innerValueLength - oldInnerValueLength;
      moveValuesRight(cursorOffset + oldInnerValueLength, difference);
    } else if (oldInnerValueLength > innerValueLength) {
      // the inner value length decreased
      // move bytes to front to fill gap for smaller updated value
      final int difference = oldInnerValueLength - innerValueLength;
      moveValuesLeft(cursorOffset + oldInnerValueLength, difference);
    }

    writeInnerValue();
  }

  private void writeInnerValue() {
    writer.wrap(buffer, cursorOffset);
    innerValue.write(writer);
  }

  private void moveValuesLeft(final int srcOffset, final int removedLength) {
    if (srcOffset <= bufferLength) {
      final int targetOffset = srcOffset - removedLength;
      final int copyLength = bufferLength - srcOffset;
      buffer.putBytes(targetOffset, buffer, srcOffset, copyLength);
    }

    bufferLength -= removedLength;
  }

  private void moveValuesRight(final int srcOffset, final int requiredLength) {
    if (srcOffset < bufferLength) {
      final int targetOffset = srcOffset + requiredLength;
      final int copyLength = bufferLength - srcOffset;
      buffer.putBytes(targetOffset, buffer, srcOffset, copyLength);
    }

    bufferLength += requiredLength;
  }

  enum InnerValueState {
    Uninitialized,
    Insert,
    Modify,
  }
}
