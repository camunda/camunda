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

import io.atomix.utils.memory.Memory;
import java.io.File;
import java.nio.channels.FileChannel;

/**
 * File buffer.
 *
 * <p>File buffers wrap a simple {@link java.io.RandomAccessFile} instance to provide random access
 * to a file on local disk. All operations are delegated directly to the {@link
 * java.io.RandomAccessFile} interface, and limitations are dependent on the semantics of the
 * underlying file.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
public final class FileBuffer extends AbstractBuffer {

  private final FileBytes bytes;

  private FileBuffer(
      final FileBytes bytes, final int offset, final int initialCapacity, final int maxCapacity) {
    super(bytes, offset, initialCapacity, maxCapacity, null);
    this.bytes = bytes;
  }

  /**
   * Allocates a file buffer of unlimited capacity.
   *
   * <p>The buffer will initially be allocated with {@code 4096} bytes. As bytes are written to the
   * resulting buffer and the original capacity is reached, the buffer's capacity will double.
   *
   * @param file The file to allocate.
   * @return The allocated buffer.
   * @see FileBuffer#allocate(File, int)
   * @see FileBuffer#allocate(File, int, int)
   * @see FileBuffer#allocate(File, String, int, int)
   */
  public static FileBuffer allocate(final File file) {
    return allocate(file, FileBytes.DEFAULT_MODE, DEFAULT_INITIAL_CAPACITY, Integer.MAX_VALUE);
  }

  /**
   * Allocates a file buffer with the given initial capacity.
   *
   * <p>If the underlying file is empty, the file count will expand dynamically as bytes are written
   * to the file. The underlying {@link FileBytes} will be initialized to the nearest power of
   * {@code 2}.
   *
   * @param file The file to allocate.
   * @param initialCapacity The initial capacity of the bytes to allocate.
   * @return The allocated buffer.
   * @see FileBuffer#allocate(File)
   * @see FileBuffer#allocate(File, int, int)
   * @see FileBuffer#allocate(File, String, int, int)
   */
  public static FileBuffer allocate(final File file, final int initialCapacity) {
    return allocate(file, FileBytes.DEFAULT_MODE, initialCapacity, Integer.MAX_VALUE);
  }

  /**
   * Allocates a file buffer.
   *
   * <p>The underlying {@link java.io.RandomAccessFile} will be created in {@code rw} mode by
   * default. The resulting buffer will be initialized with a capacity of {@code initialCapacity}.
   * The underlying {@link FileBytes} will be initialized to the nearest power of {@code 2}. As
   * bytes are written to the file the buffer's capacity will double up to {@code maxCapacity}.
   *
   * @param file The file to allocate.
   * @param initialCapacity The initial capacity of the buffer.
   * @param maxCapacity The maximum allowed capacity of the buffer.
   * @return The allocated buffer.
   * @see FileBuffer#allocate(File)
   * @see FileBuffer#allocate(File, int)
   * @see FileBuffer#allocate(File, String, int, int)
   */
  public static FileBuffer allocate(
      final File file, final int initialCapacity, final int maxCapacity) {
    return allocate(file, FileBytes.DEFAULT_MODE, initialCapacity, maxCapacity);
  }

  /**
   * Allocates a file buffer.
   *
   * <p>The resulting buffer will be initialized with a capacity of {@code initialCapacity}. The
   * underlying {@link FileBytes} will be initialized to the nearest power of {@code 2}. As bytes
   * are written to the file the buffer's capacity will double up to {@code maxCapacity}.
   *
   * @param file The file to allocate.
   * @param mode The mode in which to open the underlying {@link java.io.RandomAccessFile}.
   * @param initialCapacity The initial capacity of the buffer.
   * @param maxCapacity The maximum allowed capacity of the buffer.
   * @return The allocated buffer.
   * @see FileBuffer#allocate(File)
   * @see FileBuffer#allocate(File, int)
   * @see FileBuffer#allocate(File, int, int)
   */
  public static FileBuffer allocate(
      final File file, final String mode, final int initialCapacity, final int maxCapacity) {
    checkArgument(
        initialCapacity <= maxCapacity, "initial capacity cannot be greater than maximum capacity");
    return new FileBuffer(
        new FileBytes(file, mode, (int) Math.min(Memory.Util.toPow2(initialCapacity), maxCapacity)),
        0,
        initialCapacity,
        maxCapacity);
  }

  /**
   * Returns the underlying file object.
   *
   * @return The underlying file.
   */
  public File file() {
    return ((FileBytes) bytes).file();
  }

  /**
   * Maps a portion of the underlying file into memory in {@link FileChannel.MapMode#READ_WRITE}
   * mode starting at the current position up to the given {@code count}.
   *
   * @param size The count of the bytes to map into memory.
   * @return The mapped buffer.
   * @throws IllegalArgumentException If {@code count} is greater than the maximum allowed {@link
   *     java.nio.MappedByteBuffer} count: {@link Integer#MAX_VALUE}
   */
  public MappedBuffer map(final int size) {
    return map(position(), size, FileChannel.MapMode.READ_WRITE);
  }

  /**
   * Maps a portion of the underlying file into memory starting at the current position up to the
   * given {@code count}.
   *
   * @param size The count of the bytes to map into memory.
   * @param mode The mode in which to map the bytes into memory.
   * @return The mapped buffer.
   * @throws IllegalArgumentException If {@code count} is greater than the maximum allowed {@link
   *     java.nio.MappedByteBuffer} count: {@link Integer#MAX_VALUE}
   */
  public MappedBuffer map(final int size, final FileChannel.MapMode mode) {
    return map(position(), size, mode);
  }

  /**
   * Maps a portion of the underlying file into memory in {@link FileChannel.MapMode#READ_WRITE}
   * mode starting at the given {@code offset} up to the given {@code count}.
   *
   * @param offset The offset from which to map bytes into memory.
   * @param size The count of the bytes to map into memory.
   * @return The mapped buffer.
   * @throws IllegalArgumentException If {@code count} is greater than the maximum allowed {@link
   *     java.nio.MappedByteBuffer} count: {@link Integer#MAX_VALUE}
   */
  public MappedBuffer map(final int offset, final int size) {
    return map(offset, size, FileChannel.MapMode.READ_WRITE);
  }

  /**
   * Maps a portion of the underlying file into memory starting at the given {@code offset} up to
   * the given {@code count}.
   *
   * @param offset The offset from which to map bytes into memory.
   * @param size The count of the bytes to map into memory.
   * @param mode The mode in which to map the bytes into memory.
   * @return The mapped buffer.
   * @throws IllegalArgumentException If {@code count} is greater than the maximum allowed {@link
   *     java.nio.MappedByteBuffer} count: {@link Integer#MAX_VALUE}
   */
  public MappedBuffer map(final int offset, final int size, final FileChannel.MapMode mode) {
    return new MappedBuffer(((FileBytes) bytes).map(offset, size, mode), 0, size, size);
  }

  @Override
  protected void compact(final int from, final int to, final int length) {
    final byte[] bytes = new byte[1024];
    int position = from;
    while (position < from + length) {
      final int size = Math.min((from + length) - position, 1024);
      this.bytes.read(position, bytes, 0, size);
      this.bytes.write(0, bytes, 0, size);
      position += size;
    }
  }

  @Override
  public FileBuffer duplicate() {
    return new FileBuffer(
        new FileBytes(bytes.file(), bytes.mode(), bytes.size()),
        offset(),
        capacity(),
        maxCapacity());
  }

  /**
   * Duplicates the buffer using the given mode.
   *
   * @return The mode with which to open the duplicate buffer.
   */
  public FileBuffer duplicate(final String mode) {
    return new FileBuffer(
        new FileBytes(bytes.file(), mode, bytes.size()), offset(), capacity(), maxCapacity());
  }

  /** Deletes the underlying file. */
  public void delete() {
    bytes.delete();
  }
}
