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

import static io.atomix.storage.buffer.Bytes.BOOLEAN;
import static io.atomix.storage.buffer.Bytes.BYTE;
import static io.atomix.storage.buffer.Bytes.SHORT;

import io.atomix.utils.concurrent.ReferenceManager;
import io.atomix.utils.memory.Memory;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.InvalidMarkException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Abstract buffer implementation.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
public abstract class AbstractBuffer implements Buffer {
  static final int DEFAULT_INITIAL_CAPACITY = 4096;
  static final int MAX_SIZE = Integer.MAX_VALUE - 5;

  protected final Bytes bytes;
  protected final ReferenceManager<Buffer> referenceManager;
  private int offset;
  private int initialCapacity;
  private int capacity;
  private int maxCapacity;
  private int position;
  private int limit = -1;
  private int mark = -1;
  private final AtomicInteger references = new AtomicInteger();
  private SwappedBuffer swap;

  protected AbstractBuffer(final Bytes bytes, final ReferenceManager<Buffer> referenceManager) {
    this(bytes, 0, 0, 0, referenceManager);
  }

  protected AbstractBuffer(
      final Bytes bytes,
      final int offset,
      final int initialCapacity,
      final int maxCapacity,
      final ReferenceManager<Buffer> referenceManager) {
    if (bytes == null) {
      throw new NullPointerException("bytes cannot be null");
    }
    if (offset < 0) {
      throw new IndexOutOfBoundsException("offset out of bounds of the underlying byte array");
    }
    this.bytes = bytes;
    this.offset = offset;
    this.capacity = 0;
    this.initialCapacity = initialCapacity;
    this.maxCapacity = maxCapacity;
    capacity(initialCapacity);
    this.referenceManager = referenceManager;
    references.set(1);
  }

  /** Resets the buffer's internal offset and capacity. */
  protected AbstractBuffer reset(final int offset, final int capacity, final int maxCapacity) {
    this.offset = offset;
    this.capacity = 0;
    this.initialCapacity = capacity;
    this.maxCapacity = maxCapacity;
    capacity(initialCapacity);
    references.set(0);
    rewind();
    return this;
  }

  @Override
  public Buffer acquire() {
    references.incrementAndGet();
    return this;
  }

  @Override
  public boolean release() {
    if (references.decrementAndGet() == 0) {
      if (referenceManager != null) {
        referenceManager.release(this);
      } else {
        bytes.close();
      }
      return true;
    }
    return false;
  }

  @Override
  public int references() {
    return references.get();
  }

  @Override
  public ByteOrder order() {
    return bytes.order();
  }

  @Override
  public Buffer order(final ByteOrder order) {
    if (order == null) {
      throw new NullPointerException("order cannot be null");
    }
    if (order == order()) {
      return this;
    }
    if (swap != null) {
      return swap;
    }
    swap = new SwappedBuffer(this, offset, capacity, maxCapacity, referenceManager);
    return swap;
  }

  @Override
  public boolean isDirect() {
    return bytes.isDirect();
  }

  @Override
  public boolean isReadOnly() {
    return false;
  }

  @Override
  public boolean isFile() {
    return bytes.isFile();
  }

  @Override
  public Buffer asReadOnlyBuffer() {
    return new ReadOnlyBuffer(this, referenceManager)
        .reset(offset, capacity, maxCapacity)
        .position(position)
        .limit(limit);
  }

  @Override
  public int offset() {
    return offset;
  }

  @Override
  public int capacity() {
    return capacity;
  }

  /** Updates the buffer capacity. */
  public Buffer capacity(final int capacity) {
    if (capacity > maxCapacity) {
      throw new IllegalArgumentException("capacity cannot be greater than maximum capacity");
    } else if (capacity < this.capacity) {
      throw new IllegalArgumentException("capacity cannot be decreased");
    } else if (capacity != this.capacity) {
      // It's possible that the bytes could already meet the requirements of the capacity.
      if (offset(capacity) > bytes.size()) {
        bytes.resize((int) Math.min(Memory.Util.toPow2(offset(capacity)), Integer.MAX_VALUE));
      }
      this.capacity = capacity;
    }
    return this;
  }

  @Override
  public int maxCapacity() {
    return maxCapacity;
  }

  @Override
  public Buffer position(final int position) {
    if (limit != -1 && position > limit) {
      throw new IllegalArgumentException("position cannot be greater than limit");
    } else if (limit == -1 && position > maxCapacity) {
      throw new IllegalArgumentException("position cannot be greater than capacity");
    }
    if (position > capacity) {
      capacity((int) Math.min(maxCapacity, Memory.Util.toPow2(position)));
    }
    this.position = position;
    return this;
  }

  @Override
  public int limit() {
    return limit;
  }

  @Override
  public Buffer limit(final int limit) {
    if (limit > maxCapacity) {
      throw new IllegalArgumentException("limit cannot be greater than buffer capacity");
    }
    if (limit < -1) {
      throw new IllegalArgumentException("limit cannot be negative");
    }
    if (limit != -1 && offset(limit) > bytes.size()) {
      bytes.resize(offset(limit));
    }
    this.limit = limit;
    return this;
  }

  @Override
  public int remaining() {
    return (limit == -1 ? maxCapacity : limit) - position;
  }

  @Override
  public boolean hasRemaining() {
    return remaining() > 0;
  }

  @Override
  public Buffer skip(final int length) {
    if (length > remaining()) {
      throw new IndexOutOfBoundsException(
          "length cannot be greater than remaining bytes in the buffer");
    }
    position += length;
    return this;
  }

  @Override
  public Buffer read(final Bytes bytes) {
    this.bytes.read(checkRead(bytes.size()), bytes, 0, bytes.size());
    return this;
  }

  @Override
  public Buffer read(final byte[] bytes) {
    this.bytes.read(checkRead(bytes.length), bytes, 0, bytes.length);
    return this;
  }

  @Override
  public Buffer read(final Bytes bytes, final int offset, final int length) {
    this.bytes.read(checkRead(length), bytes, offset, length);
    return this;
  }

  @Override
  public Buffer read(final byte[] bytes, final int offset, final int length) {
    this.bytes.read(checkRead(length), bytes, offset, length);
    return this;
  }

  @Override
  public Buffer read(final Buffer buffer) {
    final int length = Math.min(buffer.remaining(), remaining());
    read(buffer.bytes(), buffer.offset() + buffer.position(), length);
    buffer.position(buffer.position() + length);
    return this;
  }

  @Override
  public int readByte() {
    return bytes.readByte(checkRead(BYTE));
  }

  @Override
  public int readUnsignedByte() {
    return bytes.readUnsignedByte(checkRead(BYTE));
  }

  @Override
  public char readChar() {
    return bytes.readChar(checkRead(Bytes.CHARACTER));
  }

  @Override
  public short readShort() {
    return bytes.readShort(checkRead(SHORT));
  }

  @Override
  public int readUnsignedShort() {
    return bytes.readUnsignedShort(checkRead(SHORT));
  }

  @Override
  public int readInt() {
    return bytes.readInt(checkRead(Bytes.INTEGER));
  }

  @Override
  public long readUnsignedInt() {
    return bytes.readUnsignedInt(checkRead(Bytes.INTEGER));
  }

  @Override
  public long readLong() {
    return bytes.readLong(checkRead(Bytes.LONG));
  }

  @Override
  public float readFloat() {
    return bytes.readFloat(checkRead(Bytes.FLOAT));
  }

  @Override
  public double readDouble() {
    return bytes.readDouble(checkRead(Bytes.DOUBLE));
  }

  @Override
  public boolean readBoolean() {
    return bytes.readBoolean(checkRead(BYTE));
  }

  @Override
  public String readUTF8() {
    return readString(StandardCharsets.UTF_8);
  }

  @Override
  public void close() {
    references.set(0);
    if (referenceManager != null) {
      referenceManager.release(this);
    } else {
      bytes.close();
    }
  }

  @Override
  public Buffer flip() {
    limit = position;
    position = 0;
    mark = -1;
    return this;
  }

  @Override
  public Buffer mark() {
    this.mark = position;
    return this;
  }

  @Override
  public Buffer reset() {
    if (mark == -1) {
      throw new InvalidMarkException();
    }
    position = mark;
    return this;
  }

  @Override
  public Buffer rewind() {
    position = 0;
    mark = -1;
    return this;
  }

  @Override
  public Buffer clear() {
    position = 0;
    limit = -1;
    mark = -1;
    return this;
  }

  @Override
  public Buffer compact() {
    compact(offset(position), offset, (limit != -1 ? limit : capacity) - offset(position));
    return clear();
  }

  @Override
  public Bytes bytes() {
    return bytes;
  }

  @Override
  public Buffer slice() {
    int maxCapacity = this.maxCapacity - position;
    int capacity =
        Math.min(Math.min(initialCapacity, maxCapacity), bytes.size() - offset(position));
    if (limit != -1) {
      maxCapacity = limit - position;
      capacity = maxCapacity;
    }
    return new SlicedBuffer(this, bytes, offset(position), capacity, maxCapacity);
  }

  @Override
  public Buffer slice(final int length) {
    checkSlice(position, length);
    return new SlicedBuffer(this, bytes, offset(position), length, length);
  }

  @Override
  public Buffer slice(final int offset, final int length) {
    checkSlice(offset, length);
    return new SlicedBuffer(this, bytes, offset(offset), length, length);
  }

  @Override
  public Buffer read(
      final int srcOffset, final Bytes bytes, final int dstOffset, final int length) {
    this.bytes.read(checkRead(srcOffset, length), bytes, dstOffset, length);
    return this;
  }

  @Override
  public Buffer read(
      final int srcOffset, final byte[] bytes, final int dstOffset, final int length) {
    this.bytes.read(checkRead(srcOffset, length), bytes, dstOffset, length);
    return this;
  }

  @Override
  public int readByte(final int offset) {
    return bytes.readByte(checkRead(offset, BYTE));
  }

  @Override
  public int readUnsignedByte(final int offset) {
    return bytes.readUnsignedByte(checkRead(offset, BYTE));
  }

  @Override
  public char readChar(final int offset) {
    return bytes.readChar(checkRead(offset, Bytes.CHARACTER));
  }

  @Override
  public short readShort(final int offset) {
    return bytes.readShort(checkRead(offset, SHORT));
  }

  @Override
  public int readUnsignedShort(final int offset) {
    return bytes.readUnsignedShort(checkRead(offset, SHORT));
  }

  @Override
  public int readInt(final int offset) {
    return bytes.readInt(checkRead(offset, Bytes.INTEGER));
  }

  @Override
  public long readUnsignedInt(final int offset) {
    return bytes.readUnsignedInt(checkRead(offset, Bytes.INTEGER));
  }

  @Override
  public long readLong(final int offset) {
    return bytes.readLong(checkRead(offset, Bytes.LONG));
  }

  @Override
  public float readFloat(final int offset) {
    return bytes.readFloat(checkRead(offset, Bytes.FLOAT));
  }

  @Override
  public double readDouble(final int offset) {
    return bytes.readDouble(checkRead(offset, Bytes.DOUBLE));
  }

  @Override
  public boolean readBoolean(final int offset) {
    return bytes.readBoolean(checkRead(offset, BYTE));
  }

  @Override
  public String readUTF8(final int offset) {
    return readString(offset, StandardCharsets.UTF_8);
  }

  @Override
  public Buffer write(final Bytes bytes) {
    this.bytes.write(checkWrite(bytes.size()), bytes, 0, bytes.size());
    return this;
  }

  @Override
  public Buffer write(final byte[] bytes) {
    this.bytes.write(checkWrite(bytes.length), bytes, 0, bytes.length);
    return this;
  }

  @Override
  public Buffer write(final ByteBuffer src) {
    final int length = src.remaining();
    this.bytes.write(checkWrite(length), src, src.position(), length);
    return this;
  }

  @Override
  public Buffer write(final Bytes bytes, final int offset, final int length) {
    this.bytes.write(checkWrite(length), bytes, offset, length);
    return this;
  }

  @Override
  public Buffer write(final byte[] bytes, final int offset, final int length) {
    this.bytes.write(checkWrite(length), bytes, offset, length);
    return this;
  }

  @Override
  public Buffer write(final Buffer buffer) {
    final int length = Math.min(buffer.remaining(), remaining());
    write(buffer.bytes(), buffer.offset() + buffer.position(), length);
    buffer.position(buffer.position() + length);
    return this;
  }

  @Override
  public Buffer writeByte(final int b) {
    bytes.writeByte(checkWrite(BYTE), b);
    return this;
  }

  @Override
  public Buffer writeUnsignedByte(final int b) {
    bytes.writeUnsignedByte(checkWrite(BYTE), b);
    return this;
  }

  @Override
  public Buffer writeChar(final char c) {
    bytes.writeChar(checkWrite(Bytes.CHARACTER), c);
    return this;
  }

  @Override
  public Buffer writeShort(final short s) {
    bytes.writeShort(checkWrite(SHORT), s);
    return this;
  }

  @Override
  public Buffer writeUnsignedShort(final int s) {
    bytes.writeUnsignedShort(checkWrite(SHORT), s);
    return this;
  }

  @Override
  public Buffer writeInt(final int i) {
    bytes.writeInt(checkWrite(Bytes.INTEGER), i);
    return this;
  }

  @Override
  public Buffer writeUnsignedInt(final long i) {
    bytes.writeUnsignedInt(checkWrite(Bytes.INTEGER), i);
    return this;
  }

  @Override
  public Buffer writeLong(final long l) {
    bytes.writeLong(checkWrite(Bytes.LONG), l);
    return this;
  }

  @Override
  public Buffer writeFloat(final float f) {
    bytes.writeFloat(checkWrite(Bytes.FLOAT), f);
    return this;
  }

  @Override
  public Buffer writeDouble(final double d) {
    bytes.writeDouble(checkWrite(Bytes.DOUBLE), d);
    return this;
  }

  @Override
  public Buffer writeBoolean(final boolean b) {
    bytes.writeBoolean(checkWrite(BYTE), b);
    return this;
  }

  @Override
  public Buffer writeUTF8(final String s) {
    return writeString(s, StandardCharsets.UTF_8);
  }

  @Override
  public Buffer write(final int offset, final Bytes bytes, final int srcOffset, final int length) {
    this.bytes.write(checkWrite(offset, length), bytes, srcOffset, length);
    return this;
  }

  @Override
  public Buffer write(final int offset, final byte[] bytes, final int srcOffset, final int length) {
    this.bytes.write(checkWrite(offset, length), bytes, srcOffset, length);
    return this;
  }

  @Override
  public Buffer write(
      final int offset, final ByteBuffer src, final int srcOffset, final int length) {
    this.bytes.write(checkWrite(offset, length), src, srcOffset, length);
    return this;
  }

  @Override
  public Buffer writeByte(final int offset, final int b) {
    bytes.writeByte(checkWrite(offset, BYTE), b);
    return this;
  }

  @Override
  public Buffer writeUnsignedByte(final int offset, final int b) {
    bytes.writeUnsignedByte(checkWrite(offset, BYTE), b);
    return this;
  }

  @Override
  public Buffer writeChar(final int offset, final char c) {
    bytes.writeChar(checkWrite(offset, Bytes.CHARACTER), c);
    return this;
  }

  @Override
  public Buffer writeShort(final int offset, final short s) {
    bytes.writeShort(checkWrite(offset, SHORT), s);
    return this;
  }

  @Override
  public Buffer writeUnsignedShort(final int offset, final int s) {
    bytes.writeUnsignedShort(checkWrite(offset, SHORT), s);
    return this;
  }

  @Override
  public Buffer writeInt(final int offset, final int i) {
    bytes.writeInt(checkWrite(offset, Bytes.INTEGER), i);
    return this;
  }

  @Override
  public Buffer writeUnsignedInt(final int offset, final long i) {
    bytes.writeUnsignedInt(checkWrite(offset, Bytes.INTEGER), i);
    return this;
  }

  @Override
  public Buffer writeLong(final int offset, final long l) {
    bytes.writeLong(checkWrite(offset, Bytes.LONG), l);
    return this;
  }

  @Override
  public Buffer writeFloat(final int offset, final float f) {
    bytes.writeFloat(checkWrite(offset, Bytes.FLOAT), f);
    return this;
  }

  @Override
  public Buffer writeDouble(final int offset, final double d) {
    bytes.writeDouble(checkWrite(offset, Bytes.DOUBLE), d);
    return this;
  }

  @Override
  public Buffer writeBoolean(final int offset, final boolean b) {
    bytes.writeBoolean(checkWrite(offset, BYTE), b);
    return this;
  }

  @Override
  public Buffer writeUTF8(final int offset, final String s) {
    return writeString(offset, s, StandardCharsets.UTF_8);
  }

  /** Compacts the given bytes. */
  protected abstract void compact(int from, int to, int length);

  @Override
  public int position() {
    return position;
  }

  @Override
  public Buffer read(final ByteBuffer buffer) {
    this.bytes.read(checkRead(buffer.remaining()), buffer, buffer.position(), buffer.remaining());
    return this;
  }

  @Override
  public int readMedium() {
    return bytes.readMedium(checkRead(3));
  }

  @Override
  public int readUnsignedMedium() {
    return bytes.readUnsignedMedium(checkRead(3));
  }

  @Override
  public String readString() {
    return readString(Charset.defaultCharset());
  }

  @Override
  public String readString(final Charset charset) {
    if (readBoolean(position)) {
      final byte[] bytes = new byte[readUnsignedShort(position + BOOLEAN)];
      read(position + BOOLEAN + SHORT, bytes, 0, bytes.length);
      this.position += BOOLEAN + SHORT + bytes.length;
      return new String(bytes, charset);
    } else {
      this.position += BOOLEAN;
    }
    return null;
  }

  /** Returns the real offset of the given relative offset. */
  private int offset(final int offset) {
    return this.offset + offset;
  }

  /** Checks that the offset is within the bounds of the buffer. */
  protected void checkOffset(final int offset) {
    if (offset(offset) < this.offset) {
      throw new IndexOutOfBoundsException();
    } else if (limit == -1) {
      if (offset > maxCapacity) {
        throw new IndexOutOfBoundsException();
      }
    } else {
      if (offset > limit) {
        throw new IndexOutOfBoundsException();
      }
    }
  }

  /** Checks bounds for a slice. */
  protected int checkSlice(final int offset, final int length) {
    checkOffset(offset);
    if (limit == -1) {
      if (offset + length > capacity) {
        if (capacity < maxCapacity) {
          capacity(calculateCapacity(offset + length));
        } else {
          throw new BufferUnderflowException();
        }
      }
    } else {
      if (offset + length > limit) {
        throw new BufferUnderflowException();
      }
    }
    return offset(offset);
  }

  /** Checks bounds for a read for the given length. */
  protected int checkRead(final int length) {
    checkRead(position, length);
    final int previousPosition = this.position;
    this.position = previousPosition + length;
    return offset(previousPosition);
  }

  /** Checks bounds for a read. */
  protected int checkRead(final int offset, final int length) {
    checkOffset(offset);
    if (limit == -1) {
      if (offset + length > capacity) {
        if (capacity < maxCapacity) {
          calculateCapacity(offset, length);
        } else {
          throw new BufferUnderflowException();
        }
      }
    } else {
      if (offset + length > limit) {
        throw new BufferUnderflowException();
      }
    }
    return offset(offset);
  }

  private void calculateCapacity(final int offset, final int length) {
    if (this.offset + offset + length <= bytes.size()) {
      capacity = bytes.size() - this.offset;
    } else {
      capacity(calculateCapacity(offset + length));
    }
  }

  /** Checks bounds for a write of the given length. */
  protected int checkWrite(final int length) {
    checkWrite(position, length);
    final int previousPosition = this.position;
    this.position = previousPosition + length;
    return offset(previousPosition);
  }

  /** Checks bounds for a write. */
  protected int checkWrite(final int offset, final int length) {
    checkOffset(offset);
    if (limit == -1) {
      if (offset + length > capacity) {
        if (capacity < maxCapacity) {
          capacity(calculateCapacity(offset + length));
        } else {
          throw new BufferOverflowException();
        }
      }
    } else {
      if (offset + length > limit) {
        throw new BufferOverflowException();
      }
    }
    return offset(offset);
  }

  /** Calculates the next capacity that meets the given minimum capacity. */
  private int calculateCapacity(final int minimumCapacity) {
    int newCapacity = Math.min(Math.max(capacity, 2), minimumCapacity);
    while (newCapacity < Math.min(minimumCapacity, maxCapacity)) {
      newCapacity <<= 1;
    }
    return Math.min(newCapacity, maxCapacity);
  }

  @Override
  public Buffer zero() {
    bytes.zero(offset);
    return this;
  }

  @Override
  public Buffer zero(final int offset) {
    checkOffset(offset);
    bytes.zero(offset(offset));
    return this;
  }

  @Override
  public Buffer zero(final int offset, final int length) {
    checkOffset(offset);
    bytes.zero(offset(offset), length);
    return this;
  }

  @Override
  public Buffer writeMedium(final int offset, final int m) {
    bytes.writeMedium(checkWrite(offset, 3), m);
    return this;
  }

  @Override
  public Buffer writeUnsignedMedium(final int offset, final int m) {
    bytes.writeUnsignedMedium(checkWrite(offset, 3), m);
    return this;
  }

  @Override
  public Buffer writeString(final int offset, final String s) {
    return writeString(offset, s, Charset.defaultCharset());
  }

  @Override
  public Buffer writeString(final int offset, final String s, final Charset charset) {
    if (s == null) {
      return writeBoolean(checkWrite(offset, BOOLEAN), Boolean.FALSE);
    } else {
      final byte[] bytes = s.getBytes(charset);
      checkWrite(offset, BOOLEAN + SHORT + bytes.length);
      writeBoolean(offset, Boolean.TRUE)
          .writeUnsignedShort(offset + BOOLEAN, bytes.length)
          .write(offset + BOOLEAN + SHORT, bytes, 0, bytes.length);
      return this;
    }
  }

  @Override
  public Buffer flush() {
    bytes.flush();
    return this;
  }

  @Override
  public Buffer read(
      final int offset, final ByteBuffer dst, final int dstOffset, final int length) {
    this.bytes.read(checkRead(offset, length), dst, dstOffset, length);
    return this;
  }

  @Override
  public int readMedium(final int offset) {
    return bytes.readMedium(checkRead(offset, 3));
  }

  @Override
  public int readUnsignedMedium(final int offset) {
    return bytes.readUnsignedMedium(checkRead(offset, 3));
  }

  @Override
  public String readString(final int offset) {
    return readString(offset, Charset.defaultCharset());
  }

  @Override
  public String readString(final int offset, final Charset charset) {
    if (readBoolean(offset)) {
      final byte[] bytes = new byte[readUnsignedShort(offset + BOOLEAN)];
      read(offset + BOOLEAN + SHORT, bytes, 0, bytes.length);
      return new String(bytes, charset);
    }
    return null;
  }

  @Override
  public Buffer writeMedium(final int m) {
    bytes.writeMedium(checkWrite(3), m);
    return this;
  }

  @Override
  public Buffer writeUnsignedMedium(final int m) {
    bytes.writeUnsignedMedium(checkWrite(3), m);
    return this;
  }

  @Override
  public Buffer writeString(final String s) {
    return writeString(s, Charset.defaultCharset());
  }

  @Override
  public Buffer writeString(final String s, final Charset charset) {
    if (s == null) {
      return writeBoolean(checkWrite(BOOLEAN), Boolean.FALSE);
    } else {
      final byte[] bytes = s.getBytes(charset);
      checkWrite(position, BOOLEAN + SHORT + bytes.length);
      writeBoolean(Boolean.TRUE).writeUnsignedShort(bytes.length).write(bytes, 0, bytes.length);
      return this;
    }
  }
}
