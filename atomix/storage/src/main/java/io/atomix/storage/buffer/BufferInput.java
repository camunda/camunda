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
import java.nio.charset.Charset;
import java.util.function.Function;

/**
 * Readable buffer.
 *
 * <p>This interface exposes methods for reading from a byte buffer. Readable buffers maintain a
 * small amount of state regarding current cursor positions and limits similar to the behavior of
 * {@link java.nio.ByteBuffer}.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
public interface BufferInput<T extends BufferInput<?>> extends AutoCloseable {

  /**
   * Returns the buffer's current read/write position.
   *
   * <p>The position is an internal cursor that tracks where to write/read bytes in the underlying
   * storage implementation. As bytes are written to or read from the buffer, the position will
   * advance based on the number of bytes read.
   *
   * @return The buffer's current position.
   */
  int position();

  /**
   * Returns the number of bytes remaining in the input.
   *
   * @return The number of bytes remaining in the input.
   */
  int remaining();

  /**
   * Returns a boolean value indicating whether the input has bytes remaining.
   *
   * @return Indicates whether bytes remain to be read from the input.
   */
  boolean hasRemaining();

  /**
   * Skips the given number of bytes in the input.
   *
   * @param bytes The number of bytes to attempt to skip.
   * @return The skipped input.
   */
  T skip(int bytes);

  /**
   * Reads bytes into the given byte array.
   *
   * @param bytes The byte array into which to read bytes.
   * @return The buffer.
   */
  T read(Bytes bytes);

  /**
   * Reads bytes into the given byte array.
   *
   * @param bytes The byte array into which to read bytes.
   * @return The buffer.
   */
  T read(byte[] bytes);

  /**
   * Reads bytes into the given byte array starting at the current position.
   *
   * @param bytes The byte array into which to read bytes.
   * @param offset The offset at which to write bytes into the given buffer
   * @return The buffer.
   */
  T read(Bytes bytes, int offset, int length);

  /**
   * Reads bytes into the given byte array starting at current position up to the given length.
   *
   * @param bytes The byte array into which to read bytes.
   * @param offset The offset at which to write bytes into the given buffer
   * @return The buffer.
   */
  T read(byte[] bytes, int offset, int length);

  /**
   * Reads bytes into the given buffer.
   *
   * @param buffer The buffer into which to read bytes.
   * @return The buffer.
   */
  T read(Buffer buffer);

  /**
   * Reads bytes into the given buffer; it will start writing at the current position and will
   * advance the position, mutating this buffer.
   *
   * @param buffer the destination buffer
   * @return the buffer input
   */
  T read(ByteBuffer buffer);

  /**
   * Reads an object from the buffer.
   *
   * @param decoder the object decoder
   * @param <U> the type of the object to read
   * @return the read object.
   */
  default <U> U readObject(final Function<byte[], U> decoder) {
    final byte[] bytes = readBytes(readInt());
    return decoder.apply(bytes);
  }

  /**
   * Reads a byte array.
   *
   * @param length The byte array length
   * @return The read byte array.
   */
  default byte[] readBytes(final int length) {
    final byte[] bytes = new byte[length];
    read(bytes);
    return bytes;
  }

  /**
   * Reads a byte from the buffer at the current position.
   *
   * @return The read byte.
   */
  int readByte();

  /**
   * Reads an unsigned byte from the buffer at the current position.
   *
   * @return The read byte.
   */
  int readUnsignedByte();

  /**
   * Reads a 16-bit character from the buffer at the current position.
   *
   * @return The read character.
   */
  char readChar();

  /**
   * Reads a 16-bit signed integer from the buffer at the current position.
   *
   * @return The read short.
   */
  short readShort();

  /**
   * Reads a 16-bit unsigned integer from the buffer at the current position.
   *
   * @return The read short.
   */
  int readUnsignedShort();

  /**
   * Reads a 24-bit signed integer from the buffer at the current position.
   *
   * @return The read integer.
   */
  int readMedium();

  /**
   * Reads a 24-bit unsigned integer from the buffer at the current position.
   *
   * @return The read integer.
   */
  int readUnsignedMedium();

  /**
   * Reads a 32-bit signed integer from the buffer at the current position.
   *
   * @return The read integer.
   */
  int readInt();

  /**
   * Reads a 32-bit unsigned integer from the buffer at the current position.
   *
   * @return The read integer.
   */
  long readUnsignedInt();

  /**
   * Reads a 64-bit signed integer from the buffer at the current position.
   *
   * @return The read long.
   */
  long readLong();

  /**
   * Reads a single-precision 32-bit floating point number from the buffer at the current position.
   *
   * @return The read float.
   */
  float readFloat();

  /**
   * Reads a double-precision 64-bit floating point number from the buffer at the current position.
   *
   * @return The read double.
   */
  double readDouble();

  /**
   * Reads a 1 byte boolean from the buffer at the current position.
   *
   * @return The read boolean.
   */
  boolean readBoolean();

  /**
   * Reads a string from the buffer at the current position.
   *
   * @return The read string.
   */
  String readString();

  /**
   * Reads a string from the buffer at the current position.
   *
   * @param charset The character set with which to decode the string.
   * @return The read string.
   */
  String readString(Charset charset);

  /**
   * Reads a UTF-8 string from the buffer at the current position.
   *
   * @return The read string.
   */
  String readUTF8();

  @Override
  void close();
}
