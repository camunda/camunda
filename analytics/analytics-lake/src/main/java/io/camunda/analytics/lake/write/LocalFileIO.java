/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.write;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.apache.iceberg.exceptions.AlreadyExistsException;
import org.apache.iceberg.exceptions.NotFoundException;
import org.apache.iceberg.exceptions.RuntimeIOException;
import org.apache.iceberg.io.FileIO;
import org.apache.iceberg.io.InputFile;
import org.apache.iceberg.io.OutputFile;
import org.apache.iceberg.io.PositionOutputStream;
import org.apache.iceberg.io.SeekableInputStream;

/**
 * A minimal, hadoop-free {@link FileIO} over the local filesystem.
 *
 * <p>iceberg-core's own catalog-agnostic answer to "read/write a local file" is {@code
 * ResolvingFileIO}, but for the {@code file:} scheme it falls back to {@code
 * org.apache.iceberg.hadoop.HadoopFileIO} — which needs a full Hadoop {@code Configuration} class
 * on the classpath. Pulling in {@code hadoop-client} for that single class in a PoC that never
 * touches HDFS felt like the wrong trade, so this is a from-scratch implementation of the three
 * {@link FileIO} methods iceberg-core actually calls (it uses {@link FileIO} to read/write table
 * metadata JSON, manifests and manifest-lists — never the Parquet data files themselves, which this
 * module writes directly via DuckDB and only <em>registers</em> with iceberg-core).
 *
 * <p><b>The one adaptation this required:</b> the warehouse location (and therefore every table and
 * data-file location iceberg-core hands to this class) is configured as a {@code file:} URI,
 * matching how every other Iceberg catalog names local locations. DuckDB's {@code COPY ... TO} and
 * its Parquet reader, however, want a plain filesystem path with no scheme. {@link
 * #toFilesystemPath(String)} is the single normalization point that reconciles the two: it is used
 * here to open real files, and by {@link IcebergLakeWriter} to compute the OS path it hands to
 * DuckDB, while the {@code file:}-prefixed string keeps flowing through Iceberg's own metadata
 * unchanged.
 *
 * <p>Public (not package-private): {@code io.camunda.analytics.lake.ui.LakeUiServer} opens its own
 * independent {@link org.apache.iceberg.jdbc.JdbcCatalog} handle against the same warehouse — see
 * that class's javadoc for why it must not share this writer's {@code Table}/catalog instances —
 * and needs both this class (to hand the catalog an {@code ioBuilder}) and {@link
 * #toFilesystemPath(String)} (to turn a data file's {@code location()} back into a path DuckDB's
 * {@code read_parquet} can open) to do so.
 */
public final class LocalFileIO implements FileIO {

  private static final long serialVersionUID = 1L;

  @Override
  public InputFile newInputFile(final String location) {
    return new LocalInputFile(location);
  }

  @Override
  public OutputFile newOutputFile(final String location) {
    return new LocalOutputFile(location);
  }

  @Override
  public void deleteFile(final String location) {
    try {
      Files.deleteIfExists(toFilesystemPath(location));
    } catch (final IOException e) {
      throw new RuntimeIOException(e, "Failed to delete file: %s", location);
    }
  }

  /** See the class javadoc — the {@code file:} URI / plain-path reconciliation point. */
  public static Path toFilesystemPath(final String location) {
    return location.startsWith("file:") ? Path.of(URI.create(location)) : Path.of(location);
  }

  private static final class LocalInputFile implements InputFile {

    private final String location;
    private final Path path;

    LocalInputFile(final String location) {
      this.location = location;
      path = toFilesystemPath(location);
    }

    @Override
    public long getLength() {
      try {
        return Files.size(path);
      } catch (final IOException e) {
        throw new RuntimeIOException(e, "Failed to get length of %s", location);
      }
    }

    @Override
    public SeekableInputStream newStream() {
      if (!Files.exists(path)) {
        throw new NotFoundException("File does not exist: %s", location);
      }
      try {
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
          public void close() throws IOException {
            channel.close();
          }
        };
      } catch (final IOException e) {
        throw new RuntimeIOException(e, "Failed to open %s", location);
      }
    }

    @Override
    public String location() {
      return location;
    }

    @Override
    public boolean exists() {
      return Files.exists(path);
    }
  }

  private static final class LocalOutputFile implements OutputFile {

    private final String location;
    private final Path path;

    LocalOutputFile(final String location) {
      this.location = location;
      path = toFilesystemPath(location);
    }

    @Override
    public PositionOutputStream create() {
      if (Files.exists(path)) {
        throw new AlreadyExistsException("File already exists: %s", location);
      }
      return open(StandardOpenOption.CREATE_NEW);
    }

    @Override
    public PositionOutputStream createOrOverwrite() {
      return open(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private PositionOutputStream open(final StandardOpenOption... extraOptions) {
      try {
        if (path.getParent() != null) {
          Files.createDirectories(path.getParent());
        }
        final StandardOpenOption[] options = new StandardOpenOption[extraOptions.length + 1];
        options[0] = StandardOpenOption.WRITE;
        System.arraycopy(extraOptions, 0, options, 1, extraOptions.length);
        final FileChannel channel = FileChannel.open(path, options);
        return new PositionOutputStream() {
          // Tracked here instead of asking the channel: Iceberg calls getPos() AFTER close()
          // (e.g. AvroFileAppender.length() -> storedLength()) to record the final file length,
          // and a closed FileChannel throws ClosedChannelException on position().
          private long pos;

          @Override
          public long getPos() {
            return pos;
          }

          @Override
          public void write(final int b) throws IOException {
            pos += channel.write(ByteBuffer.wrap(new byte[] {(byte) b}));
          }

          @Override
          public void write(final byte[] b, final int off, final int len) throws IOException {
            long written = 0;
            final ByteBuffer buffer = ByteBuffer.wrap(b, off, len);
            while (buffer.hasRemaining()) {
              written += channel.write(buffer);
            }
            pos += written;
          }

          @Override
          public void close() throws IOException {
            channel.close();
          }
        };
      } catch (final IOException e) {
        throw new RuntimeIOException(e, "Failed to open %s for writing", location);
      }
    }

    @Override
    public String location() {
      return location;
    }

    @Override
    public InputFile toInputFile() {
      return new LocalInputFile(location);
    }
  }
}
