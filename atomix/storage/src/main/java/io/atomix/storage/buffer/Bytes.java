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

import java.nio.ByteOrder;

/**
 * Common interface for interacting with a memory or disk based array of bytes.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
public interface Bytes extends BytesInput<Bytes>, BytesOutput<Bytes>, AutoCloseable {
  int BYTE = 1;
  int BOOLEAN = 1;
  int CHARACTER = 2;
  int SHORT = 2;
  int MEDIUM = 3;
  int INTEGER = 4;
  int LONG = 8;
  int FLOAT = 4;
  int DOUBLE = 8;

  /**
   * Returns whether the bytes has an array.
   *
   * @return Whether the bytes has an underlying array.
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
   * Returns the count of the bytes.
   *
   * @return The count of the bytes.
   */
  int size();

  /**
   * Resizes the bytes.
   *
   * <p>When the bytes are resized, underlying memory addresses in copies of this instance may no
   * longer be valid. Additionally, if the {@code newSize} is smaller than the current {@code count}
   * then some data may be lost during the resize. Use with caution.
   *
   * @param newSize The count to which to resize this instance.
   * @return The resized bytes.
   */
  Bytes resize(int newSize);

  /**
   * Returns the byte order.
   *
   * <p>For consistency with {@link java.nio.ByteBuffer}, all bytes implementations are initially in
   * {@link ByteOrder#BIG_ENDIAN} order.
   *
   * @return The byte order.
   */
  ByteOrder order();

  /**
   * Sets the byte order, returning a new swapped {@link Bytes} instance.
   *
   * <p>By default, all bytes are read and written in {@link ByteOrder#BIG_ENDIAN} order. This
   * provides complete consistency with {@link java.nio.ByteBuffer}. To flip bytes to {@link
   * ByteOrder#LITTLE_ENDIAN} order, this {@code Bytes} instance is decorated by a {@link
   * SwappedBytes} instance which will reverse read and written bytes using, e.g. {@link
   * Integer#reverseBytes(int)}.
   *
   * @param order The byte order.
   * @return The updated bytes.
   * @throws NullPointerException If the {@code order} is {@code null}
   */
  Bytes order(ByteOrder order);

  /**
   * Returns a boolean value indicating whether the bytes are direct.
   *
   * @return Indicates whether the bytes are direct.
   */
  boolean isDirect();

  /**
   * Returns a boolean value indicating whether the bytes are backed by a file.
   *
   * @return Indicates whether the bytes are backed by a file.
   */
  boolean isFile();

  @Override
  void close();
}
