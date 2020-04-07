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
 * Writable buffer.
 *
 * <p>This interface exposes methods for writing to a byte buffer. Writable buffers maintain a small
 * amount of state regarding current cursor positions and limits similar to the behavior of {@link
 * java.nio.ByteBuffer}.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
public interface BufferOutput<T extends BufferOutput<?>> extends AutoCloseable {

  /**
   * Writes an array of bytes to the buffer.
   *
   * @param bytes The array of bytes to write.
   * @return The written buffer.
   */
  T write(Bytes bytes);

  /**
   * Writes an array of bytes to the buffer.
   *
   * @param bytes The array of bytes to write.
   * @return The written buffer.
   */
  T write(byte[] bytes);

  /**
   * Writes bytes to the buffer.
   *
   * @param bytes the bytes to write
   * @return the written buffer
   */
  T write(ByteBuffer bytes);

  /**
   * Writes the given bytes at offset {@code offset} up to a given {@code length}.
   *
   * <p>Note that this should NOT mutate the given {@link ByteBuffer}.
   *
   * @param source the bytes to write
   * @param offset the offset in the destination buffer to write to
   * @param length the maximum number of bytes to write from
   * @return the written buffer
   */
  T write(int offset, ByteBuffer source, int srcOffset, int length);

  /**
   * Writes an array of bytes to the buffer.
   *
   * @param bytes The array of bytes to write.
   * @param offset The offset at which to start writing the bytes.
   * @param length The number of bytes from the provided byte array to write to the buffer.
   * @return The written buffer.
   */
  T write(Bytes bytes, int offset, int length);

  /**
   * Writes an array of bytes to the buffer.
   *
   * @param bytes The array of bytes to write.
   * @param offset The offset at which to start writing the bytes.
   * @param length The number of bytes from the provided byte array to write to the buffer.
   * @return The written buffer.
   */
  T write(byte[] bytes, int offset, int length);

  /**
   * Writes a buffer to the buffer.
   *
   * @param buffer The buffer to write.
   * @return The written buffer.
   */
  T write(Buffer buffer);

  /**
   * Writes an object to the snapshot.
   *
   * @param object the object to write
   * @param encoder the object encoder
   * @return The snapshot writer.
   */
  @SuppressWarnings("unchecked")
  default <U> T writeObject(final U object, final Function<U, byte[]> encoder) {
    final byte[] bytes = encoder.apply(object);
    writeInt(bytes.length).write(bytes);
    return (T) this;
  }

  /**
   * Writes a byte array.
   *
   * @param bytes The byte array to write.
   * @return The written buffer.
   */
  @SuppressWarnings("unchecked")
  default T writeBytes(final byte[] bytes) {
    write(bytes);
    return (T) this;
  }

  /**
   * Writes a byte to the buffer.
   *
   * @param b The byte to write.
   * @return The written buffer.
   */
  T writeByte(int b);

  /**
   * Writes an unsigned byte to the buffer.
   *
   * @param b The byte to write.
   * @return The written buffer.
   */
  T writeUnsignedByte(int b);

  /**
   * Writes a 16-bit character to the buffer.
   *
   * @param c The character to write.
   * @return The written buffer.
   */
  T writeChar(char c);

  /**
   * Writes a 16-bit signed integer to the buffer.
   *
   * @param s The short to write.
   * @return The written buffer.
   */
  T writeShort(short s);

  /**
   * Writes a 16-bit unsigned integer to the buffer.
   *
   * @param s The short to write.
   * @return The written buffer.
   */
  T writeUnsignedShort(int s);

  /**
   * Writes a 24-bit signed integer to the buffer.
   *
   * @param m The integer to write.
   * @return The written buffer.
   */
  T writeMedium(int m);

  /**
   * Writes a 24-bit unsigned integer to the buffer.
   *
   * @param m The integer to write.
   * @return The written buffer.
   */
  T writeUnsignedMedium(int m);

  /**
   * Writes a 32-bit signed integer to the buffer.
   *
   * @param i The integer to write.
   * @return The written buffer.
   */
  T writeInt(int i);

  /**
   * Writes a 32-bit unsigned integer to the buffer.
   *
   * @param i The integer to write.
   * @return The written buffer.
   */
  T writeUnsignedInt(long i);

  /**
   * Writes a 64-bit signed integer to the buffer.
   *
   * @param l The long to write.
   * @return The written buffer.
   */
  T writeLong(long l);

  /**
   * Writes a single-precision 32-bit floating point number to the buffer.
   *
   * @param f The float to write.
   * @return The written buffer.
   */
  T writeFloat(float f);

  /**
   * Writes a double-precision 64-bit floating point number to the buffer.
   *
   * @param d The double to write.
   * @return The written buffer.
   */
  T writeDouble(double d);

  /**
   * Writes a 1 byte boolean to the buffer.
   *
   * @param b The boolean to write.
   * @return The written buffer.
   */
  T writeBoolean(boolean b);

  /**
   * Writes a string to the buffer.
   *
   * @param s The string to write.
   * @return The written buffer.
   */
  T writeString(String s);

  /**
   * Writes a string to the buffer.
   *
   * @param s The string to write.
   * @param charset The character set with which to encode the string.
   * @return The written buffer.
   */
  T writeString(String s, Charset charset);

  /**
   * Writes a UTF-8 string to the buffer.
   *
   * @param s The string to write.
   * @return The written buffer.
   */
  T writeUTF8(String s);

  /**
   * Flushes the buffer to the underlying persistence layer.
   *
   * @return The flushed buffer.
   */
  T flush();

  @Override
  void close();
}
