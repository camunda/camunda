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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.nio.channels.FileChannel;

/**
 * Direct {@link java.nio.ByteBuffer} based buffer.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
public class MappedBuffer extends ByteBufferBuffer {

  protected MappedBuffer(
      final MappedBytes bytes, final int offset, final int initialCapacity, final int maxCapacity) {
    super(bytes, offset, initialCapacity, maxCapacity, null);
  }

  /**
   * Allocates a dynamic capacity mapped buffer in {@link FileChannel.MapMode#READ_WRITE} mode with
   * an initial capacity of {@code 16MiB} and a maximum capacity of {@link Integer#MAX_VALUE}.
   *
   * <p>The resulting buffer will have a maximum capacity of {@link Integer#MAX_VALUE}. As bytes are
   * written to the buffer its capacity will double in count each time the current capacity is
   * reached. Memory will be mapped by opening and expanding the given {@link File} to the desired
   * {@code capacity} and mapping the file contents into memory via {@link
   * FileChannel#map(FileChannel.MapMode, long, long)}.
   *
   * @param file The file to map into memory.
   * @return The mapped buffer.
   * @throws NullPointerException If {@code file} is {@code null}
   * @see #allocate(File, FileChannel.MapMode)
   * @see #allocate(File, int)
   * @see #allocate(File, FileChannel.MapMode, int)
   * @see #allocate(File, int, int)
   * @see #allocate(File, FileChannel.MapMode, int, int)
   */
  public static MappedBuffer allocate(final File file) {
    return allocate(
        file, FileChannel.MapMode.READ_WRITE, DEFAULT_INITIAL_CAPACITY, Integer.MAX_VALUE);
  }

  /**
   * Allocates a dynamic capacity mapped buffer in {@link FileChannel.MapMode#READ_WRITE} mode with
   * an initial capacity of {@code 16MiB} and a maximum capacity of {@link Integer#MAX_VALUE}.
   *
   * <p>The resulting buffer will be initialized to a capacity of {@code 4096} and have a maximum
   * capacity of {@link Integer#MAX_VALUE}. As bytes are written to the buffer its capacity will
   * double in count each time the current capacity is reached. Memory will be mapped by opening and
   * expanding the given {@link File} to the desired {@code capacity} and mapping the file contents
   * into memory via {@link FileChannel#map(FileChannel.MapMode, long, long)}.
   *
   * @param file The file to map into memory.
   * @param mode The mode with which to map the file.
   * @return The mapped buffer.
   * @throws NullPointerException If {@code file} is {@code null}
   * @see #allocate(File)
   * @see #allocate(File, int)
   * @see #allocate(File, FileChannel.MapMode, int)
   * @see #allocate(File, int, int)
   * @see #allocate(File, FileChannel.MapMode, int, int)
   */
  public static MappedBuffer allocate(final File file, final FileChannel.MapMode mode) {
    return allocate(file, mode, DEFAULT_INITIAL_CAPACITY, Integer.MAX_VALUE);
  }

  /**
   * Allocates a fixed capacity mapped buffer in {@link FileChannel.MapMode#READ_WRITE} mode.
   *
   * <p>Memory will be mapped by opening and expanding the given {@link File} to the desired {@code
   * capacity} and mapping the file contents into memory via {@link
   * FileChannel#map(FileChannel.MapMode, long, long)}.
   *
   * <p>The resulting buffer will have a capacity of {@code capacity}. The underlying {@link
   * MappedBytes} will be initialized to the next power of {@code 2}.
   *
   * @param file The file to map into memory.
   * @param capacity The fixed capacity of the buffer to allocate (in bytes).
   * @return The mapped buffer.
   * @throws NullPointerException If {@code file} is {@code null}
   * @throws IllegalArgumentException If the {@code capacity} is greater than {@link
   *     Integer#MAX_VALUE}.
   * @see #allocate(File)
   * @see #allocate(File, FileChannel.MapMode)
   * @see #allocate(File, FileChannel.MapMode, int)
   * @see #allocate(File, int, int)
   * @see #allocate(File, FileChannel.MapMode, int, int)
   */
  public static MappedBuffer allocate(final File file, final int capacity) {
    return allocate(file, FileChannel.MapMode.READ_WRITE, capacity, capacity);
  }

  /**
   * Allocates a fixed capacity mapped buffer in the given {@link FileChannel.MapMode}.
   *
   * <p>Memory will be mapped by opening and expanding the given {@link File} to the desired {@code
   * capacity} and mapping the file contents into memory via {@link
   * FileChannel#map(FileChannel.MapMode, long, long)}.
   *
   * <p>The resulting buffer will have a capacity of {@code capacity}. The underlying {@link
   * MappedBytes} will be initialized to the next power of {@code 2}.
   *
   * @param file The file to map into memory.
   * @param mode The mode with which to map the file.
   * @param capacity The fixed capacity of the buffer to allocate (in bytes).
   * @return The mapped buffer.
   * @throws NullPointerException If {@code file} is {@code null}
   * @throws IllegalArgumentException If the {@code capacity} is greater than {@link
   *     Integer#MAX_VALUE}.
   * @see #allocate(File)
   * @see #allocate(File, FileChannel.MapMode)
   * @see #allocate(File, int)
   * @see #allocate(File, int, int)
   * @see #allocate(File, FileChannel.MapMode, int, int)
   */
  public static MappedBuffer allocate(
      final File file, final FileChannel.MapMode mode, final int capacity) {
    return allocate(file, mode, capacity, capacity);
  }

  /**
   * Allocates a mapped buffer.
   *
   * <p>Memory will be mapped by opening and expanding the given {@link File} to the desired {@code
   * count} and mapping the file contents into memory via {@link
   * FileChannel#map(FileChannel.MapMode, long, long)}.
   *
   * <p>The resulting buffer will have a capacity of {@code initialCapacity}. The underlying {@link
   * MappedBytes} will be initialized to the next power of {@code 2}. As bytes are written to the
   * buffer, the buffer's capacity will double as int as {@code maxCapacity > capacity}.
   *
   * @param file The file to map into memory. If the file doesn't exist it will be automatically
   *     created.
   * @param initialCapacity The initial capacity of the buffer.
   * @param maxCapacity The maximum capacity of the buffer.
   * @return The mapped buffer.
   * @throws NullPointerException If {@code file} is {@code null}
   * @throws IllegalArgumentException If the {@code capacity} or {@code maxCapacity} is greater than
   *     {@link Integer#MAX_VALUE}.
   * @see #allocate(File)
   * @see #allocate(File, FileChannel.MapMode)
   * @see #allocate(File, int)
   * @see #allocate(File, FileChannel.MapMode, int)
   * @see #allocate(File, FileChannel.MapMode, int, int)
   */
  public static MappedBuffer allocate(
      final File file, final int initialCapacity, final int maxCapacity) {
    return allocate(file, FileChannel.MapMode.READ_WRITE, initialCapacity, maxCapacity);
  }

  /**
   * Allocates a mapped buffer.
   *
   * <p>Memory will be mapped by opening and expanding the given {@link File} to the desired {@code
   * count} and mapping the file contents into memory via {@link
   * FileChannel#map(FileChannel.MapMode, long, long)}.
   *
   * <p>The resulting buffer will have a capacity of {@code initialCapacity}. The underlying {@link
   * MappedBytes} will be initialized to the next power of {@code 2}. As bytes are written to the
   * buffer, the buffer's capacity will double as int as {@code maxCapacity > capacity}.
   *
   * @param file The file to map into memory. If the file doesn't exist it will be automatically
   *     created.
   * @param mode The mode with which to map the file.
   * @param initialCapacity The initial capacity of the buffer.
   * @param maxCapacity The maximum capacity of the buffer.
   * @return The mapped buffer.
   * @throws NullPointerException If {@code file} is {@code null}
   * @throws IllegalArgumentException If the {@code capacity} or {@code maxCapacity} is greater than
   *     {@link Integer#MAX_VALUE}.
   * @see #allocate(File)
   * @see #allocate(File, FileChannel.MapMode)
   * @see #allocate(File, int)
   * @see #allocate(File, FileChannel.MapMode, int)
   * @see #allocate(File, int, int)
   */
  public static MappedBuffer allocate(
      final File file,
      final FileChannel.MapMode mode,
      final int initialCapacity,
      final int maxCapacity) {
    checkNotNull(file, "file cannot be null");
    checkNotNull(mode, "mode cannot be null");
    checkArgument(
        initialCapacity <= maxCapacity, "initial capacity cannot be greater than maximum capacity");
    return new MappedBuffer(
        MappedBytes.allocate(file, mode, initialCapacity), 0, initialCapacity, maxCapacity);
  }

  @Override
  public MappedBuffer duplicate() {
    return new MappedBuffer((MappedBytes) bytes, offset(), capacity(), maxCapacity());
  }

  /** Deletes the underlying file. */
  public void delete() {
    ((MappedBytes) bytes).delete();
  }
}
