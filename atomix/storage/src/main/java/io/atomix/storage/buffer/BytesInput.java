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

/**
 * Readable bytes.
 *
 * <p>This interface exposes methods for reading bytes from specific positions in a byte array.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
public interface BytesInput<T extends BytesInput<T>> {

  /**
   * Reads bytes into the given byte array starting at the given offset up to the given length.
   *
   * @param offset The offset from which to start reading bytes.
   * @param dst The byte array into which to read bytes.
   * @param dstOffset The offset at which to write bytes into the given buffer.
   * @param length The total number of bytes to read.
   * @return The buffer.
   */
  T read(int offset, Bytes dst, int dstOffset, int length);

  /**
   * Reads bytes into the given byte array starting at the given offset up to the given length.
   *
   * @param offset The offset from which to start reading bytes.
   * @param dst The byte array into which to read bytes.
   * @param dstOffset The offset at which to write bytes into the given buffer
   * @param length The total number of bytes to read.
   * @return The buffer.
   */
  T read(int offset, byte[] dst, int dstOffset, int length);

  /**
   * Reads bytes into the given byte buffer starting at the given offset up to the given length.
   *
   * @param offset The offset from which to start reading bytes.
   * @param dst The byte buffer into which to read bytes.
   * @param dstOffset The offset at which to write bytes into the given buffer
   * @param length The total number of bytes to read.
   * @return the input buffer
   */
  T read(int offset, ByteBuffer dst, int dstOffset, int length);

  /**
   * Reads a byte from the buffer at the given offset.
   *
   * @param offset The offset at which to read the byte.
   * @return The read byte.
   */
  int readByte(int offset);

  /**
   * Reads an unsigned byte from the buffer at the given offset.
   *
   * @param offset The offset at which to read the byte.
   * @return The read unsigned byte.
   */
  int readUnsignedByte(int offset);

  /**
   * Reads a 16-bit character from the buffer at the given offset.
   *
   * @param offset The offset at which to read the character.
   * @return The read character.
   */
  char readChar(int offset);

  /**
   * Reads a 16-bit signed integer from the buffer at the given offset.
   *
   * @param offset The offset at which to read the short.
   * @return The read short.
   */
  short readShort(int offset);

  /**
   * Reads a 16-bit unsigned integer from the buffer at the given offset.
   *
   * @param offset The offset at which to read the short.
   * @return The read short.
   */
  int readUnsignedShort(int offset);

  /**
   * Reads a 24-bit signed integer from the buffer at the given offset.
   *
   * @param offset The offset at which to read the integer.
   * @return The read medium.
   */
  int readMedium(int offset);

  /**
   * Reads a 24-bin unsigned integer from the buffer at the given offset.
   *
   * @param offset The offset at which to read the integer.
   * @return The read medium.
   */
  int readUnsignedMedium(int offset);

  /**
   * Reads a 32-bit signed integer from the buffer at the given offset.
   *
   * @param offset The offset at which to read the integer.
   * @return The read integer.
   */
  int readInt(int offset);

  /**
   * Reads a 32-bit unsigned integer from the buffer at the given offset.
   *
   * @param offset The offset at which to read the integer.
   * @return The read integer.
   */
  long readUnsignedInt(int offset);

  /**
   * Reads a 64-bit signed integer from the buffer at the given offset.
   *
   * @param offset The offset at which to read the long.
   * @return The read long.
   */
  long readLong(int offset);

  /**
   * Reads a single-precision 32-bit floating point number from the buffer at the given offset.
   *
   * @param offset The offset at which to read the float.
   * @return The read float.
   */
  float readFloat(int offset);

  /**
   * Reads a double-precision 64-bit floating point number from the buffer at the given offset.
   *
   * @param offset The offset at which to read the double.
   * @return The read double.
   */
  double readDouble(int offset);

  /**
   * Reads a 1 byte boolean from the buffer at the given offset.
   *
   * @param offset The offset at which to read the boolean.
   * @return The read boolean.
   */
  boolean readBoolean(int offset);

  /**
   * Reads a string from the buffer at the given offset.
   *
   * @param offset The offset at which to read the string.
   * @return The read string.
   */
  String readString(int offset);

  /**
   * Reads a string from the buffer at the given offset.
   *
   * @param offset The offset at which to read the string.
   * @param charset The character set with which to decode the string.
   * @return The read string.
   */
  String readString(int offset, Charset charset);

  /**
   * Reads a UTF-8 string from the buffer at the given offset.
   *
   * @param offset The offset at which to read the string.
   * @return The read string.
   */
  String readUTF8(int offset);
}
