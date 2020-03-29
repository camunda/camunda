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

import io.atomix.utils.memory.Memory;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;

/**
 * File bytes.
 *
 * <p>File bytes wrap a simple {@link RandomAccessFile} instance to provide random access to a
 * randomAccessFile on local disk. All operations are delegated directly to the {@link
 * RandomAccessFile} interface, and limitations are dependent on the semantics of the underlying
 * randomAccessFile.
 *
 * <p>Bytes are always stored in the underlying randomAccessFile in {@link ByteOrder#BIG_ENDIAN}
 * order. To flip the byte order to read or write to/from a randomAccessFile in {@link
 * ByteOrder#LITTLE_ENDIAN} order use {@link Bytes#order(ByteOrder)}.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
public class FileBytes extends AbstractBytes {
  static final String DEFAULT_MODE = "rw";
  private static final int PAGE_SIZE = 1024 * 4;
  private static final byte[] BLANK_PAGE = new byte[PAGE_SIZE];
  private final File file;
  private final String mode;
  private final RandomAccessFile randomAccessFile;
  private int size;

  FileBytes(final File file, String mode, final int size) {
    if (file == null) {
      throw new NullPointerException("file cannot be null");
    }
    if (mode == null) {
      mode = DEFAULT_MODE;
    }
    if (size < 0) {
      throw new IllegalArgumentException("size must be positive");
    }

    this.file = file;
    this.mode = mode;
    this.size = size;
    try {
      this.randomAccessFile = new RandomAccessFile(file, mode);
      if (size > randomAccessFile.length()) {
        randomAccessFile.setLength(size);
      }
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Allocates a randomAccessFile buffer of unlimited count.
   *
   * <p>The buffer will be allocated with {@link Long#MAX_VALUE} bytes. As bytes are written to the
   * buffer, the underlying {@link RandomAccessFile} will expand.
   *
   * @param file The randomAccessFile to allocate.
   * @return The allocated buffer.
   */
  public static FileBytes allocate(final File file) {
    return allocate(file, DEFAULT_MODE, Integer.MAX_VALUE);
  }

  /**
   * Allocates a randomAccessFile buffer.
   *
   * <p>If the underlying randomAccessFile is empty, the randomAccessFile count will expand
   * dynamically as bytes are written to the randomAccessFile.
   *
   * @param file The randomAccessFile to allocate.
   * @param size The count of the bytes to allocate.
   * @return The allocated buffer.
   */
  public static FileBytes allocate(final File file, final int size) {
    return allocate(file, DEFAULT_MODE, size);
  }

  /**
   * Allocates a randomAccessFile buffer.
   *
   * <p>If the underlying randomAccessFile is empty, the randomAccessFile count will expand
   * dynamically as bytes are written to the randomAccessFile.
   *
   * @param file The randomAccessFile to allocate.
   * @param mode The mode in which to open the underlying {@link RandomAccessFile}.
   * @param size The count of the bytes to allocate.
   * @return The allocated buffer.
   */
  public static FileBytes allocate(final File file, final String mode, final int size) {
    return new FileBytes(file, mode, (int) Math.min(Memory.Util.toPow2(size), Integer.MAX_VALUE));
  }

  /**
   * Returns the underlying file object.
   *
   * @return The underlying file.
   */
  public File file() {
    return file;
  }

  /**
   * Returns the file mode.
   *
   * @return The file mode.
   */
  public String mode() {
    return mode;
  }

  @Override
  public int size() {
    return size;
  }

  @Override
  public Bytes resize(final int newSize) {
    if (newSize < size) {
      throw new IllegalArgumentException(
          "cannot decrease file bytes size; use zero() to decrease file size");
    }
    final int oldSize = this.size;
    this.size = newSize;
    try {
      final long length = randomAccessFile.length();
      if (newSize > length) {
        randomAccessFile.setLength(newSize);
        zero(oldSize);
      }
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
    return this;
  }

  @Override
  public boolean isFile() {
    return true;
  }

  @Override
  public ByteOrder order() {
    return ByteOrder.BIG_ENDIAN;
  }

  @Override
  public Bytes flush() {
    try {
      randomAccessFile.getFD().sync();
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
    return this;
  }

  @Override
  public void close() {
    try {
      randomAccessFile.close();
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
    super.close();
  }

  /**
   * Maps a portion of the randomAccessFile into memory in {@link FileChannel.MapMode#READ_WRITE}
   * mode and returns a {@link UnsafeMappedBytes} instance.
   *
   * @param offset The offset from which to map the randomAccessFile into memory.
   * @param size The count of the bytes to map into memory.
   * @return The mapped bytes.
   * @throws IllegalArgumentException If {@code count} is greater than the maximum allowed {@link
   *     java.nio.MappedByteBuffer} count: {@link Integer#MAX_VALUE}
   */
  public MappedBytes map(final int offset, final int size) {
    return map(offset, size, parseMode(mode));
  }

  /**
   * Maps a portion of the randomAccessFile into memory and returns a {@link UnsafeMappedBytes}
   * instance.
   *
   * @param offset The offset from which to map the randomAccessFile into memory.
   * @param size The count of the bytes to map into memory.
   * @param mode The mode in which to map the randomAccessFile into memory.
   * @return The mapped bytes.
   * @throws IllegalArgumentException If {@code count} is greater than the maximum allowed {@link
   *     java.nio.MappedByteBuffer} count: {@link Integer#MAX_VALUE}
   */
  public MappedBytes map(final int offset, final int size, final FileChannel.MapMode mode) {
    final MappedByteBuffer mappedByteBuffer = mapFile(randomAccessFile, offset, size, mode);
    return new MappedBytes(file, randomAccessFile, mappedByteBuffer, mode);
  }

  private static MappedByteBuffer mapFile(
      final RandomAccessFile randomAccessFile,
      final int offset,
      final int size,
      final FileChannel.MapMode mode) {
    try {
      return randomAccessFile.getChannel().map(mode, offset, size);
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static FileChannel.MapMode parseMode(final String mode) {
    switch (mode) {
      case "r":
        return FileChannel.MapMode.READ_ONLY;
      case "rw":
      default:
        return FileChannel.MapMode.READ_WRITE;
    }
  }

  /** Seeks to the given offset. */
  private void seekToOffset(final int offset) throws IOException {
    if (randomAccessFile.getFilePointer() != offset) {
      randomAccessFile.seek(offset);
    }
  }

  @Override
  public Bytes zero() {
    try {
      randomAccessFile.setLength(0);
      randomAccessFile.setLength(size);
      for (int i = 0; i < size; i += PAGE_SIZE) {
        randomAccessFile.write(BLANK_PAGE, 0, Math.min(size - i, PAGE_SIZE));
      }
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
    return this;
  }

  @Override
  public Bytes zero(final int offset) {
    try {
      final int length = Math.max(offset, size);
      randomAccessFile.setLength(offset);
      randomAccessFile.setLength(length);
      seekToOffset(offset);
      for (int i = offset; i < length; i += PAGE_SIZE) {
        randomAccessFile.write(BLANK_PAGE, 0, Math.min(length - i, PAGE_SIZE));
      }
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
    return this;
  }

  @Override
  public Bytes zero(final int offset, final int length) {
    for (int i = offset; i < offset + length; i++) {
      writeByte(i, (byte) 0);
    }
    return this;
  }

  @Override
  public Bytes write(final int position, Bytes bytes, final int offset, final int length) {
    checkWrite(position, length);
    if (bytes instanceof WrappedBytes) {
      bytes = ((WrappedBytes) bytes).root();
    }
    if (bytes.hasArray()) {
      try {
        seekToOffset(position);
        randomAccessFile.write(bytes.array(), (int) offset, (int) length);
      } catch (final IOException e) {
        throw new RuntimeException(e);
      }
    } else {
      try {
        seekToOffset(position);
        final byte[] writeBytes = new byte[(int) length];
        bytes.read(offset, writeBytes, 0, length);
        randomAccessFile.write(writeBytes);
      } catch (final IOException e) {
        throw new RuntimeException(e);
      }
    }
    return this;
  }

  @Override
  public Bytes write(final int position, final byte[] bytes, final int offset, final int length) {
    checkWrite(position, length);
    try {
      seekToOffset(position);
      randomAccessFile.write(bytes, (int) offset, (int) length);
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
    return this;
  }

  @Override
  public Bytes write(
      final int offset, final ByteBuffer src, final int srcOffset, final int length) {
    checkWrite(offset, length);

    try {
      final ByteBuffer view = src.asReadOnlyBuffer();
      view.position(srcOffset).limit(srcOffset + length);
      randomAccessFile.getChannel().write(view, offset);
    } catch (final IllegalArgumentException e) {
      // this is necessary in order not to break the method contract
      throw new IndexOutOfBoundsException(e.getMessage());
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    return this;
  }

  @Override
  public Bytes writeByte(final int offset, final int b) {
    checkWrite(offset, BYTE);
    try {
      seekToOffset(offset);
      randomAccessFile.writeByte(b);
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
    return this;
  }

  @Override
  public Bytes writeChar(final int offset, final char c) {
    checkWrite(offset, CHARACTER);
    try {
      seekToOffset(offset);
      randomAccessFile.writeChar(c);
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
    return this;
  }

  @Override
  public Bytes writeShort(final int offset, final short s) {
    checkWrite(offset, SHORT);
    try {
      seekToOffset(offset);
      randomAccessFile.writeShort(s);
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
    return this;
  }

  @Override
  public Bytes writeInt(final int offset, final int i) {
    checkWrite(offset, INTEGER);
    try {
      seekToOffset(offset);
      randomAccessFile.writeInt(i);
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
    return this;
  }

  @Override
  public Bytes writeLong(final int offset, final long l) {
    checkWrite(offset, LONG);
    try {
      seekToOffset(offset);
      randomAccessFile.writeLong(l);
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
    return this;
  }

  @Override
  public Bytes writeFloat(final int offset, final float f) {
    checkWrite(offset, FLOAT);
    try {
      seekToOffset(offset);
      randomAccessFile.writeFloat(f);
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
    return this;
  }

  @Override
  public Bytes writeDouble(final int offset, final double d) {
    checkWrite(offset, DOUBLE);
    try {
      seekToOffset(offset);
      randomAccessFile.writeDouble(d);
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
    return this;
  }

  @Override
  public Bytes read(final int position, Bytes bytes, final int offset, final int length) {
    checkRead(position, length);
    if (bytes instanceof WrappedBytes) {
      bytes = ((WrappedBytes) bytes).root();
    }
    if (bytes.hasArray()) {
      try {
        seekToOffset(position);
        randomAccessFile.readFully(bytes.array(), (int) offset, (int) length);
      } catch (final IOException e) {
        throw new RuntimeException(e);
      }
    } else {
      try {
        seekToOffset(position);
        final byte[] readBytes = new byte[(int) length];
        randomAccessFile.readFully(readBytes);
        bytes.write(offset, readBytes, 0, length);
      } catch (final IOException e) {
        throw new RuntimeException(e);
      }
    }
    return this;
  }

  @Override
  public Bytes read(final int position, final byte[] bytes, final int offset, final int length) {
    checkRead(position, length);
    try {
      seekToOffset(position);
      randomAccessFile.readFully(bytes, (int) offset, (int) length);
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
    return this;
  }

  @Override
  public Bytes read(final int offset, final ByteBuffer dst, final int dstOffset, final int length) {
    checkRead(offset, length);

    try {
      final ByteBuffer view = dst.duplicate();
      view.position(dstOffset).limit(dstOffset + length);
      randomAccessFile.getChannel().read(view, offset);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    return this;
  }

  @Override
  public int readByte(final int offset) {
    checkRead(offset, BYTE);
    try {
      seekToOffset(offset);
      return randomAccessFile.readByte();
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public char readChar(final int offset) {
    checkRead(offset, CHARACTER);
    try {
      seekToOffset(offset);
      return randomAccessFile.readChar();
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public short readShort(final int offset) {
    checkRead(offset, SHORT);
    try {
      seekToOffset(offset);
      return randomAccessFile.readShort();
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public int readInt(final int offset) {
    checkRead(offset, INTEGER);
    try {
      seekToOffset(offset);
      return randomAccessFile.readInt();
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public long readLong(final int offset) {
    checkRead(offset, LONG);
    try {
      seekToOffset(offset);
      return randomAccessFile.readLong();
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public float readFloat(final int offset) {
    checkRead(offset, FLOAT);
    try {
      seekToOffset(offset);
      return randomAccessFile.readFloat();
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public double readDouble(final int offset) {
    checkRead(offset, DOUBLE);
    try {
      seekToOffset(offset);
      return randomAccessFile.readDouble();
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
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
}
