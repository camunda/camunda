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
 * Writable bytes.
 *
 * <p>This interface exposes methods for writing bytes to specific positions in a byte array.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
public interface BytesOutput<T extends BytesOutput<T>> {

  /**
   * Zeros out all bytes in the array.
   *
   * @return The written bytes.
   */
  T zero();

  /**
   * Zeros out all bytes starting at the given offset in the array.
   *
   * @param offset The offset at which to start zeroing out bytes.
   * @return The written bytes.
   */
  T zero(int offset);

  /**
   * Zeros out bytes starting at the given offset up to the given length.
   *
   * @param offset The offset at which to start zeroing out bytes.
   * @param length THe total number of bytes to zero out.
   * @return The written bytes.
   */
  T zero(int offset, int length);

  /**
   * Writes an array of bytes to the buffer.
   *
   * @param offset The offset at which to start writing the bytes.
   * @param src The array of bytes to write.
   * @param srcOffset The offset at which to start reading bytes from the given source.
   * @param length The number of bytes from the provided byte array to write to the buffer.
   * @return The written buffer.
   */
  T write(int offset, Bytes src, int srcOffset, int length);

  /**
   * Writes an array of bytes to the buffer.
   *
   * @param offset The offset at which to start writing the bytes.
   * @param src The array of bytes to write.
   * @param srcOffset The offset at which to start reading bytes from the given source.
   * @param length The number of bytes from the provided byte array to write to the buffer.
   * @return The written buffer.
   */
  T write(int offset, byte[] src, int srcOffset, int length);

  /**
   * Writes an array of bytes to the buffer.
   *
   * @param offset The offset at which to start writing the bytes.
   * @param src The byte buffer to write.
   * @param srcOffset The offset at which to start reading bytes from the given source.
   * @param length The number of bytes from the provided byte array to write to the buffer.
   * @return The written buffer.
   */
  T write(int offset, ByteBuffer src, int srcOffset, int length);

  /**
   * Writes a byte to the buffer at the given offset.
   *
   * @param offset The offset at which to write the byte.
   * @param b The byte to write.
   * @return The written buffer.
   */
  T writeByte(int offset, int b);

  /**
   * Writes an unsigned byte to the buffer at the given position.
   *
   * @param offset The offset at which to write the byte.
   * @param b The byte to write.
   * @return The written buffer.
   */
  T writeUnsignedByte(int offset, int b);

  /**
   * Writes a 16-bit character to the buffer at the given offset.
   *
   * @param offset The offset at which to write the character.
   * @param c The character to write.
   * @return The written buffer.
   */
  T writeChar(int offset, char c);

  /**
   * Writes a 16-bit signed integer to the buffer at the given offset.
   *
   * @param offset The offset at which to write the short.
   * @param s The short to write.
   * @return The written buffer.
   */
  T writeShort(int offset, short s);

  /**
   * Writes a 16-bit unsigned integer to the buffer at the given offset.
   *
   * @param offset The offset at which to write the short.
   * @param s The short to write.
   * @return The written buffer.
   */
  T writeUnsignedShort(int offset, int s);

  /**
   * Writes a 24-bit signed integer to the buffer at the given offset.
   *
   * @param offset The offset at which to write the short.
   * @param m The short to write.
   * @return The written buffer.
   */
  T writeMedium(int offset, int m);

  /**
   * Writes a 24-bit unsigned integer to the buffer at the given offset.
   *
   * @param offset The offset at which to write the short.
   * @param m The short to write.
   * @return The written buffer.
   */
  T writeUnsignedMedium(int offset, int m);

  /**
   * Writes a 32-bit signed integer to the buffer at the given offset.
   *
   * @param offset The offset at which to write the integer.
   * @param i The integer to write.
   * @return The written buffer.
   */
  T writeInt(int offset, int i);

  /**
   * Writes a 32-bit unsigned integer to the buffer at the given offset.
   *
   * @param offset The offset at which to write the integer.
   * @param i The integer to write.
   * @return The written buffer.
   */
  T writeUnsignedInt(int offset, long i);

  /**
   * Writes a 64-bit signed integer to the buffer at the given offset.
   *
   * @param offset The offset at which to write the long.
   * @param l The long to write.
   * @return The written buffer.
   */
  T writeLong(int offset, long l);

  /**
   * Writes a single-precision 32-bit floating point number to the buffer at the given offset.
   *
   * @param offset The offset at which to write the float.
   * @param f The float to write.
   * @return The written buffer.
   */
  T writeFloat(int offset, float f);

  /**
   * Writes a double-precision 64-bit floating point number to the buffer at the given offset.
   *
   * @param offset The offset at which to write the double.
   * @param d The double to write.
   * @return The written buffer.
   */
  T writeDouble(int offset, double d);

  /**
   * Writes a 1 byte boolean to the buffer at the given offset.
   *
   * @param offset The offset at which to write the boolean.
   * @param b The boolean to write.
   * @return The written buffer.
   */
  T writeBoolean(int offset, boolean b);

  /**
   * Writes a string to the buffer at the given offset.
   *
   * @param offset The offset at which to write the string.
   * @param s The string to write.
   * @return The written buffer.
   */
  T writeString(int offset, String s);

  /**
   * Writes a string to the buffer at the given offset.
   *
   * @param offset The offset at which to write the string.
   * @param s The string to write.
   * @param charset The character set with which to encode the string.
   * @return The written buffer.
   */
  T writeString(int offset, String s, Charset charset);

  /**
   * Writes a UTF-8 string to the buffer at the given offset.
   *
   * @param offset The offset at which to write the string.
   * @param s The string to write.
   * @return The written buffer.
   */
  T writeUTF8(int offset, String s);

  /**
   * Flushes the bytes to the underlying persistence layer.
   *
   * @return The flushed buffer.
   */
  T flush();
}
