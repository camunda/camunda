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

import io.atomix.utils.concurrent.ReferenceCounted;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Navigable byte buffer for input/output operations.
 *
 * <p>The byte buffer provides a fluent interface for reading bytes from and writing bytes to some
 * underlying storage implementation. The {@code Buffer} type is agnostic about the specific
 * underlying storage implementation, but different buffer implementations may be designed for
 * specific storage layers.
 *
 * <p>Aside from the underlying storage implementation, this buffer works very similarly to Java's
 * {@link java.nio.ByteBuffer}. It intentionally exposes methods that can be easily understood by
 * any developer with experience with {@code ByteBuffer}.
 *
 * <p>In order to support reading and writing from buffers, {@code Buffer} implementations maintain
 * a series of pointers to aid in navigating the buffer.
 *
 * <p>Most notable of these pointers is the {@code position}. When values are written to or read
 * from the buffer, the buffer increments its internal {@code position} according to the number of
 * bytes read. This allows users to iterate through the bytes in the buffer without maintaining
 * external pointers.
 *
 * <p>
 *
 * <pre>{@code
 * try (Buffer buffer = DirectBuffer.allocate(1024)) {
 *   buffer.writeInt(1);
 *   buffer.flip();
 *   assert buffer.readInt() == 1;
 * }
 *
 * }</pre>
 *
 * <p>Buffers implement {@link ReferenceCounted} in order to keep track of the number of currently
 * held references to a given buffer. When a buffer is constructed, the buffer contains only {@code
 * 1} reference. This reference can be released via the {@link Buffer#close()} method which can be
 * called automatically by using a try-with-resources statement demonstrated above. Additional
 * references to the buffer should be acquired via {@link ReferenceCounted#acquire()} and released
 * via {@link ReferenceCounted#release}. Once all references to a buffer have been released -
 * including the initial reference - the memory will be freed (for in-memory buffers) and writes
 * will be automatically flushed to disk (for persistent buffers).
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
public interface Buffer
    extends BytesInput<Buffer>,
        BufferInput<Buffer>,
        BytesOutput<Buffer>,
        BufferOutput<Buffer>,
        ReferenceCounted<Buffer> {

  /**
   * Returns whether the buffer has an array.
   *
   * @return Whether the buffer has an underlying array.
   */
  default boolean hasArray() {
    return false;
  }

  /**
   * Returns the underlying byte array.
   *
   * @return the underlying byte array
   * @throws UnsupportedOperationException if a heap array is not supported
   */
  default byte[] array() {
    throw new UnsupportedOperationException();
  }

  /**
   * Returns the byte order.
   *
   * <p>For consistency with {@link java.nio.ByteBuffer}, all buffer implementations are initially
   * in {@link ByteOrder#BIG_ENDIAN} order.
   *
   * @return The byte order.
   */
  ByteOrder order();

  /**
   * Sets the byte order, returning a new swapped {@link Buffer} instance.
   *
   * <p>By default, all buffers are read and written in {@link ByteOrder#BIG_ENDIAN} order. This
   * provides complete consistency with {@link java.nio.ByteBuffer}. To flip buffers to {@link
   * ByteOrder#LITTLE_ENDIAN} order, this buffer's {@code Bytes} instance is decorated by a {@link
   * SwappedBytes} instance which will reverse read and written bytes using, e.g. {@link
   * Integer#reverseBytes(int)}.
   *
   * @param order The byte order.
   * @return The updated buffer.
   */
  Buffer order(ByteOrder order);

  /**
   * Returns a boolean value indicating whether the buffer is a direct buffer.
   *
   * @return Indicates whether the buffer is a direct buffer.
   */
  boolean isDirect();

  /**
   * Returns a boolean value indicating whether the buffer is a read-only buffer.
   *
   * @return Indicates whether the buffer is a read-only buffer.
   */
  boolean isReadOnly();

  /**
   * Returns a boolean value indicating whether the buffer is backed by a file.
   *
   * @return Indicates whether the buffer is backed by a file.
   */
  boolean isFile();

  /**
   * Returns a read-only view of the buffer.
   *
   * <p>The returned buffer will share the underlying {@link Bytes} with which buffer, but the
   * buffer's {@code limit}, {@code capacity}, and {@code position} will be independent of this
   * buffer.
   *
   * @return A read-only buffer.
   */
  Buffer asReadOnlyBuffer();

  /**
   * Returns the buffer's starting offset within the underlying {@link Bytes}.
   *
   * <p>The offset is used to calculate the absolute position of the buffer's relative {@link
   * Buffer#position() position} within the underlying {@link Bytes}.
   *
   * @return The buffer's offset.
   */
  int offset();

  /**
   * Returns the buffer's capacity.
   *
   * <p>The capacity represents the total amount of storage space allocated to the buffer by the
   * underlying storage implementation. As bytes are written to the buffer, the buffer's capacity
   * may grow up to {@link Buffer#maxCapacity()}.
   *
   * @return The buffer's capacity.
   */
  int capacity();

  /**
   * Sets the buffer's capacity.
   *
   * <p>The given capacity must be greater than the current {@link Buffer#capacity() capacity} and
   * less than or equal to {@link Buffer#maxCapacity()}. When the capacity is changed, the
   * underlying {@link Bytes} will be resized via {@link Bytes#resize(int)}.
   *
   * @param capacity The capacity to which to resize the buffer.
   * @return The resized buffer.
   * @throws IllegalArgumentException If the given {@code capacity} is less than the current {@code
   *     capacity} or greater than {@link Buffer#maxCapacity()}
   */
  Buffer capacity(int capacity);

  /**
   * Returns the maximum allowed capacity for the buffer.
   *
   * <p>The maximum capacity is the limit up to which this buffer's {@link Buffer#capacity()
   * capacity} can be expanded. While the capacity grows, the maximum capacity is fixed from the
   * moment the buffer is created and cannot change.
   *
   * @return The buffer's maximum capacity.
   */
  int maxCapacity();

  /**
   * Sets the buffer's current read/write position.
   *
   * <p>The position is an internal cursor that tracks where to write/read bytes in the underlying
   * storage implementation.
   *
   * @param position The position to set.
   * @return This buffer.
   * @throws IllegalArgumentException If the given position is less than {@code 0} or more than
   *     {@link Buffer#limit()}
   */
  Buffer position(int position);

  /**
   * Returns the buffer's read/write limit.
   *
   * <p>The limit dictates the highest position to which bytes can be read from or written to the
   * buffer. If the limit is not explicitly set then it will always equal the buffer's {@link
   * Buffer#maxCapacity() maxCapacity}. Note that the limit may be set by related methods such as
   * {@link Buffer#flip()}
   *
   * @return The buffer's limit.
   */
  int limit();

  /**
   * Sets the buffer's read/write limit.
   *
   * <p>The limit dictates the highest position to which bytes can be read from or written to the
   * buffer. The limit must be within the bounds of the buffer, i.e. greater than {@code 0} and less
   * than or equal to {@link Buffer#capacity()}
   *
   * @param limit The limit to set.
   * @return This buffer.
   * @throws IllegalArgumentException If the given limit is less than {@code 0} or more than {@link
   *     Buffer#capacity()}
   */
  Buffer limit(int limit);

  /**
   * Returns the number of bytes remaining in the buffer until the {@link Buffer#limit()} is
   * reached.
   *
   * <p>The bytes remaining is calculated by {@code buffer.limit() - buffer.position()}. If no limit
   * is set on the buffer then the {@link Buffer#maxCapacity() maxCapacity} will be used.
   *
   * @return The number of bytes remaining in the buffer.
   */
  @Override
  int remaining();

  /**
   * Returns a boolean indicating whether the buffer has bytes remaining.
   *
   * <p>If {@link Buffer#remaining()} is greater than {@code 0} then this method will return {@code
   * true}, otherwise {@code false}
   *
   * @return Indicates whether bytes are remaining in the buffer. {@code true} if {@link
   *     Buffer#remaining()} is greater than {@code 0}, {@code false} otherwise.
   */
  @Override
  boolean hasRemaining();

  /**
   * Advances the buffer's {@code position} {@code length} bytes.
   *
   * @param length The number of bytes to advance this buffer's {@code position}.
   * @return This buffer.
   * @throws IndexOutOfBoundsException If {@code length} is greater than {@link Buffer#remaining()}
   */
  @Override
  Buffer skip(int length);

  /**
   * Reads bytes into the given byte array.
   *
   * <p>Bytes will be read starting at the current buffer position until either the byte array
   * {@code length} or the {@link Buffer#limit()} has been reached. If {@link Buffer#remaining()} is
   * less than the {@code length} of the given byte array, a {@link
   * java.nio.BufferUnderflowException} will be thrown.
   *
   * @param bytes The byte array into which to read bytes.
   * @return The buffer.
   * @throws java.nio.BufferUnderflowException If the given byte array's {@code length} is greater
   *     than {@link Buffer#remaining()}
   * @see Buffer#read(Bytes, int, int)
   * @see Buffer#read(int, Bytes, int, int)
   */
  @Override
  Buffer read(Bytes bytes);

  /**
   * Reads bytes into the given byte array.
   *
   * <p>Bytes will be read starting at the current buffer position until either the byte array
   * {@code length} or the {@link Buffer#limit()} has been reached. If {@link Buffer#remaining()} is
   * less than the {@code length} of the given byte array, a {@link
   * java.nio.BufferUnderflowException} will be thrown.
   *
   * @param bytes The byte array into which to read bytes.
   * @return The buffer.
   * @throws java.nio.BufferUnderflowException If the given byte array's {@code length} is greater
   *     than {@link Buffer#remaining()}
   * @see Buffer#read(byte[], int, int)
   * @see Buffer#read(int, byte[], int, int)
   */
  @Override
  Buffer read(byte[] bytes);

  /**
   * Reads bytes into the given byte array starting at the current position.
   *
   * <p>Bytes will be read from the current position up to the given length. If the provided {@code
   * length} is greater than {@link Buffer#remaining()} then a {@link
   * java.nio.BufferUnderflowException} will be thrown. If the {@code offset} is out of bounds of
   * the buffer then an {@link IndexOutOfBoundsException} will be thrown.
   *
   * @param bytes The byte array into which to read bytes.
   * @param dstOffset The offset at which to write bytes into the given buffer
   * @return The buffer.
   * @throws java.nio.BufferUnderflowException If {@code length} is greater than {@link
   *     Buffer#remaining()}
   * @throws IndexOutOfBoundsException If the given offset is out of the bounds of the buffer. Note
   *     that bounds are determined by the buffer's {@link Buffer#limit()} rather than capacity.
   * @see Buffer#read(Bytes)
   * @see Buffer#read(int, Bytes, int, int)
   */
  @Override
  Buffer read(Bytes bytes, int dstOffset, int length);

  /**
   * Reads bytes into the given byte array starting at current position up to the given length.
   *
   * <p>Bytes will be read from the current position up to the given length. If the provided {@code
   * length} is greater than {@link Buffer#remaining()} then a {@link
   * java.nio.BufferUnderflowException} will be thrown. If the {@code offset} is out of bounds of
   * the buffer then an {@link IndexOutOfBoundsException} will be thrown.
   *
   * @param bytes The byte array into which to read bytes.
   * @param offset The offset at which to write bytes into the given buffer
   * @return The buffer.
   * @throws java.nio.BufferUnderflowException If {@code length} is greater than {@link
   *     Buffer#remaining()}
   * @throws IndexOutOfBoundsException If the given offset is out of the bounds of the buffer. Note
   *     that bounds are determined by the buffer's {@link Buffer#limit()} rather than capacity.
   * @see Buffer#read(byte[])
   * @see Buffer#read(int, byte[], int, int)
   */
  @Override
  Buffer read(byte[] bytes, int offset, int length);

  /**
   * Reads bytes into the given buffer.
   *
   * <p>Bytes will be read starting at the current buffer position until either {@link
   * Buffer#limit()} has been reached. If {@link Buffer#remaining()} is less than the {@link
   * Buffer#remaining()} of the given buffer, a {@link java.nio.BufferUnderflowException} will be
   * thrown.
   *
   * @param buffer The buffer into which to read bytes.
   * @return The buffer.
   * @throws java.nio.BufferUnderflowException If the given {@link Buffer#remaining()} is greater
   *     than this buffer's {@link Buffer#remaining()}
   */
  @Override
  Buffer read(Buffer buffer);

  /**
   * Reads a byte from the buffer at the current position.
   *
   * <p>When the byte is read from the buffer, the buffer's {@code position} will be advanced by
   * {@link Bytes#BYTE}. If there are no bytes remaining in the buffer then a {@link
   * java.nio.BufferUnderflowException} will be thrown.
   *
   * @return The read byte.
   * @throws java.nio.BufferUnderflowException If {@link Buffer#remaining()} is less than {@link
   *     Bytes#BYTE}
   * @see Buffer#readByte(int)
   */
  @Override
  int readByte();

  /**
   * Reads an unsigned byte from the buffer at the current position.
   *
   * <p>When the byte is read from the buffer, the buffer's {@code position} will be advanced by
   * {@link Bytes#BYTE}. If there are no bytes remaining in the buffer then a {@link
   * java.nio.BufferUnderflowException} will be thrown.
   *
   * @return The read byte.
   * @throws java.nio.BufferUnderflowException If {@link Buffer#remaining()} is less than {@link
   *     Bytes#BYTE}
   * @see Buffer#readUnsignedByte(int)
   */
  @Override
  int readUnsignedByte();

  /**
   * Reads a 16-bit character from the buffer at the current position.
   *
   * <p>When the character is read from the buffer, the buffer's {@code position} will be advanced
   * by {@link Bytes#CHARACTER}. If there are less than {@link Bytes#CHARACTER} bytes remaining in
   * the buffer then a {@link java.nio.BufferUnderflowException} will be thrown.
   *
   * @return The read character.
   * @throws java.nio.BufferUnderflowException If {@link Buffer#remaining()} is less than {@link
   *     Bytes#CHARACTER}
   * @see Buffer#readChar(int)
   */
  @Override
  char readChar();

  /**
   * Reads a 16-bit signed integer from the buffer at the current position.
   *
   * <p>When the short is read from the buffer, the buffer's {@code position} will be advanced by
   * {@link Bytes#SHORT}. If there are less than {@link Bytes#SHORT} bytes remaining in the buffer
   * then a {@link java.nio.BufferUnderflowException} will be thrown.
   *
   * @return The read short.
   * @throws java.nio.BufferUnderflowException If {@link Buffer#remaining()} is less than {@link
   *     Bytes#SHORT}
   * @see Buffer#readShort(int)
   */
  @Override
  short readShort();

  /**
   * Reads a 16-bit unsigned integer from the buffer at the current position.
   *
   * <p>When the short is read from the buffer, the buffer's {@code position} will be advanced by
   * {@link Bytes#SHORT}. If there are less than {@link Bytes#SHORT} bytes remaining in the buffer
   * then a {@link java.nio.BufferUnderflowException} will be thrown.
   *
   * @return The read short.
   * @throws java.nio.BufferUnderflowException If {@link Buffer#remaining()} is less than {@link
   *     Bytes#SHORT}
   * @see Buffer#readUnsignedShort(int)
   */
  @Override
  int readUnsignedShort();

  /**
   * Reads a 32-bit signed integer from the buffer at the current position.
   *
   * <p>When the integer is read from the buffer, the buffer's {@code position} will be advanced by
   * {@link Bytes#INTEGER}. If there are less than {@link Bytes#INTEGER} bytes remaining in the
   * buffer then a {@link java.nio.BufferUnderflowException} will be thrown.
   *
   * @return The read integer.
   * @throws java.nio.BufferUnderflowException If {@link Buffer#remaining()} is less than {@link
   *     Bytes#INTEGER}
   * @see Buffer#readInt(int)
   */
  @Override
  int readInt();

  /**
   * Reads a 32-bit unsigned integer from the buffer at the current position.
   *
   * <p>When the integer is read from the buffer, the buffer's {@code position} will be advanced by
   * {@link Bytes#INTEGER}. If there are less than {@link Bytes#INTEGER} bytes remaining in the
   * buffer then a {@link java.nio.BufferUnderflowException} will be thrown.
   *
   * @return The read integer.
   * @throws java.nio.BufferUnderflowException If {@link Buffer#remaining()} is less than {@link
   *     Bytes#INTEGER}
   * @see Buffer#readUnsignedInt(int)
   */
  @Override
  long readUnsignedInt();

  /**
   * Reads a 64-bit signed integer from the buffer at the current position.
   *
   * <p>When the long is read from the buffer, the buffer's {@code position} will be advanced by
   * {@link Bytes#LONG}. If there are less than {@link Bytes#LONG} bytes remaining in the buffer
   * then a {@link java.nio.BufferUnderflowException} will be thrown.
   *
   * @return The read long.
   * @throws java.nio.BufferUnderflowException If {@link Buffer#remaining()} is less than {@link
   *     Bytes#LONG}
   * @see Buffer#readLong(int)
   */
  @Override
  long readLong();

  /**
   * Reads a single-precision 32-bit floating point number from the buffer at the current position.
   *
   * <p>When the float is read from the buffer, the buffer's {@code position} will be advanced by
   * {@link Bytes#FLOAT}. If there are less than {@link Bytes#FLOAT} bytes remaining in the buffer
   * then a {@link java.nio.BufferUnderflowException} will be thrown.
   *
   * @return The read float.
   * @throws java.nio.BufferUnderflowException If {@link Buffer#remaining()} is less than {@link
   *     Bytes#FLOAT}
   * @see Buffer#readFloat(int)
   */
  @Override
  float readFloat();

  /**
   * Reads a double-precision 64-bit floating point number from the buffer at the current position.
   *
   * <p>When the double is read from the buffer, the buffer's {@code position} will be advanced by
   * {@link Bytes#DOUBLE}. If there are less than {@link Bytes#DOUBLE} bytes remaining in the buffer
   * then a {@link java.nio.BufferUnderflowException} will be thrown.
   *
   * @return The read double.
   * @throws java.nio.BufferUnderflowException If {@link Buffer#remaining()} is less than {@link
   *     Bytes#DOUBLE}
   * @see Buffer#readDouble(int)
   */
  @Override
  double readDouble();

  /**
   * Reads a 1 byte boolean from the buffer at the current position.
   *
   * <p>When the boolean is read from the buffer, the buffer's {@code position} will be advanced by
   * {@code 1}. If there are no bytes remaining in the buffer then a {@link
   * java.nio.BufferUnderflowException} will be thrown.
   *
   * @return The read boolean.
   * @throws java.nio.BufferUnderflowException If {@link Buffer#remaining()} is less than {@code 1}
   * @see Buffer#readBoolean(int)
   */
  @Override
  boolean readBoolean();

  /**
   * Reads a UTF-8 string from the buffer at the current position.
   *
   * <p>When the string is read from the buffer, the buffer's {@code position} will be advanced by 2
   * bytes plus the byte length of the string. If there are no bytes remaining in the buffer then a
   * {@link java.nio.BufferUnderflowException} will be thrown.
   *
   * @return The read string.
   * @throws java.nio.BufferUnderflowException If {@link Buffer#remaining()} is less than {@code 1}
   * @see Buffer#readUTF8(int)
   */
  @Override
  String readUTF8();

  /**
   * Closes the buffer.
   *
   * <p>This method effectively acts as an alias to {@link Buffer#release()} and allows buffers to
   * be used as resources in try-with-resources statements. When the buffer is closed the internal
   * reference counter defined by {@link ReferenceCounted} will be decremented. However, if
   * references to the buffer still exist then those references will be allowed to continue to
   * operate on the buffer until all references have been released.
   */
  @Override
  void close();

  /**
   * Flips the buffer.
   *
   * <p>The limit is set to the current position and then the position is set to zero. If the mark
   * is defined then it is discarded.
   *
   * <pre>{@code
   * assert buffer.writeLong(1234).flip().readLong() == 1234;
   *
   * }</pre>
   *
   * @return This buffer.
   */
  Buffer flip();

  /**
   * Sets a mark at the current position.
   *
   * <p>The mark is a simple internal reference to the buffer's current position. Marks can be used
   * to reset the buffer to a specific position after some operation.
   *
   * <p>
   *
   * <pre>{@code
   * buffer.mark();
   * buffer.writeInt(1).writeBoolean(true);
   * buffer.reset();
   * assert buffer.readInt() == 1;
   *
   * }</pre>
   *
   * @return This buffer.
   */
  Buffer mark();

  /**
   * Resets the buffer's position to the previously-marked position.
   *
   * <p>Invoking this method neither changes nor discards the mark's value.
   *
   * @return This buffer.
   * @throws java.nio.InvalidMarkException If no mark is set.
   */
  Buffer reset();

  /**
   * Rewinds the buffer. The position is set to zero and the mark is discarded.
   *
   * @return This buffer.
   */
  Buffer rewind();

  /**
   * Clears the buffer.
   *
   * <p>The position is set to zero, the limit is set to the capacity, and the mark is discarded.
   *
   * @return This buffer.
   */
  Buffer clear();

  /**
   * Compacts the buffer, moving bytes from the current position to the end of the buffer to the
   * head of the buffer.
   *
   * @return This buffer.
   */
  Buffer compact();

  /**
   * Returns a duplicate of the buffer.
   *
   * @return A duplicate buffer.
   */
  Buffer duplicate();

  /**
   * Returns the bytes underlying the buffer.
   *
   * <p>The buffer is a wrapper around {@link Bytes} that handles writing sequences of bytes by
   * tracking positions and limits. This method returns the {@link Bytes} that this buffer wraps.
   *
   * @return The underlying bytes.
   */
  Bytes bytes();

  /**
   * Returns a view of this buffer starting at the current position.
   *
   * <p>The returned buffer will contain the same underlying {@link Bytes} instance as this buffer,
   * but its pointers will be offset by the current {@code position} of this buffer at the time the
   * slice is created. Calls to {@link Buffer#rewind()} and similar methods on the resulting {@link
   * Buffer} will result in the buffer's {@code position} being reset to the {@code position} of
   * this buffer at the time the slice was created.
   *
   * <p>The returned buffer is reference counted separately from this buffer. Therefore, closing the
   * returned buffer will release the buffer back to the internal buffer poll and will not result in
   * this buffer being closed. Users should always call {@link Buffer#close()} once finished using
   * the buffer slice.
   *
   * @return A slice of this buffer.
   * @see Buffer#slice(int)
   * @see Buffer#slice(int, int)
   */
  Buffer slice();

  /**
   * Returns a view of this buffer of the given length starting at the current position.
   *
   * <p>The returned buffer will contain the same underlying {@link Bytes} instance as this buffer,
   * but its pointers will be offset by the current {@code position} of this buffer at the time the
   * slice is created. Calls to {@link Buffer#rewind()} and similar methods on the resulting {@link
   * Buffer} will result in the buffer's {@code position} being reset to the {@code position} of
   * this buffer at the time the slice was created.
   *
   * <p>The returned buffer is reference counted separately from this buffer. Therefore, closing the
   * returned buffer will release the buffer back to the internal buffer poll and will not result in
   * this buffer being closed. Users should always call {@link Buffer#close()} once finished using
   * the buffer slice.
   *
   * @param length The length of the slice.
   * @return A slice of this buffer.
   * @see Buffer#slice()
   * @see Buffer#slice(int, int)
   */
  Buffer slice(int length);

  /**
   * Returns a view of this buffer starting at the given offset with the given length.
   *
   * <p>The returned buffer will contain the same underlying {@link Bytes} instance as this buffer,
   * but its pointers will be offset by the given {@code offset} and its length limited by the given
   * {@code length}. Calls to {@link Buffer#rewind()} and similar methods on the resulting {@link
   * Buffer} will result in the buffer's {@code position} being reset to the given {@code offset}.
   *
   * <p>The returned buffer is reference counted separately from this buffer. Therefore, closing the
   * returned buffer will release the buffer back to the internal buffer poll and will not result in
   * this buffer being closed. Users should always call {@link Buffer#close()} once finished using
   * the buffer slice.
   *
   * @param offset The offset at which to begin the slice.
   * @param length The number of bytes in the slice.
   * @return The buffer slice.
   * @throws IndexOutOfBoundsException If the given offset is not contained within the bounds of
   *     this buffer
   * @throws java.nio.BufferUnderflowException If the length of the remaining bytes in the buffer is
   *     less than {@code length}
   * @see Buffer#slice()
   * @see Buffer#slice(int)
   */
  Buffer slice(int offset, int length);

  /**
   * Reads bytes into the given byte array starting at the given offset up to the given length.
   *
   * <p>Bytes will be read from the given starting offset up to the given length. If the provided
   * {@code length} is greater than {@link Buffer#limit() - srcOffset} then a {@link
   * java.nio.BufferUnderflowException} will be thrown. If the {@code srcOffset} is out of bounds of
   * the buffer then an {@link IndexOutOfBoundsException} will be thrown.
   *
   * @param srcOffset The offset from which to start reading bytes.
   * @param bytes The byte array into which to read bytes.
   * @param dstOffset The offset at which to write bytes into the given buffer
   * @return The buffer.
   * @throws java.nio.BufferUnderflowException If {@code length} is greater than {@link
   *     Buffer#remaining()}
   * @throws IndexOutOfBoundsException If the given offset is out of the bounds of the buffer. Note
   *     that bounds are determined by the buffer's {@link Buffer#limit()} rather than capacity.
   * @see Buffer#read(Bytes)
   * @see Buffer#read(Bytes, int, int)
   */
  @Override
  Buffer read(int srcOffset, Bytes bytes, int dstOffset, int length);

  /**
   * Reads bytes into the given byte array starting at the given offset up to the given length.
   *
   * <p>Bytes will be read from the given starting offset up to the given length. If the provided
   * {@code length} is greater than {@link Buffer#remaining()} then a {@link
   * java.nio.BufferUnderflowException} will be thrown. If the {@code offset} is out of bounds of
   * the buffer then an {@link IndexOutOfBoundsException} will be thrown.
   *
   * @param srcOffset The offset from which to start reading bytes.
   * @param bytes The byte array into which to read bytes.
   * @param dstOffset The offset at which to write bytes into the given buffer
   * @return The buffer.
   * @throws java.nio.BufferUnderflowException If {@code length} is greater than {@link
   *     Buffer#remaining()}
   * @throws IndexOutOfBoundsException If the given offset is out of the bounds of the buffer. Note
   *     that bounds are determined by the buffer's {@link Buffer#limit()} rather than capacity.
   * @see Buffer#read(byte[])
   * @see Buffer#read(byte[], int, int)
   */
  @Override
  Buffer read(int srcOffset, byte[] bytes, int dstOffset, int length);

  /**
   * Reads a byte from the buffer at the given offset.
   *
   * <p>The byte will be read from the given offset. If the given index is out of the bounds of the
   * buffer then a {@link IndexOutOfBoundsException} will be thrown.
   *
   * @param offset The offset at which to read the byte.
   * @return The read byte.
   * @throws IndexOutOfBoundsException If the given offset is out of the bounds of the buffer. Note
   *     that bounds are determined by the buffer's {@link Buffer#limit()} rather than capacity.
   * @see Buffer#readByte()
   */
  @Override
  int readByte(int offset);

  /**
   * Reads an unsigned byte from the buffer at the given offset.
   *
   * <p>The byte will be read from the given offset. If the given index is out of the bounds of the
   * buffer then a {@link IndexOutOfBoundsException} will be thrown.
   *
   * @param offset The offset at which to read the byte.
   * @return The read byte.
   * @throws IndexOutOfBoundsException If the given offset is out of the bounds of the buffer. Note
   *     that bounds are determined by the buffer's {@link Buffer#limit()} rather than capacity.
   * @see Buffer#readUnsignedByte()
   */
  @Override
  int readUnsignedByte(int offset);

  /**
   * Reads a 16-bit character from the buffer at the given offset.
   *
   * <p>The character will be read from the given offset. If the given index is out of the bounds of
   * the buffer then a {@link IndexOutOfBoundsException} will be thrown.
   *
   * @param offset The offset at which to read the character.
   * @return The read character.
   * @throws IndexOutOfBoundsException If the given offset is out of the bounds of the buffer. Note
   *     that bounds are determined by the buffer's {@link Buffer#limit()} rather than capacity.
   * @see Buffer#readChar()
   */
  @Override
  char readChar(int offset);

  /**
   * Reads a 16-bit signed integer from the buffer at the given offset.
   *
   * <p>The short will be read from the given offset. If the given index is out of the bounds of the
   * buffer then a {@link IndexOutOfBoundsException} will be thrown.
   *
   * @param offset The offset at which to read the short.
   * @return The read short.
   * @throws IndexOutOfBoundsException If the given offset is out of the bounds of the buffer. Note
   *     that bounds are determined by the buffer's {@link Buffer#limit()} rather than capacity.
   * @see Buffer#readShort()
   */
  @Override
  short readShort(int offset);

  /**
   * Reads a 16-bit unsigned integer from the buffer at the given offset.
   *
   * <p>The short will be read from the given offset. If the given index is out of the bounds of the
   * buffer then a {@link IndexOutOfBoundsException} will be thrown.
   *
   * @param offset The offset at which to read the short.
   * @return The read short.
   * @throws IndexOutOfBoundsException If the given offset is out of the bounds of the buffer. Note
   *     that bounds are determined by the buffer's {@link Buffer#limit()} rather than capacity.
   * @see Buffer#readUnsignedShort()
   */
  @Override
  int readUnsignedShort(int offset);

  /**
   * Reads a 32-bit signed integer from the buffer at the given offset.
   *
   * <p>The integer will be read from the given offset. If the given index is out of the bounds of
   * the buffer then a {@link IndexOutOfBoundsException} will be thrown.
   *
   * @param offset The offset at which to read the integer.
   * @return The read integer.
   * @throws IndexOutOfBoundsException If the given offset is out of the bounds of the buffer. Note
   *     that bounds are determined by the buffer's {@link Buffer#limit()} rather than capacity.
   * @see Buffer#readInt()
   */
  @Override
  int readInt(int offset);

  /**
   * Reads a 32-bit unsigned integer from the buffer at the given offset.
   *
   * <p>The integer will be read from the given offset. If the given index is out of the bounds of
   * the buffer then a {@link IndexOutOfBoundsException} will be thrown.
   *
   * @param offset The offset at which to read the integer.
   * @return The read integer.
   * @throws IndexOutOfBoundsException If the given offset is out of the bounds of the buffer. Note
   *     that bounds are determined by the buffer's {@link Buffer#limit()} rather than capacity.
   * @see Buffer#readUnsignedInt()
   */
  @Override
  long readUnsignedInt(int offset);

  /**
   * Reads a 64-bit signed integer from the buffer at the given offset.
   *
   * <p>The long will be read from the given offset. If the given index is out of the bounds of the
   * buffer then a {@link IndexOutOfBoundsException} will be thrown.
   *
   * @param offset The offset at which to read the long.
   * @return The read long.
   * @throws IndexOutOfBoundsException If the given offset is out of the bounds of the buffer. Note
   *     that bounds are determined by the buffer's {@link Buffer#limit()} rather than capacity.
   * @see Buffer#readLong()
   */
  @Override
  long readLong(int offset);

  /**
   * Reads a single-precision 32-bit floating point number from the buffer at the given offset.
   *
   * <p>The float will be read from the given offset. If the given index is out of the bounds of the
   * buffer then a {@link IndexOutOfBoundsException} will be thrown.
   *
   * @param offset The offset at which to read the float.
   * @return The read float.
   * @throws IndexOutOfBoundsException If the given offset is out of the bounds of the buffer. Note
   *     that bounds are determined by the buffer's {@link Buffer#limit()} rather than capacity.
   * @see Buffer#readFloat()
   */
  @Override
  float readFloat(int offset);

  /**
   * Reads a double-precision 64-bit floating point number from the buffer at the given offset.
   *
   * <p>The double will be read from the given offset. If the given index is out of the bounds of
   * the buffer then a {@link IndexOutOfBoundsException} will be thrown.
   *
   * @param offset The offset at which to read the double.
   * @return The read double.
   * @throws IndexOutOfBoundsException If the given offset is out of the bounds of the buffer. Note
   *     that bounds are determined by the buffer's {@link Buffer#limit()} rather than capacity.
   * @see Buffer#readDouble()
   */
  @Override
  double readDouble(int offset);

  /**
   * Reads a 1 byte boolean from the buffer at the given offset.
   *
   * <p>The boolean will be read from the given offset. If the given index is out of the bounds of
   * the buffer then a {@link IndexOutOfBoundsException} will be thrown.
   *
   * @param offset The offset at which to read the boolean.
   * @return The read boolean.
   * @throws IndexOutOfBoundsException If the given offset is out of the bounds of the buffer. Note
   *     that bounds are determined by the buffer's {@link Buffer#limit()} rather than capacity.
   * @see Buffer#readBoolean()
   */
  @Override
  boolean readBoolean(int offset);

  /**
   * Reads a UTF-8 string from the buffer at the given offset.
   *
   * <p>The string will be read from the given offset. If the given index is out of the bounds of
   * the buffer then a {@link IndexOutOfBoundsException} will be thrown.
   *
   * @param offset The offset at which to read the boolean.
   * @return The read string.
   * @throws IndexOutOfBoundsException If the given offset is out of the bounds of the buffer. Note
   *     that bounds are determined by the buffer's {@link Buffer#limit()} rather than capacity.
   * @see Buffer#readUTF8()
   */
  @Override
  String readUTF8(int offset);

  /**
   * Writes an array of bytes to the buffer.
   *
   * <p>When the bytes are written to the buffer, the buffer's {@code position} will be advanced by
   * the number of bytes in the provided byte array. If the number of bytes exceeds {@link
   * Buffer#limit()} then an {@link java.nio.BufferOverflowException} will be thrown.
   *
   * @param bytes The array of bytes to write.
   * @return The written buffer.
   * @throws java.nio.BufferOverflowException If the number of bytes exceeds the buffer's remaining
   *     bytes.
   * @see Buffer#write(Bytes, int, int)
   * @see Buffer#write(int, Bytes, int, int)
   */
  @Override
  Buffer write(Bytes bytes);

  /**
   * Writes an array of bytes to the buffer.
   *
   * <p>When the bytes are written to the buffer, the buffer's {@code position} will be advanced by
   * the number of bytes in the provided byte array. If the number of bytes exceeds {@link
   * Buffer#limit()} then an {@link java.nio.BufferOverflowException} will be thrown.
   *
   * @param bytes The array of bytes to write.
   * @return The written buffer.
   * @throws java.nio.BufferOverflowException If the number of bytes exceeds the buffer's remaining
   *     bytes.
   * @see Buffer#write(byte[], int, int)
   * @see Buffer#write(int, byte[], int, int)
   */
  @Override
  Buffer write(byte[] bytes);

  /**
   * Writes the remaining bytes of the given buffer into this buffer, from the given buffer's {@link
   * ByteBuffer#position()} to its {@link ByteBuffer#limit()}
   *
   * <p>When the bytes are written to the buffer, the buffer's {@code position} will be advanced by
   * the number of bytes in the provided byte array. If the number of bytes exceeds {@link
   * Buffer#limit()} then an {@link java.nio.BufferOverflowException} will be thrown.
   *
   * @param bytes the bytes to write
   * @return the written buffer.
   * @throws java.nio.BufferOverflowException if the number of bytes exceeds the buffer's remaining
   *     bytes.
   * @see Buffer#write(int, ByteBuffer, int, int)
   */
  Buffer write(ByteBuffer bytes);

  /**
   * Writes an array of bytes to the buffer.
   *
   * <p>The bytes will be written starting at the current position up to the given length. If the
   * length of the byte array is larger than the provided {@code length} then only {@code length}
   * bytes will be read from the array. If the provided {@code length} is greater than the remaining
   * bytes in this buffer then a {@link java.nio.BufferOverflowException} will be thrown.
   *
   * @param bytes The array of bytes to write.
   * @param offset The offset at which to start writing the bytes.
   * @param length The number of bytes from the provided byte array to write to the buffer.
   * @return The written buffer.
   * @throws java.nio.BufferOverflowException If there are not enough bytes remaining in the buffer.
   * @throws IndexOutOfBoundsException If the given offset is out of the bounds of the buffer.
   * @see Buffer#write(Bytes)
   * @see Buffer#write(int, Bytes, int, int)
   */
  @Override
  Buffer write(Bytes bytes, int offset, int length);

  /**
   * Writes an array of bytes to the buffer.
   *
   * <p>The bytes will be written starting at the current position up to the given length. If the
   * length of the byte array is larger than the provided {@code length} then only {@code length}
   * bytes will be read from the array. If the provided {@code length} is greater than the remaining
   * bytes in this buffer then a {@link java.nio.BufferOverflowException} will be thrown.
   *
   * @param bytes The array of bytes to write.
   * @param offset The offset at which to start writing the bytes.
   * @param length The number of bytes from the provided byte array to write to the buffer.
   * @return The written buffer.
   * @throws java.nio.BufferOverflowException If there are not enough bytes remaining in the buffer.
   * @throws IndexOutOfBoundsException If the given offset is out of the bounds of the buffer.
   * @see Buffer#write(byte[])
   * @see Buffer#write(int, byte[], int, int)
   */
  @Override
  Buffer write(byte[] bytes, int offset, int length);

  /**
   * Writes a buffer to the buffer.
   *
   * <p>When the buffer is written to the buffer, the buffer's {@code position} will be advanced by
   * the number of bytes in the provided buffer. If the provided {@link Buffer#remaining()} exceeds
   * {@link Buffer#remaining()} then an {@link java.nio.BufferOverflowException} will be thrown.
   *
   * @param buffer The buffer to write.
   * @return The written buffer.
   * @throws java.nio.BufferOverflowException If the given buffer's {@link Buffer#remaining()} bytes
   *     exceeds this buffer's remaining bytes.
   */
  @Override
  Buffer write(Buffer buffer);

  /**
   * Writes a byte to the buffer at the current position.
   *
   * <p>When the byte is written to the buffer, the buffer's {@code position} will be advanced by
   * {@link Bytes#BYTE}. If there are no bytes remaining in the buffer then a {@link
   * java.nio.BufferOverflowException} will be thrown.
   *
   * @param b The byte to write.
   * @return The written buffer.
   * @throws java.nio.BufferOverflowException If there are no bytes remaining in the buffer.
   * @see Buffer#writeByte(int, int)
   */
  @Override
  Buffer writeByte(int b);

  /**
   * Writes an unsigned byte to the buffer at the current position.
   *
   * <p>When the byte is written to the buffer, the buffer's {@code position} will be advanced by
   * {@link Bytes#BYTE}. If there are no bytes remaining in the buffer then a {@link
   * java.nio.BufferOverflowException} will be thrown.
   *
   * @param b The byte to write.
   * @return The written buffer.
   * @throws java.nio.BufferOverflowException If there are no bytes remaining in the buffer.
   * @see Buffer#writeUnsignedByte(int, int)
   */
  @Override
  Buffer writeUnsignedByte(int b);

  /**
   * Writes a 16-bit character to the buffer at the current position.
   *
   * <p>When the character is written to the buffer, the buffer's {@code position} will be advanced
   * by {@link Bytes#CHARACTER}. If less than {@code 2} bytes are remaining in the buffer then a
   * {@link java.nio.BufferOverflowException} will be thrown.
   *
   * @param c The character to write.
   * @return The written buffer.
   * @throws java.nio.BufferOverflowException If {@link Buffer#remaining()} is less than {@link
   *     Bytes#CHARACTER}.
   * @see Buffer#writeChar(int, char)
   */
  @Override
  Buffer writeChar(char c);

  /**
   * Writes a 16-bit signed integer to the buffer at the current position.
   *
   * <p>When the short is written to the buffer, the buffer's {@code position} will be advanced by
   * {@link Bytes#SHORT}. If less than {@link Bytes#SHORT} bytes are remaining in the buffer then a
   * {@link java.nio.BufferOverflowException} will be thrown.
   *
   * @param s The short to write.
   * @return The written buffer.
   * @throws java.nio.BufferOverflowException If {@link Buffer#remaining()} is less than {@link
   *     Bytes#SHORT}.
   * @see Buffer#writeShort(int, short)
   */
  @Override
  Buffer writeShort(short s);

  /**
   * Writes a 16-bit signed integer to the buffer at the current position.
   *
   * <p>When the short is written to the buffer, the buffer's {@code position} will be advanced by
   * {@link Bytes#SHORT}. If less than {@link Bytes#SHORT} bytes are remaining in the buffer then a
   * {@link java.nio.BufferOverflowException} will be thrown.
   *
   * @param s The short to write.
   * @return The written buffer.
   * @throws java.nio.BufferOverflowException If {@link Buffer#remaining()} is less than {@link
   *     Bytes#SHORT}.
   * @see Buffer#writeUnsignedShort(int, int)
   */
  @Override
  Buffer writeUnsignedShort(int s);

  /**
   * Writes a 32-bit signed integer to the buffer at the current position.
   *
   * <p>When the integer is written to the buffer, the buffer's {@code position} will be advanced by
   * {@link Bytes#INTEGER}. If less than {@link Bytes#INTEGER} bytes are remaining in the buffer
   * then a {@link java.nio.BufferOverflowException} will be thrown.
   *
   * @param i The integer to write.
   * @return The written buffer.
   * @throws java.nio.BufferOverflowException If {@link Buffer#remaining()} is less than {@link
   *     Bytes#INTEGER}.
   * @see Buffer#writeInt(int, int)
   */
  @Override
  Buffer writeInt(int i);

  /**
   * Writes a 32-bit signed integer to the buffer at the current position.
   *
   * <p>When the integer is written to the buffer, the buffer's {@code position} will be advanced by
   * {@link Bytes#INTEGER}. If less than {@link Bytes#INTEGER} bytes are remaining in the buffer
   * then a {@link java.nio.BufferOverflowException} will be thrown.
   *
   * @param i The integer to write.
   * @return The written buffer.
   * @throws java.nio.BufferOverflowException If {@link Buffer#remaining()} is less than {@link
   *     Bytes#INTEGER}.
   * @see Buffer#writeUnsignedInt(int, long)
   */
  @Override
  Buffer writeUnsignedInt(long i);

  /**
   * Writes a 64-bit signed integer to the buffer at the current position.
   *
   * <p>When the long is written to the buffer, the buffer's {@code position} will be advanced by
   * {@link Bytes#LONG}. If less than {@link Bytes#LONG} bytes are remaining in the buffer then a
   * {@link java.nio.BufferOverflowException} will be thrown.
   *
   * @param l The long to write.
   * @return The written buffer.
   * @throws java.nio.BufferOverflowException If {@link Buffer#remaining()} is less than {@link
   *     Bytes#LONG}.
   * @see Buffer#writeLong(int, long)
   */
  @Override
  Buffer writeLong(long l);

  /**
   * Writes a single-precision 32-bit floating point number to the buffer at the current position.
   *
   * <p>When the float is written to the buffer, the buffer's {@code position} will be advanced by
   * {@link Bytes#FLOAT}. If less than {@link Bytes#FLOAT} bytes are remaining in the buffer then a
   * {@link java.nio.BufferOverflowException} will be thrown.
   *
   * @param f The float to write.
   * @return The written buffer.
   * @throws java.nio.BufferOverflowException If {@link Buffer#remaining()} is less than {@link
   *     Bytes#FLOAT}.
   * @see Buffer#writeFloat(int, float)
   */
  @Override
  Buffer writeFloat(float f);

  /**
   * Writes a double-precision 64-bit floating point number to the buffer at the current position.
   *
   * <p>When the double is written to the buffer, the buffer's {@code position} will be advanced by
   * {@link Bytes#DOUBLE}. If less than {@link Bytes#DOUBLE} bytes are remaining in the buffer then
   * a {@link java.nio.BufferOverflowException} will be thrown.
   *
   * @param d The double to write.
   * @return The written buffer.
   * @throws java.nio.BufferOverflowException If {@link Buffer#remaining()} is less than {@link
   *     Bytes#DOUBLE}.
   * @see Buffer#writeDouble(int, double)
   */
  @Override
  Buffer writeDouble(double d);

  /**
   * Writes a 1 byte boolean to the buffer at the current position.
   *
   * <p>When the boolean is written to the buffer, the buffer's {@code position} will be advanced by
   * {@code 1}. If there are no bytes remaining in the buffer then a {@link
   * java.nio.BufferOverflowException} will be thrown.
   *
   * @param b The boolean to write.
   * @return The written buffer.
   * @throws java.nio.BufferOverflowException If the number of bytes exceeds the buffer's remaining
   *     bytes.
   * @see Buffer#writeBoolean(int, boolean)
   */
  @Override
  Buffer writeBoolean(boolean b);

  /**
   * Writes a UTF-8 string to the buffer at the current position.
   *
   * <p>The string will be written with a two-byte unsigned byte length followed by the UTF-8 bytes.
   * If there are not enough bytes remaining in the buffer then a {@link
   * java.nio.BufferOverflowException} will be thrown.
   *
   * @param s The string to write.
   * @return The written buffer.
   * @throws java.nio.BufferOverflowException If the number of bytes exceeds the buffer's remaining
   *     bytes.
   * @see Buffer#writeUTF8(int, String)
   */
  @Override
  Buffer writeUTF8(String s);

  /**
   * Writes an array of bytes to the buffer.
   *
   * <p>The bytes will be written starting at the given offset up to the given length. If the
   * remaining bytes in the byte array is larger than the provided {@code length} then only {@code
   * length} bytes will be read from the array. If the provided {@code length} is greater than
   * {@link Buffer#limit()} minus {@code offset} then a {@link java.nio.BufferOverflowException}
   * will be thrown.
   *
   * @param offset The offset at which to start writing the bytes.
   * @param src The array of bytes to write.
   * @param srcOffset The offset at which to begin reading bytes from the source.
   * @param length The number of bytes from the provided byte array to write to the buffer.
   * @return The written buffer.
   * @throws java.nio.BufferOverflowException If there are not enough bytes remaining in the buffer.
   * @throws IndexOutOfBoundsException If the given offset is out of the bounds of the buffer.
   * @see Buffer#write(Bytes)
   * @see Buffer#write(Bytes, int, int)
   */
  @Override
  Buffer write(int offset, Bytes src, int srcOffset, int length);

  /**
   * Writes an array of bytes to the buffer.
   *
   * <p>The bytes will be written starting at the given offset up to the given length. If the
   * remaining bytes in the byte array is larger than the provided {@code length} then only {@code
   * length} bytes will be read from the array. If the provided {@code length} is greater than
   * {@link Buffer#limit()} minus {@code offset} then a {@link java.nio.BufferOverflowException}
   * will be thrown.
   *
   * @param offset The offset at which to start writing the bytes.
   * @param src The array of bytes to write.
   * @param srcOffset The offset at which to begin reading bytes from the source.
   * @param length The number of bytes from the provided byte array to write to the buffer.
   * @return The written buffer.
   * @throws java.nio.BufferOverflowException If there are not enough bytes remaining in the buffer.
   * @throws IndexOutOfBoundsException If the given offset is out of the bounds of the buffer.
   * @see Buffer#write(byte[])
   * @see Buffer#write(byte[], int, int)
   */
  @Override
  Buffer write(int offset, byte[] src, int srcOffset, int length);

  /**
   * Writes the {@code length} bytes from the {@code src}, starting at {@code srcOffset} into this
   * buffer.
   *
   * <p>The bytes will be written starting at the given offset up to the given length. If the
   * remaining bytes in the buffer is larger than the provided {@code length} then only {@code
   * length} bytes will be read from the array. If the provided {@code length} is greater than
   * {@link Buffer#limit()} minus {@code offset} then a {@link java.nio.BufferOverflowException}
   * will be thrown.
   *
   * @param offset The offset at which to start writing the bytes.
   * @param src The source buffer to write.
   * @param srcOffset The offset at which to begin reading bytes from the source.
   * @param length The number of bytes from the provided byte array to write to the buffer.
   * @return The written buffer.
   * @throws java.nio.BufferOverflowException If there are not enough bytes remaining in the buffer.
   * @throws IndexOutOfBoundsException If the given offset is out of the bounds of the buffer.
   * @see Buffer#write(ByteBuffer)
   */
  @Override
  Buffer write(int offset, ByteBuffer src, int srcOffset, int length);

  /**
   * Writes a byte to the buffer at the given offset.
   *
   * <p>The byte will be written at the given offset. If there are no bytes remaining in the buffer
   * then a {@link java.nio.BufferOverflowException} will be thrown.
   *
   * @param offset The offset at which to write the byte.
   * @param b The byte to write.
   * @return The written buffer.
   * @throws java.nio.BufferOverflowException If there are not enough bytes remaining in the buffer.
   * @throws IndexOutOfBoundsException If the given offset is out of the bounds of the buffer. Note
   *     that bounds are determined by the buffer's {@link Buffer#limit()} rather than capacity.
   * @see Buffer#writeByte(int)
   */
  @Override
  Buffer writeByte(int offset, int b);

  /**
   * Writes an unsigned byte to the buffer at the given offset.
   *
   * <p>The byte will be written at the given offset. If there are no bytes remaining in the buffer
   * then a {@link java.nio.BufferOverflowException} will be thrown.
   *
   * @param offset The offset at which to write the byte.
   * @param b The byte to write.
   * @return The written buffer.
   * @throws java.nio.BufferOverflowException If there are not enough bytes remaining in the buffer.
   * @throws IndexOutOfBoundsException If the given offset is out of the bounds of the buffer. Note
   *     that bounds are determined by the buffer's {@link Buffer#limit()} rather than capacity.
   * @see Buffer#writeUnsignedByte(int)
   */
  @Override
  Buffer writeUnsignedByte(int offset, int b);

  /**
   * Writes a 16-bit character to the buffer at the given offset.
   *
   * <p>The character will be written at the given offset. If there are less than {@link
   * Bytes#CHARACTER} bytes remaining in the buffer then a {@link java.nio.BufferOverflowException}
   * will be thrown.
   *
   * @param offset The offset at which to write the character.
   * @param c The character to write.
   * @return The written buffer.
   * @throws java.nio.BufferOverflowException If {@link Buffer#remaining()} is less than {@link
   *     Bytes#CHARACTER}.
   * @throws IndexOutOfBoundsException If the given offset is out of the bounds of the buffer. Note
   *     that bounds are determined by the buffer's {@link Buffer#limit()} rather than capacity.
   * @see Buffer#writeChar(char)
   */
  @Override
  Buffer writeChar(int offset, char c);

  /**
   * Writes a 16-bit signed integer to the buffer at the given offset.
   *
   * <p>The short will be written at the given offset. If there are less than {@link Bytes#SHORT}
   * bytes remaining in the buffer then a {@link java.nio.BufferOverflowException} will be thrown.
   *
   * @param offset The offset at which to write the short.
   * @param s The short to write.
   * @return The written buffer.
   * @throws java.nio.BufferOverflowException If {@link Buffer#remaining()} is less than {@link
   *     Bytes#SHORT}.
   * @throws IndexOutOfBoundsException If the given offset is out of the bounds of the buffer. Note
   *     that bounds are determined by the buffer's {@link Buffer#limit()} rather than capacity.
   * @see Buffer#writeShort(short)
   */
  @Override
  Buffer writeShort(int offset, short s);

  /**
   * Writes a 16-bit signed integer to the buffer at the given offset.
   *
   * <p>The short will be written at the given offset. If there are less than {@link Bytes#SHORT}
   * bytes remaining in the buffer then a {@link java.nio.BufferOverflowException} will be thrown.
   *
   * @param offset The offset at which to write the short.
   * @param s The short to write.
   * @return The written buffer.
   * @throws java.nio.BufferOverflowException If {@link Buffer#remaining()} is less than {@link
   *     Bytes#SHORT}.
   * @throws IndexOutOfBoundsException If the given offset is out of the bounds of the buffer. Note
   *     that bounds are determined by the buffer's {@link Buffer#limit()} rather than capacity.
   * @see Buffer#writeUnsignedShort(int)
   */
  @Override
  Buffer writeUnsignedShort(int offset, int s);

  /**
   * Writes a 32-bit signed integer to the buffer at the given offset.
   *
   * <p>The integer will be written at the given offset. If there are less than {@link
   * Bytes#INTEGER} bytes remaining in the buffer then a {@link java.nio.BufferOverflowException}
   * will be thrown.
   *
   * @param offset The offset at which to write the integer.
   * @param i The integer to write.
   * @return The written buffer.
   * @throws java.nio.BufferOverflowException If {@link Buffer#remaining()} is less than {@link
   *     Bytes#INTEGER}.
   * @throws IndexOutOfBoundsException If the given offset is out of the bounds of the buffer. Note
   *     that bounds are determined by the buffer's {@link Buffer#limit()} rather than capacity.
   * @see Buffer#writeInt(int)
   */
  @Override
  Buffer writeInt(int offset, int i);

  /**
   * Writes a 32-bit signed integer to the buffer at the given offset.
   *
   * <p>The integer will be written at the given offset. If there are less than {@link
   * Bytes#INTEGER} bytes remaining in the buffer then a {@link java.nio.BufferOverflowException}
   * will be thrown.
   *
   * @param offset The offset at which to write the integer.
   * @param i The integer to write.
   * @return The written buffer.
   * @throws java.nio.BufferOverflowException If {@link Buffer#remaining()} is less than {@link
   *     Bytes#INTEGER}.
   * @throws IndexOutOfBoundsException If the given offset is out of the bounds of the buffer. Note
   *     that bounds are determined by the buffer's {@link Buffer#limit()} rather than capacity.
   * @see Buffer#writeUnsignedInt(long)
   */
  @Override
  Buffer writeUnsignedInt(int offset, long i);

  /**
   * Writes a 64-bit signed integer to the buffer at the given offset.
   *
   * <p>The long will be written at the given offset. If there are less than {@link Bytes#LONG}
   * bytes remaining in the buffer then a {@link java.nio.BufferOverflowException} will be thrown.
   *
   * @param offset The offset at which to write the long.
   * @param l The long to write.
   * @return The written buffer.
   * @throws java.nio.BufferOverflowException If {@link Buffer#remaining()} is less than {@link
   *     Bytes#LONG}.
   * @throws IndexOutOfBoundsException If the given offset is out of the bounds of the buffer. Note
   *     that bounds are determined by the buffer's {@link Buffer#limit()} rather than capacity.
   * @see Buffer#writeLong(long)
   */
  @Override
  Buffer writeLong(int offset, long l);

  /**
   * Writes a single-precision 32-bit floating point number to the buffer at the given offset.
   *
   * <p>The float will be written at the given offset. If there are less than {@link Bytes#FLOAT}
   * bytes remaining in the buffer then a {@link java.nio.BufferOverflowException} will be thrown.
   *
   * @param offset The offset at which to write the float.
   * @param f The float to write.
   * @return The written buffer.
   * @throws java.nio.BufferOverflowException If {@link Buffer#remaining()} is less than {@link
   *     Bytes#FLOAT}.
   * @throws IndexOutOfBoundsException If the given offset is out of the bounds of the buffer. Note
   *     that bounds are determined by the buffer's {@link Buffer#limit()} rather than capacity.
   * @see Buffer#writeFloat(float)
   */
  @Override
  Buffer writeFloat(int offset, float f);

  /**
   * Writes a double-precision 64-bit floating point number to the buffer at the given offset.
   *
   * <p>The double will be written at the given offset. If there are less than {@link Bytes#DOUBLE}
   * bytes remaining in the buffer then a {@link java.nio.BufferOverflowException} will be thrown.
   *
   * @param offset The offset at which to write the double.
   * @param d The double to write.
   * @return The written buffer.
   * @throws java.nio.BufferOverflowException If {@link Buffer#remaining()} is less than {@link
   *     Bytes#DOUBLE}.
   * @throws IndexOutOfBoundsException If the given offset is out of the bounds of the buffer. Note
   *     that bounds are determined by the buffer's {@link Buffer#limit()} rather than capacity.
   * @see Buffer#writeDouble(double)
   */
  @Override
  Buffer writeDouble(int offset, double d);

  /**
   * Writes a 1 byte boolean to the buffer at the given offset.
   *
   * <p>The boolean will be written as a single byte at the given offset. If there are no bytes
   * remaining in the buffer then a {@link java.nio.BufferOverflowException} will be thrown.
   *
   * @param offset The offset at which to write the boolean.
   * @param b The boolean to write.
   * @return The written buffer.
   * @throws java.nio.BufferOverflowException If {@link Buffer#remaining()} is less than {@code 1}.
   * @throws IndexOutOfBoundsException If the given offset is out of the bounds of the buffer. Note
   *     that bounds are determined by the buffer's {@link Buffer#limit()} rather than capacity.
   * @see Buffer#writeBoolean(boolean)
   */
  @Override
  Buffer writeBoolean(int offset, boolean b);

  /**
   * Writes a UTF-8 string to the buffer at the given offset.
   *
   * <p>The string will be written with a two-byte unsigned byte length followed by the UTF-8 bytes.
   * If there are not enough bytes remaining in the buffer then a {@link
   * java.nio.BufferOverflowException} will be thrown.
   *
   * @param offset The offset at which to write the string.
   * @param s The string to write.
   * @return The written buffer.
   * @throws java.nio.BufferOverflowException If {@link Buffer#remaining()} is less than {@code 1}.
   * @throws IndexOutOfBoundsException If the given offset is out of the bounds of the buffer. Note
   *     that bounds are determined by the buffer's {@link Buffer#limit()} rather than capacity.
   * @see Buffer#writeUTF8(String)
   */
  @Override
  Buffer writeUTF8(int offset, String s);
}
