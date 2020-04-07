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

import io.atomix.utils.AtomixIOException;
import io.atomix.utils.memory.BufferCleaner;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** {@link ByteBuffer} based mapped bytes. */
public class MappedBytes extends ByteBufferBytes {

  private static final Logger LOGGER = LoggerFactory.getLogger(MappedBytes.class);
  private final File file;
  private final RandomAccessFile randomAccessFile;
  private final FileChannel.MapMode mode;

  protected MappedBytes(
      final File file,
      final RandomAccessFile randomAccessFile,
      final MappedByteBuffer buffer,
      final FileChannel.MapMode mode) {
    super(buffer);
    this.file = file;
    this.randomAccessFile = randomAccessFile;
    this.mode = mode;
  }

  /**
   * Allocates a mapped buffer in {@link FileChannel.MapMode#READ_WRITE} mode.
   *
   * <p>Memory will be mapped by opening and expanding the given {@link File} to the desired {@code
   * count} and mapping the file contents into memory via {@link
   * FileChannel#map(FileChannel.MapMode, long, long)}.
   *
   * @param file The file to map into memory. If the file doesn't exist it will be automatically
   *     created.
   * @param size The count of the buffer to allocate (in bytes).
   * @return The mapped buffer.
   * @throws NullPointerException If {@code file} is {@code null}
   * @throws IllegalArgumentException If {@code count} is greater than {@link MappedBytes#MAX_SIZE}
   * @see #allocate(File, FileChannel.MapMode, int)
   */
  public static MappedBytes allocate(final File file, final int size) {
    return allocate(file, FileChannel.MapMode.READ_WRITE, size);
  }

  /**
   * Allocates a mapped buffer.
   *
   * <p>Memory will be mapped by opening and expanding the given {@link File} to the desired {@code
   * count} and mapping the file contents into memory via {@link
   * FileChannel#map(FileChannel.MapMode, long, long)}.
   *
   * @param file The file to map into memory. If the file doesn't exist it will be automatically
   *     created.
   * @param mode The mode with which to map the file.
   * @param size The count of the buffer to allocate (in bytes).
   * @return The mapped buffer.
   * @throws NullPointerException If {@code file} is {@code null}
   * @throws IllegalArgumentException If {@code count} is greater than {@link Integer#MAX_VALUE}
   * @see #allocate(File, int)
   */
  public static MappedBytes allocate(
      final File file, final FileChannel.MapMode mode, final int size) {
    return FileBytes.allocate(file, size).map(0, size, mode);
  }

  @Override
  protected ByteBuffer newByteBuffer(final int size) {
    try {
      return randomAccessFile.getChannel().map(mode, 0, size);
    } catch (final IOException e) {
      throw new AtomixIOException(e);
    }
  }

  @Override
  public boolean isDirect() {
    return true;
  }

  @Override
  public Bytes flush() {
    ((MappedByteBuffer) buffer).force();
    return this;
  }

  @Override
  public void close() {
    try {
      BufferCleaner.freeBuffer(buffer);
    } catch (final Exception e) {
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug("Failed to unmap direct buffer", e);
      }
    }
    try {
      randomAccessFile.close();
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
    super.close();
  }

  /** Deletes the underlying file. */
  public void delete() {
    try {
      close();
      Files.delete(file.toPath());
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
}
