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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Wrapped bytes.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
public class WrappedBytes extends AbstractBytes {
  protected final Bytes bytes;
  private final Bytes root;

  public WrappedBytes(final Bytes bytes) {
    if (bytes == null) {
      throw new NullPointerException("bytes cannot be null");
    }
    this.bytes = bytes;
    this.root = bytes instanceof WrappedBytes ? ((WrappedBytes) bytes).root : bytes;
  }

  /** Returns the root bytes. */
  public Bytes root() {
    return root;
  }

  @Override
  public int size() {
    return bytes.size();
  }

  @Override
  public Bytes resize(final int newSize) {
    return bytes.resize(newSize);
  }

  @Override
  public ByteOrder order() {
    return bytes.order();
  }

  @Override
  public boolean readBoolean(final int offset) {
    return bytes.readBoolean(offset);
  }

  @Override
  public int readUnsignedByte(final int offset) {
    return bytes.readUnsignedByte(offset);
  }

  @Override
  public int readUnsignedShort(final int offset) {
    return bytes.readUnsignedShort(offset);
  }

  @Override
  public int readMedium(final int offset) {
    return bytes.readMedium(offset);
  }

  @Override
  public int readUnsignedMedium(final int offset) {
    return bytes.readUnsignedMedium(offset);
  }

  @Override
  public long readUnsignedInt(final int offset) {
    return bytes.readUnsignedInt(offset);
  }

  @Override
  public String readString(final int offset) {
    return bytes.readString(offset);
  }

  @Override
  public String readUTF8(final int offset) {
    return bytes.readUTF8(offset);
  }

  @Override
  public Bytes writeBoolean(final int offset, final boolean b) {
    bytes.writeBoolean(offset, b);
    return this;
  }

  @Override
  public Bytes writeUnsignedByte(final int offset, final int b) {
    bytes.writeUnsignedByte(offset, b);
    return this;
  }

  @Override
  public Bytes writeUnsignedShort(final int offset, final int s) {
    bytes.writeUnsignedShort(offset, s);
    return this;
  }

  @Override
  public Bytes writeMedium(final int offset, final int m) {
    bytes.writeMedium(offset, m);
    return this;
  }

  @Override
  public Bytes writeUnsignedMedium(final int offset, final int m) {
    bytes.writeUnsignedMedium(offset, m);
    return this;
  }

  @Override
  public Bytes writeUnsignedInt(final int offset, final long i) {
    bytes.writeUnsignedInt(offset, i);
    return this;
  }

  @Override
  public Bytes writeString(final int offset, final String s) {
    bytes.writeString(offset, s);
    return this;
  }

  @Override
  public Bytes writeUTF8(final int offset, final String s) {
    bytes.writeUTF8(offset, s);
    return this;
  }

  @Override
  public Bytes flush() {
    bytes.flush();
    return this;
  }

  @Override
  public void close() {
    bytes.close();
  }

  @Override
  public Bytes zero() {
    bytes.zero();
    return this;
  }

  @Override
  public Bytes zero(final int offset) {
    bytes.zero(offset);
    return this;
  }

  @Override
  public Bytes zero(final int offset, final int length) {
    bytes.zero(offset, length);
    return this;
  }

  @Override
  public Bytes write(final int offset, final Bytes src, final int srcOffset, final int length) {
    bytes.write(offset, src, srcOffset, length);
    return this;
  }

  @Override
  public Bytes write(final int offset, final byte[] src, final int srcOffset, final int length) {
    bytes.write(offset, src, srcOffset, length);
    return this;
  }

  @Override
  public Bytes write(
      final int offset, final ByteBuffer src, final int srcOffset, final int length) {
    bytes.write(offset, src, srcOffset, length);
    return this;
  }

  @Override
  public Bytes writeByte(final int offset, final int b) {
    bytes.writeByte(offset, b);
    return this;
  }

  @Override
  public Bytes writeChar(final int offset, final char c) {
    bytes.writeChar(offset, c);
    return this;
  }

  @Override
  public Bytes writeShort(final int offset, final short s) {
    bytes.writeShort(offset, s);
    return this;
  }

  @Override
  public Bytes writeInt(final int offset, final int i) {
    bytes.writeInt(offset, i);
    return this;
  }

  @Override
  public Bytes writeLong(final int offset, final long l) {
    bytes.writeLong(offset, l);
    return this;
  }

  @Override
  public Bytes writeFloat(final int offset, final float f) {
    bytes.writeFloat(offset, f);
    return this;
  }

  @Override
  public Bytes writeDouble(final int offset, final double d) {
    bytes.writeDouble(offset, d);
    return this;
  }

  @Override
  public Bytes read(final int offset, final Bytes dst, final int dstOffset, final int length) {
    bytes.read(offset, dst, dstOffset, length);
    return this;
  }

  @Override
  public Bytes read(final int offset, final byte[] dst, final int dstOffset, final int length) {
    bytes.read(offset, dst, dstOffset, length);
    return this;
  }

  @Override
  public Bytes read(final int offset, final ByteBuffer dst, final int dstOffset, final int length) {
    bytes.read(offset, dst, dstOffset, length);
    return this;
  }

  @Override
  public int readByte(final int offset) {
    return bytes.readByte(offset);
  }

  @Override
  public char readChar(final int offset) {
    return bytes.readChar(offset);
  }

  @Override
  public short readShort(final int offset) {
    return bytes.readShort(offset);
  }

  @Override
  public int readInt(final int offset) {
    return bytes.readInt(offset);
  }

  @Override
  public long readLong(final int offset) {
    return bytes.readLong(offset);
  }

  @Override
  public float readFloat(final int offset) {
    return bytes.readFloat(offset);
  }

  @Override
  public double readDouble(final int offset) {
    return bytes.readDouble(offset);
  }
}
