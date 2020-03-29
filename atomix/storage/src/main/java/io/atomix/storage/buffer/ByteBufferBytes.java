/*
 * Copyright 2015-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.storage.buffer;

import static com.google.common.base.Preconditions.checkNotNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Byte buffer bytes. */
public abstract class ByteBufferBytes extends AbstractBytes {
  protected ByteBuffer buffer;

  protected ByteBufferBytes(final ByteBuffer buffer) {
    this.buffer = buffer;
  }

  public Bytes reset(final ByteBuffer buffer) {
    buffer.clear();
    this.buffer = checkNotNull(buffer, "buffer cannot be null");
    return this;
  }

  /**
   * Allocates a new byte buffer.
   *
   * @param size the buffer size
   * @return a newly allocated byte buffer
   */
  protected abstract ByteBuffer newByteBuffer(int size);

  @Override
  public byte[] array() {
    return buffer.array();
  }

  @Override
  public int size() {
    return buffer.capacity();
  }

  @Override
  public Bytes resize(final int newSize) {
    final ByteBuffer oldBuffer = buffer;
    final ByteBuffer newBuffer = newByteBuffer(newSize);
    oldBuffer.position(0).limit(oldBuffer.capacity());
    newBuffer.position(0).limit(newBuffer.capacity());
    newBuffer.put(oldBuffer);
    newBuffer.clear();
    return reset(newBuffer);
  }

  /**
   * Returns the underlying {@link ByteBuffer}.
   *
   * @return the underlying byte buffer
   */
  public ByteBuffer byteBuffer() {
    return buffer;
  }

  @Override
  public Bytes zero() {
    return this;
  }

  @Override
  public Bytes zero(final int offset) {
    for (int i = index(offset); i < buffer.capacity(); i++) {
      buffer.put(i, (byte) 0);
    }
    return this;
  }

  @Override
  public Bytes zero(final int offset, final int length) {
    for (int i = index(offset); i < offset + length; i++) {
      buffer.put(i, (byte) 0);
    }
    return this;
  }

  @Override
  public Bytes write(final int position, final Bytes bytes, final int offset, final int length) {
    for (int i = 0; i < length; i++) {
      buffer.put((int) position + i, (byte) bytes.readByte(offset + i));
    }
    return this;
  }

  @Override
  public Bytes write(final int position, final byte[] bytes, final int offset, final int length) {
    for (int i = 0; i < length; i++) {
      buffer.put((int) position + i, (byte) bytes[index(offset) + i]);
    }
    return this;
  }

  @Override
  public Bytes write(
      final int offset, final ByteBuffer src, final int srcOffset, final int length) {
    for (int i = 0; i < length; i++) {
      buffer.put(offset + i, src.get(srcOffset + i));
    }
    return this;
  }

  @Override
  public Bytes writeByte(final int offset, final int b) {
    buffer.put(index(offset), (byte) b);
    return this;
  }

  @Override
  public Bytes writeChar(final int offset, final char c) {
    buffer.putChar(index(offset), c);
    return this;
  }

  @Override
  public Bytes writeShort(final int offset, final short s) {
    buffer.putShort(index(offset), s);
    return this;
  }

  @Override
  public Bytes writeInt(final int offset, final int i) {
    buffer.putInt(index(offset), i);
    return this;
  }

  @Override
  public Bytes writeLong(final int offset, final long l) {
    buffer.putLong(index(offset), l);
    return this;
  }

  @Override
  public Bytes writeFloat(final int offset, final float f) {
    buffer.putFloat(index(offset), f);
    return this;
  }

  @Override
  public Bytes writeDouble(final int offset, final double d) {
    buffer.putDouble(index(offset), d);
    return this;
  }

  @Override
  public ByteOrder order() {
    return buffer.order();
  }

  @Override
  public Bytes order(final ByteOrder order) {
    return reset(buffer.order(order));
  }

  /** Returns the index for the given offset. */
  private int index(final int offset) {
    return (int) offset;
  }

  @Override
  public Bytes read(final int position, final Bytes bytes, final int offset, final int length) {
    for (int i = 0; i < length; i++) {
      bytes.writeByte(offset + i, readByte(position + i));
    }
    return this;
  }

  @Override
  public Bytes read(final int position, final byte[] bytes, final int offset, final int length) {
    for (int i = 0; i < length; i++) {
      bytes[index(offset) + i] = (byte) readByte(position + i);
    }
    return this;
  }

  @Override
  public Bytes read(final int offset, final ByteBuffer dst, final int dstOffset, final int length) {
    for (int i = 0; i < length; i++) {
      dst.put(dstOffset + i, (byte) readByte(offset + i));
    }

    return this;
  }

  @Override
  public int readByte(final int offset) {
    return buffer.get(index(offset));
  }

  @Override
  public char readChar(final int offset) {
    return buffer.getChar(index(offset));
  }

  @Override
  public short readShort(final int offset) {
    return buffer.getShort(index(offset));
  }

  @Override
  public int readInt(final int offset) {
    return buffer.getInt(index(offset));
  }

  @Override
  public long readLong(final int offset) {
    return buffer.getLong(index(offset));
  }

  @Override
  public float readFloat(final int offset) {
    return buffer.getFloat(index(offset));
  }

  @Override
  public double readDouble(final int offset) {
    return buffer.getDouble(index(offset));
  }
}
