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
package io.atomix.raft.storage.log;

import static io.camunda.zeebe.journal.file.SegmentedJournal.ASQN_IGNORE;

import io.atomix.raft.protocol.PersistedRaftRecord;
import io.atomix.raft.protocol.ReplicatableJournalRecord;
import io.atomix.raft.storage.log.RaftLogFlusher.Factory;
import io.atomix.raft.storage.log.entry.RaftLogEntry;
import io.atomix.raft.storage.serializer.RaftEntrySBESerializer;
import io.atomix.raft.storage.serializer.RaftEntrySerializer;
import io.camunda.zeebe.journal.CheckedJournalException.FlushException;
import io.camunda.zeebe.journal.Journal;
import io.camunda.zeebe.journal.JournalRecord;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.Closeable;
import java.nio.file.Path;
import java.util.SortedMap;
import org.agrona.CloseHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Raft log. */
public final class RaftLog implements Closeable {
  private static final Logger LOGGER = LoggerFactory.getLogger(RaftLog.class);

  private final RaftEntrySerializer serializer = new RaftEntrySBESerializer();
  private final Journal journal;
  private final RaftLogFlusher flusher;
  private IndexedRaftLogEntry lastAppendedEntry;
  private volatile long commitIndex;

  RaftLog(final Journal journal, final RaftLogFlusher flusher) {
    this.journal = journal;
    this.flusher = flusher;
  }

  /**
   * Returns a new Raft log builder.
   *
   * @return A new Raft log builder.
   */
  public static RaftLogBuilder builder(final MeterRegistry meterRegistry) {
    return new RaftLogBuilder(meterRegistry);
  }

  /**
   * Opens the reader that can read both committed and uncommitted entries.
   *
   * @return the reader
   */
  public RaftLogReader openUncommittedReader() {
    return new RaftLogUncommittedReader(journal.openReader());
  }

  /**
   * Opens the reader that can only read committed entries.
   *
   * @return the reader
   */
  public RaftLogReader openCommittedReader() {
    return new RaftLogCommittedReader(this, new RaftLogUncommittedReader(journal.openReader()));
  }

  public boolean isOpen() {
    return journal.isOpen();
  }

  /**
   * Compacts the journal up to the given index.
   *
   * <p>The semantics of compaction are not specified by this interface.
   *
   * @param index The index up to which to compact the journal.
   * @return true if anything was deleted, false otherwise
   */
  public boolean deleteUntil(final long index) {
    return journal.deleteUntil(index);
  }

  /**
   * Returns the Raft log commit index.
   *
   * @return The Raft log commit index.
   */
  public long getCommitIndex() {
    return commitIndex;
  }

  /**
   * Commits entries up to the given index.
   *
   * @param index The index up to which to commit entries.
   */
  public void setCommitIndex(final long index) {
    commitIndex = index;
  }

  public boolean flushesDirectly() {
    return flusher.isDirect();
  }

  public long getFirstIndex() {
    return journal.getFirstIndex();
  }

  public long getLastIndex() {
    return journal.getLastIndex();
  }

  public IndexedRaftLogEntry getLastEntry() {
    if (lastAppendedEntry == null) {
      readLastEntry();
    }

    return lastAppendedEntry;
  }

  private void readLastEntry() {
    try (final var reader = openUncommittedReader()) {
      reader.seekToLast();
      if (reader.hasNext()) {
        lastAppendedEntry = reader.next();
      }
    }
  }

  public boolean isEmpty() {
    return journal.isEmpty();
  }

  public IndexedRaftLogEntry append(final RaftLogEntry entry) {
    final JournalRecord journalRecord =
        journal.append(
            entry.getLowestAsqn().orElse(ASQN_IGNORE),
            entry.entry().toSerializable(entry.term(), serializer));

    lastAppendedEntry = new IndexedRaftLogEntryImpl(entry.term(), entry.entry(), journalRecord);
    return lastAppendedEntry;
  }

  public IndexedRaftLogEntry append(final PersistedRaftRecord entry) {
    journal.append(entry);

    final RaftLogEntry raftEntry = serializer.readRaftLogEntry(entry.data());
    lastAppendedEntry = new IndexedRaftLogEntryImpl(entry.term(), raftEntry.entry(), entry);
    return lastAppendedEntry;
  }

  public IndexedRaftLogEntry append(final ReplicatableJournalRecord entry) {
    final var writtenRecord = journal.append(entry.checksum(), entry.serializedJournalRecord());

    final RaftLogEntry raftEntry = serializer.readRaftLogEntry(writtenRecord.data());
    lastAppendedEntry = new IndexedRaftLogEntryImpl(entry.term(), raftEntry.entry(), writtenRecord);
    return lastAppendedEntry;
  }

  public void reset(final long index) {
    if (index < commitIndex) {
      throw new IllegalStateException(
          String.format(
              """
               Expected to delete index after %d, but it is lower than the commit index %d.\
               Deleting committed entries can lead to inconsistencies and is prohibited.\
               This can happen if a quorum of nodes has experienced data loss and became leader.\
               This situation probably requires manual intervention to resume operations""",
              index, commitIndex));
    }
    journal.reset(index);
    lastAppendedEntry = null;
  }

  public void deleteAfter(final long index) throws FlushException {
    if (index < commitIndex) {
      throw new IllegalStateException(
          String.format(
              """
                 Expected to delete index after %d, but it is lower than the commit index %d.\
                 Deleting committed entries can lead to inconsistencies and is prohibited.\
               This can happen if a quorum of nodes has experienced data loss and became leader.\
               This situation probably requires manual intervention to resume operations""",
              index, commitIndex));
    }
    journal.deleteAfter(index);
    lastAppendedEntry = null;

    // we have to flush here to ensure the truncated log is represented properly
    flush();
  }

  /**
   * Flushes the underlying journal using the configured flushing strategy. For guarantees, refer to
   * the configured {@link RaftLogFlusher}.
   */
  public void flush() throws FlushException {
    flusher.flush(journal);
  }

  /**
   * Flushes the underlying journal in a blocking, synchronous way. When this returns, it is
   * guaranteed that any appended data since the last flush is persisted on disk.
   *
   * <p>NOTE: this bypasses the configured flushing strategy, and is meant to be used when certain
   * guarantees are required.
   */
  public void forceFlush() throws FlushException {
    Factory.DIRECT.flush(journal);
  }

  @Override
  public void close() {
    CloseHelper.closeAll(
        error -> LOGGER.warn("Unexpected error while closing the Raft log", error),
        journal,
        flusher);
  }

  @Override
  public String toString() {
    return "RaftLog{"
        + "journal="
        + journal
        + ", serializer="
        + serializer
        + ", lastAppendedEntry="
        + lastAppendedEntry
        + ", commitIndex="
        + commitIndex
        + '}';
  }

  public SortedMap<Long, Path> getTailSegments(final long index) {
    return journal.getTailSegments(index);
  }
}
