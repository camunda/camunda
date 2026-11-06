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
 * limitations under the License
 */
package io.atomix.raft.storage.system;

import static com.google.common.base.MoreObjects.toStringHelper;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.atomix.cluster.MemberId;
import io.atomix.raft.metrics.MetaStoreMetrics;
import io.atomix.raft.storage.RaftStorage;
import io.atomix.raft.storage.StorageException;
import io.atomix.raft.storage.serializer.MetaEncoder;
import io.atomix.raft.storage.serializer.MetaStoreSerializer;
import io.camunda.zeebe.journal.JournalMetaStore;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages persistence of server configurations.
 *
 * <p>The server metastore is responsible for persisting server configurations. Each server persists
 * their current {@link #loadTerm() term} and last {@link #loadVote() vote} as is dictated by the
 * Raft consensus algorithm. Additionally, the metastore is responsible for storing the last know
 * server {@link Configuration}, including cluster membership.
 */
public class MetaStore implements JournalMetaStore, AutoCloseable {

  private final Logger log = LoggerFactory.getLogger(getClass());
  private final FileChannel configurationChannel;
  private final File confFile;
  private final MetaStoreSerializer serializer = new MetaStoreSerializer();
  private final FileChannel metaFileChannel;
  private final MetaStoreMetrics metrics;

  // volatile to avoid synchronizing on the whole meta store when reading this single value
  private volatile long lastFlushedIndex;
  private volatile long commitIndex;

  // Guards only the pending slot below and is never held during IO, so the Raft thread never waits
  // for a file write when requesting an async update. Lock order: this -> slotLock.
  private final Object slotLock = new Object();
  private final int partitionId;
  private boolean hasPendingIndex;
  private long pendingIndex;
  private boolean drainScheduled;
  private boolean closed;
  private ExecutorService asyncWriter;
  private volatile Exception asyncFailure;

  public MetaStore(final RaftStorage storage, final MeterRegistry meterRegistry)
      throws IOException {
    if (!(storage.directory().isDirectory() || storage.directory().mkdirs())) {
      throw new IllegalArgumentException(
          String.format("Can't create storage directory [%s].", storage.directory()));
    }

    partitionId = storage.partitionId();
    metrics = new MetaStoreMetrics(String.valueOf(storage.partitionId()), meterRegistry);

    // Note that for raft safety, irrespective of the storage level, <term, vote> metadata is always
    // persisted on disk.
    final File metaFile = new File(storage.directory(), String.format("%s.meta", storage.prefix()));
    MetaStoreRecord record = null;
    final var initFromFile = metaFile.exists();
    if (!initFromFile) {
      Files.write(
          metaFile.toPath(),
          new byte[32], // write zeros to prevent reading junk values
          StandardOpenOption.CREATE_NEW,
          StandardOpenOption.WRITE,
          StandardOpenOption.SYNC);

      // initialize the lastFlushedIndex to its null value; otherwise it will read it as 0 since
      // all bytes in the empty file are now 0
      lastFlushedIndex = MetaEncoder.lastFlushedIndexNullValue();
      commitIndex = MetaEncoder.commitIndexNullValue();
      record = new MetaStoreRecord(0, lastFlushedIndex, commitIndex, "");
    }

    metaFileChannel =
        FileChannel.open(
            metaFile.toPath(),
            StandardOpenOption.READ,
            StandardOpenOption.WRITE,
            StandardOpenOption.DSYNC);

    // Read existing meta info if the file was present
    if (initFromFile) {
      readMetaFromFile();
      record = serializer.readRecord();
      lastFlushedIndex = record.lastFlushedIndex();
      commitIndex = record.commitIndex();
    }
    // rewrite meta file with current schema
    initializeMetaBuffer(record);

    confFile = new File(storage.directory(), String.format("%s.conf", storage.prefix()));

    if (!confFile.exists()) {
      Files.write(
          confFile.toPath(),
          new byte[32], // write zeros to prevent reading junk values
          StandardOpenOption.CREATE_NEW,
          StandardOpenOption.WRITE,
          StandardOpenOption.SYNC);
    }
    configurationChannel =
        FileChannel.open(confFile.toPath(), StandardOpenOption.READ, StandardOpenOption.WRITE);
  }

  /**
   * Stores the current server term.
   *
   * @param term The current server term.
   */
  public synchronized void storeTerm(final long term) {
    log.trace("Store term {}", term);
    serializer.writeTerm(term);
    writeToFile(serializer.metaByteBuffer(), metaFileChannel, false);
  }

  /**
   * Loads the stored server term.
   *
   * @return The stored server term.
   */
  public synchronized long loadTerm() {
    readMetaFromFile();
    return serializer.readTerm();
  }

  /**
   * Stores the last voted server.
   *
   * @param vote The server vote.
   */
  public synchronized void storeVote(final MemberId vote) {
    log.trace("Store vote {}", vote);
    final String id = vote == null ? null : vote.id();
    serializer.writeVotedFor(id);
    writeToFile(serializer.metaByteBuffer(), metaFileChannel, false);
  }

  /**
   * Loads the last vote for the server.
   *
   * @return The last vote for the server.
   */
  public synchronized MemberId loadVote() {
    readMetaFromFile();
    final String id = serializer.readVotedFor();
    return id.isEmpty() ? null : MemberId.from(id);
  }

  @Override
  public synchronized void storeLastFlushedIndex(final long index) {
    // a pending async value must never overtake this store, even when this one is skipped below
    discardPendingIndex();
    failIfAsyncWriteFailed();
    writeLastFlushedIndex(index);
  }

  /**
   * Requests an update of the last flushed index without waiting for the file write. Must only be
   * used after the journal flush of {@code index} returned. Consecutive requests are coalesced, the
   * latest value wins. A failed write is rethrown on the next store call.
   */
  @Override
  public void storeLastFlushedIndexAsync(final long index) {
    failIfAsyncWriteFailed();
    synchronized (slotLock) {
      if (closed) {
        // close already persisted the latest requested value; a lower persisted value is safe
        return;
      }
      pendingIndex = index;
      hasPendingIndex = true;
      if (drainScheduled) {
        return;
      }
      drainScheduled = true;
      if (asyncWriter == null) {
        asyncWriter =
            Executors.newSingleThreadExecutor(
                new ThreadFactoryBuilder()
                    .setNameFormat("raft-partition-" + partitionId + "-meta-flush")
                    .setDaemon(true)
                    .build());
      }
      asyncWriter.execute(this::drainPendingIndex);
    }
  }

  private void drainPendingIndex() {
    while (true) {
      synchronized (this) {
        final long index;
        synchronized (slotLock) {
          if (!hasPendingIndex) {
            drainScheduled = false;
            return;
          }
          index = pendingIndex;
          hasPendingIndex = false;
        }

        try {
          writeLastFlushedIndex(index);
        } catch (final Exception e) {
          log.error("Failed to store last flushed index {} asynchronously", index, e);
          asyncFailure = e;
          synchronized (slotLock) {
            drainScheduled = false;
          }
          return;
        }
      }
    }
  }

  private void discardPendingIndex() {
    synchronized (slotLock) {
      hasPendingIndex = false;
    }
  }

  private void failIfAsyncWriteFailed() {
    final var failure = asyncFailure;
    if (failure != null) {
      throw new StorageException("Failed to store last flushed index asynchronously", failure);
    }
  }

  private void writeLastFlushedIndex(final long index) {
    if (index == lastFlushedIndex) {
      log.trace("Skip storing same last flushed index {}", index);
      return;
    }

    log.trace("Store last flushed index {} and commitIndex {}", index, commitIndex);
    try (final var ignored = metrics.observeLastFlushedIndexUpdate()) {
      serializer.writeLastFlushedIndex(index);
      writeToFile(serializer.metaByteBuffer(), metaFileChannel, false);
      lastFlushedIndex = index;
    }
  }

  @Override
  public long loadLastFlushedIndex() {
    return lastFlushedIndex;
  }

  @Override
  public void resetLastFlushedIndex() {
    storeLastFlushedIndex(MetaEncoder.lastFlushedIndexNullValue());
  }

  @Override
  public boolean hasLastFlushedIndex() {
    return lastFlushedIndex != MetaEncoder.lastFlushedIndexNullValue();
  }

  public void storeCommitIndex(final long index) {
    Preconditions.checkArgument(index >= 0, "commit index must be >= 0");
    if (index == commitIndex) {
      log.trace("Skip storing same last flushed commit index {}", index);
      return;
    }
    commitIndex = index;
    // the commitIndex is only stored in the ByteBuffer, it will be flushed when "lastFlushedIndex"
    // is updated.
    serializer.writeCommitIndex(index);
  }

  public boolean hasCommitIndex() {
    return commitIndex != MetaEncoder.commitIndexNullValue();
  }

  /**
   * @return the currentCommitIndex or -1 if not present. Check with {@link
   *     MetaStore#hasCommitIndex()} if it is initialized.
   */
  public long commitIndex() {
    return commitIndex;
  }

  /**
   * Stores the current cluster configuration.
   *
   * @param configuration The current cluster configuration.
   */
  public synchronized void storeConfiguration(final Configuration configuration) {
    log.trace("Store configuration {}", configuration);
    final var buffer = serializer.writeConfiguration(configuration);
    writeToFile(buffer, configurationChannel, true);
  }

  /**
   * Loads the current cluster configuration.
   *
   * @return The current cluster configuration.
   */
  public synchronized Configuration loadConfiguration() {
    try {
      configurationChannel.position(0);
      final ByteBuffer buffer = ByteBuffer.allocate((int) confFile.length());
      configurationChannel.read(buffer);
      buffer.position(0);
      return serializer.readConfiguration(buffer);
    } catch (final IOException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public synchronized void close() {
    final ExecutorService writer;
    final boolean hadPending;
    final long pending;
    synchronized (slotLock) {
      closed = true;
      hadPending = hasPendingIndex;
      pending = pendingIndex;
      hasPendingIndex = false;
      writer = asyncWriter;
    }
    if (writer != null) {
      // a drain task still queued finds nothing pending and ends
      writer.shutdown();
    }

    try {
      // write to disk what's present in the buffer, as it may have not yet been written.
      try {
        if (hadPending && asyncFailure == null) {
          serializer.writeLastFlushedIndex(pending);
          lastFlushedIndex = pending;
        }
        writeToFile(serializer.metaByteBuffer(), metaFileChannel, true);
      } catch (final Exception e) {
        log.warn("Failed to write to metaStore before closing", e);
      }
      metaFileChannel.close();
      configurationChannel.close();
    } catch (final IOException e) {
      log.warn("Failed to close metastore", e);
    }
  }

  @Override
  public String toString() {
    return toStringHelper(this).toString();
  }

  /**
   * Overwrite the file with contents with the current schema
   *
   * @param record with the information to overwrite
   */
  private void initializeMetaBuffer(final MetaStoreRecord record) {
    serializer.writeRecord(record);
    writeToFile(serializer.metaByteBuffer(), metaFileChannel, true);
  }

  /** Load the Meta file into metaBuffer */
  private void readMetaFromFile() {
    try {
      metaFileChannel.read(serializer.metaByteBuffer(), 0);
    } catch (final IOException e) {
      throw new StorageException(e);
    }
  }

  private void writeToFile(final ByteBuffer buffer, final FileChannel file, final boolean force) {
    try {
      buffer.position(0);
      file.write(buffer, 0);
      if (force) {
        file.force(true);
      }
    } catch (final IOException e) {
      throw new StorageException(e);
    }
  }
}
