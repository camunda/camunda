/*
 * Copyright 2017-present Open Networking Foundation
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
package io.atomix.utils.memory;

import java.io.File;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mapped memory.
 *
 * <p>This is a special memory descriptor that handles management of {@link MappedByteBuffer} based
 * memory. The mapped memory descriptor simply points to the memory address of the underlying byte
 * buffer. When memory is reallocated, the parent {@link MappedMemoryAllocator} is used to create a
 * new {@link MappedByteBuffer} and free the existing buffer.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
public class MappedMemory implements Memory {
  private static final long MAX_SIZE = Integer.MAX_VALUE - 5;

  private static final Logger LOGGER = LoggerFactory.getLogger(MappedMemory.class);
  private final MappedByteBuffer buffer;
  private final MappedMemoryAllocator allocator;
  private final int size;

  public MappedMemory(final MappedByteBuffer buffer, final MappedMemoryAllocator allocator) {
    this.buffer = buffer;
    this.allocator = allocator;
    this.size = buffer.capacity();
  }

  /**
   * Allocates memory mapped to a file on disk.
   *
   * @param file The file to which to map memory.
   * @param size The count of the memory to map.
   * @return The mapped memory.
   * @throws IllegalArgumentException If {@code count} is greater than {@link MappedMemory#MAX_SIZE}
   */
  public static MappedMemory allocate(final File file, final int size) {
    return new MappedMemoryAllocator(file).allocate(size);
  }

  /**
   * Allocates memory mapped to a file on disk.
   *
   * @param file The file to which to map memory.
   * @param mode The mode with which to map memory.
   * @param size The count of the memory to map.
   * @return The mapped memory.
   * @throws IllegalArgumentException If {@code count} is greater than {@link MappedMemory#MAX_SIZE}
   */
  public static MappedMemory allocate(
      final File file, final FileChannel.MapMode mode, final int size) {
    if (size > MAX_SIZE) {
      throw new IllegalArgumentException("size cannot be greater than " + MAX_SIZE);
    }
    return new MappedMemoryAllocator(file, mode).allocate(size);
  }

  /** Flushes the mapped buffer to disk. */
  public void flush() {
    buffer.force();
  }

  @Override
  public int size() {
    return size;
  }

  @Override
  public void free() {
    try {
      BufferCleaner.freeBuffer(buffer);
    } catch (final Exception e) {
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug("Failed to unmap direct buffer", e);
      }
    }
    allocator.release();
  }

  public void close() {
    free();
    allocator.close();
  }
}
