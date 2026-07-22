/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink.encode;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.apache.parquet.io.InputFile;
import org.apache.parquet.io.SeekableInputStream;

/**
 * A from-scratch, Hadoop-free {@code org.apache.parquet.io.InputFile} over a local path, so tests
 * can read a written file back with parquet-java's own {@code ParquetFileReader} without pulling
 * {@code org.apache.hadoop.fs.FileSystem} (and the {@code commons-logging} dependency it needs)
 * into the picture: parquet-hadoop's own {@code HadoopInputFile} would require exactly that.
 * Mirrors {@code io.camunda.analytics.lake.write.LocalFileIO}'s shape, over parquet's own IO
 * interfaces instead of Iceberg's.
 */
final class LocalParquetInputFile implements InputFile {

  private final Path path;

  LocalParquetInputFile(final Path path) {
    this.path = path;
  }

  @Override
  public long getLength() throws IOException {
    return Files.size(path);
  }

  @Override
  public SeekableInputStream newStream() throws IOException {
    final FileChannel channel = FileChannel.open(path, StandardOpenOption.READ);
    return new SeekableInputStream() {
      @Override
      public long getPos() throws IOException {
        return channel.position();
      }

      @Override
      public void seek(final long newPos) throws IOException {
        channel.position(newPos);
      }

      @Override
      public int read() throws IOException {
        final ByteBuffer one = ByteBuffer.allocate(1);
        final int n = channel.read(one);
        return n < 0 ? -1 : one.get(0) & 0xFF;
      }

      @Override
      public int read(final byte[] b, final int off, final int len) throws IOException {
        return channel.read(ByteBuffer.wrap(b, off, len));
      }

      @Override
      public int read(final ByteBuffer buf) throws IOException {
        return channel.read(buf);
      }

      @Override
      public void readFully(final byte[] b) throws IOException {
        readFully(b, 0, b.length);
      }

      @Override
      public void readFully(final byte[] b, final int off, final int len) throws IOException {
        readFully(ByteBuffer.wrap(b, off, len));
      }

      @Override
      public void readFully(final ByteBuffer buf) throws IOException {
        while (buf.hasRemaining()) {
          if (channel.read(buf) < 0) {
            throw new EOFException();
          }
        }
      }

      @Override
      public void close() throws IOException {
        channel.close();
      }
    };
  }
}
