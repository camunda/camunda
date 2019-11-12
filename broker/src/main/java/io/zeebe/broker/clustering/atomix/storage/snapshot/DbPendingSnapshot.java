package io.zeebe.broker.clustering.atomix.storage.snapshot;

import io.atomix.protocols.raft.storage.snapshot.PendingSnapshot;
import io.atomix.utils.time.WallClockTimestamp;
import io.zeebe.util.ZbLogger;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;

public class DbPendingSnapshot implements PendingSnapshot {
  private static final Logger LOGGER = new ZbLogger(DbPendingSnapshot.class);

  private final long index;
  private final long term;
  private final WallClockTimestamp timestamp;
  private final Path directory;

  private ByteBuffer expectedId;

  public DbPendingSnapshot(
      final long index, final long term, final WallClockTimestamp timestamp, final Path directory) {
    this.index = index;
    this.term = term;
    this.timestamp = timestamp;
    this.directory = directory;
  }

  @Override
  public long index() {
    return index;
  }

  @Override
  public long term() {
    return term;
  }

  @Override
  public WallClockTimestamp timestamp() {
    return timestamp;
  }

  @Override
  public boolean contains(final ByteBuffer chunkId) {
    return Files.exists(directory.resolve(getFile(chunkId)));
  }

  @Override
  public boolean isExpectedChunk(final ByteBuffer chunkId) {
    if (expectedId == null) {
      return chunkId == null;
    }

    return expectedId.equals(chunkId);
  }

  @Override
  public void write(final ByteBuffer chunkId, final ByteBuffer chunkData) {
    final var filename = getFile(chunkId);
    final var path = directory.resolve(filename);

    try (var channel =
        Files.newByteChannel(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
      channel.write(chunkData);
    } catch (final FileAlreadyExistsException e) {
      LOGGER.debug("Chunk {} of pending snapshot {} already exists at {}", filename, this, path, e);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public void expect(final ByteBuffer nextChunkId) {
    expectedId = nextChunkId;
  }

  @Override
  public void commit() {}

  @Override
  public void abort() {
    try {
      Files.delete(directory);
    } catch (final IOException e) {
      LOGGER.warn("Failed to delete pending snapshot {}", this, e);
    }
  }

  @Override
  public String toString() {
    return "DbPendingSnapshot{"
        + "index="
        + index
        + ", term="
        + term
        + ", timestamp="
        + timestamp
        + ", directory="
        + directory
        + '}';
  }

  private String getFile(final ByteBuffer chunkId) {
    final var view = new UnsafeBuffer(chunkId);
    return view.getStringWithoutLengthAscii(0, chunkId.remaining());
  }
}
