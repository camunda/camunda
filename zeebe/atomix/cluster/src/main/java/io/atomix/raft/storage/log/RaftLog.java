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
import io.atomix.raft.storage.log.entry.RaftLogEntry;
import io.atomix.raft.storage.serializer.RaftEntrySBESerializer;
import io.atomix.raft.storage.serializer.RaftEntrySerializer;
import io.camunda.zeebe.journal.CheckedJournalException.FlushException;
import io.camunda.zeebe.journal.Journal;
import io.camunda.zeebe.journal.JournalRecord;
import io.camunda.zeebe.journal.SegmentInfo;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.Closeable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
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

  // The highest commit index a leader announced to this member, which can be ahead of
  // commitIndex: a follower only advances its commit index once the records it acknowledges are
  // durable, but it must never delete records which the leader already declared committed, not even
  // while the flush covering them is still in progress. Only used to guard truncations;
  // commitIndex keeps its stricter meaning of "committed and durable" for committed readers.
  private long announcedCommitIndex;

  // Counts how often records were removed from this log, see #getTruncationGeneration().
  private long truncationGeneration;

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

  /**
   * Announces that a leader considers all entries up to the given index committed. Unlike {@link
   * #setCommitIndex(long)}, this may be called before those entries are durable, and it does not
   * make them visible to committed readers. It only ensures that {@link #reset(long)} and {@link
   * #deleteAfter(long)} keep refusing to delete entries which a leader already declared committed,
   * even while the flush covering them is still in progress. The announced index never decreases.
   *
   * <p>Only called on the thread which appends to and truncates the log, i.e. the Raft thread.
   *
   * @param index the highest index a leader declared committed
   */
  public void announceCommitIndex(final long index) {
    announcedCommitIndex = Math.max(announcedCommitIndex, index);
  }

  /**
   * Returns the highest commit index announced by a leader. This can be ahead of {@link
   * #getCommitIndex()}, which only covers entries which are also durable.
   *
   * @see #announceCommitIndex(long)
   */
  public long getAnnouncedCommitIndex() {
    return announcedCommitIndex;
  }

  public long getFirstIndex() {
    return journal.getFirstIndex();
  }

  public long getLastIndex() {
    return journal.getLastIndex();
  }

  /**
   * Returns the index of the last record which is known to be flushed to persistent storage. See
   * {@link Journal#getLastFlushedIndex()}.
   */
  public long getLastFlushedIndex() {
    return journal.getLastFlushedIndex();
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

  /**
   * Returns how often records were removed from this log, i.e. how often {@link #reset(long)} or
   * {@link #deleteAfter(long)} was called. Operations which cover specific records but complete
   * asynchronously can capture this before they start and compare it afterwards, to detect that the
   * records they cover may have ceased to exist. Comparing indexes instead is not enough: a
   * truncation followed by new appends can restore the same last index with different records.
   *
   * <p>Only read and updated on the thread which appends to and truncates the log, i.e. the Raft
   * thread.
   */
  public long getTruncationGeneration() {
    return truncationGeneration;
  }

  public void reset(final long index) {
    final long committedIndex = Math.max(commitIndex, announcedCommitIndex);
    if (index < committedIndex) {
      throw new IllegalStateException(
          String.format(
              """
               Expected to delete index after %d, but it is lower than the commit index %d.\
               Deleting committed entries can lead to inconsistencies and is prohibited.\
               This can happen if a quorum of nodes has experienced data loss and became leader.\
               This situation probably requires manual intervention to resume operations""",
              index, committedIndex));
    }
    // pending flush results for the deleted records must fail, they can never become durable
    flusher.onLogTruncation(index - 1);
    truncationGeneration++;
    journal.reset(index);
    lastAppendedEntry = null;
  }

  public void deleteAfter(final long index) throws FlushException {
    final long committedIndex = Math.max(commitIndex, announcedCommitIndex);
    if (index < committedIndex) {
      throw new IllegalStateException(
          String.format(
              """
                 Expected to delete index after %d, but it is lower than the commit index %d.\
                 Deleting committed entries can lead to inconsistencies and is prohibited.\
               This can happen if a quorum of nodes has experienced data loss and became leader.\
               This situation probably requires manual intervention to resume operations""",
              index, committedIndex));
    }
    // pending flush results for the deleted records must fail, they can never become durable
    flusher.onLogTruncation(index);
    truncationGeneration++;
    journal.deleteAfter(index);
    lastAppendedEntry = null;

    // we have to flush right away, bypassing the configured flush strategy, to ensure the
    // truncation itself is durable: the journal already lowered its flush watermark, so records
    // beyond it would otherwise be treated as valid partial writes on restart even though they
    // were already acknowledged based on an earlier flush
    forceFlush();
  }

  /**
   * Requests that the journal is durable at least up to the given index, using the configured
   * flushing strategy. For guarantees, refer to the configured {@link RaftLogFlusher}.
   *
   * @param index the index up to which durability is requested
   * @return a future which completes once the configured flusher's durability guarantee holds for
   *     the given index; it may complete on a different thread
   */
  public CompletableFuture<Void> flush(final long index) {
    return flusher.flush(journal, index);
  }

  /**
   * Same as {@link #flush(long)}, but blocks until the flush result is completed.
   *
   * @param index the index up to which durability is requested
   * @throws FlushException if the flush failed
   */
  public void flushSync(final long index) throws FlushException {
    try {
      flush(index).join();
    } catch (final CompletionException e) {
      final var cause = e.getCause();
      if (cause instanceof final FlushException flushException) {
        throw flushException;
      }
      if (cause instanceof final RuntimeException runtimeException) {
        throw runtimeException;
      }
      throw new FlushException("Flush failed for an unexpected reason", cause);
    }
  }

  /**
   * Flushes the underlying journal in a blocking, synchronous way. When this returns, it is
   * guaranteed that any appended data since the last flush is persisted on disk.
   *
   * <p>NOTE: this bypasses the configured flushing strategy, and is meant to be used when certain
   * guarantees are required.
   */
  public void forceFlush() throws FlushException {
    journal.flush();
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

  public SegmentInfo getTailSegments(final long index) {
    return journal.getTailSegments(index);
  }
}
