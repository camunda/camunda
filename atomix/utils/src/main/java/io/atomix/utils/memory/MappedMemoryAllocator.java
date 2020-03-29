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
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Mapped memory allocator.
 *
 * <p>The mapped memory allocator provides direct memory access to memory mapped from a file on
 * disk. The mapped allocator supports allocating memory in any {@link FileChannel.MapMode}. Once
 * the file is mapped and the memory has been allocated, the mapped allocator provides the memory
 * address of the underlying {@link java.nio.MappedByteBuffer} for access via {@link
 * sun.misc.Unsafe}.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
public class MappedMemoryAllocator implements MemoryAllocator<MappedMemory> {
  public static final FileChannel.MapMode DEFAULT_MAP_MODE = FileChannel.MapMode.READ_WRITE;

  private final AtomicInteger referenceCount = new AtomicInteger();
  private final RandomAccessFile file;
  private final FileChannel channel;
  private final FileChannel.MapMode mode;
  private final long offset;

  public MappedMemoryAllocator(final File file) {
    this(file, DEFAULT_MAP_MODE, 0);
  }

  public MappedMemoryAllocator(final File file, final FileChannel.MapMode mode) {
    this(file, mode, 0);
  }

  public MappedMemoryAllocator(final File file, final FileChannel.MapMode mode, final long offset) {
    this(createFile(file, mode), mode, offset);
  }

  public MappedMemoryAllocator(
      final RandomAccessFile file, final FileChannel.MapMode mode, final long offset) {
    if (file == null) {
      throw new NullPointerException("file cannot be null");
    }
    if (mode == null) {
      throw new NullPointerException("mode cannot be null");
    }
    if (offset < 0) {
      throw new IllegalArgumentException("offset cannot be negative");
    }
    this.file = file;
    this.channel = this.file.getChannel();
    this.mode = mode;
    this.offset = offset;
  }

  private static RandomAccessFile createFile(final File file, FileChannel.MapMode mode) {
    if (file == null) {
      throw new NullPointerException("file cannot be null");
    }
    if (mode == null) {
      mode = DEFAULT_MAP_MODE;
    }
    try {
      return new RandomAccessFile(file, parseMode(mode));
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static String parseMode(final FileChannel.MapMode mode) {
    if (mode == FileChannel.MapMode.READ_ONLY) {
      return "r";
    } else if (mode == FileChannel.MapMode.READ_WRITE) {
      return "rw";
    }
    throw new IllegalArgumentException("unsupported map mode");
  }

  @Override
  public MappedMemory allocate(final int size) {
    try {
      if (file.length() < size) {
        file.setLength(size);
      }
      referenceCount.incrementAndGet();
      return new MappedMemory(channel.map(mode, offset, size), this);
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public MappedMemory reallocate(final MappedMemory memory, final int size) {
    final MappedMemory newMemory = allocate(size);
    memory.free();
    return newMemory;
  }

  public void close() {
    try {
      file.close();
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  /** Releases a reference from the allocator. */
  void release() {
    if (referenceCount.decrementAndGet() == 0) {
      close();
    }
  }
}
