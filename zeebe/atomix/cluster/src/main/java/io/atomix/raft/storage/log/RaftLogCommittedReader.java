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

import io.atomix.raft.storage.serializer.RaftEntrySBESerializer;
import io.atomix.raft.storage.serializer.RaftEntrySerializer;
import io.camunda.zeebe.journal.CorruptedJournalException;
import java.util.NoSuchElementException;

/** Raft log reader that reads only committed entries. */
public class RaftLogCommittedReader implements RaftLogReader {
  private final RaftLog log;
  private final RaftEntrySerializer serializer = new RaftEntrySBESerializer();

  // NOTE: nextIndex is only used if the reader is in commit mode, hence why it's not subject to
  // inconsistencies when the log is truncated/compacted/etc.
  private long nextIndex;
  private final RaftLogUncommittedReader reader;
  private final boolean failOnMissingCommittedEntry;

  RaftLogCommittedReader(final RaftLog log, final RaftLogUncommittedReader reader) {
    this(log, reader, false);
  }

  /**
   * @param failOnMissingCommittedEntry whether {@link #hasNext()} throws a {@link
   *     CorruptedJournalException} when the log cannot return an entry at or below the commit
   *     index, instead of reporting the end of the log
   */
  RaftLogCommittedReader(
      final RaftLog log,
      final RaftLogUncommittedReader reader,
      final boolean failOnMissingCommittedEntry) {
    this.log = log;
    this.reader = reader;
    this.failOnMissingCommittedEntry = failOnMissingCommittedEntry;
    nextIndex = log.getFirstIndex();
  }

  @Override
  public boolean hasNext() {
    final long commitIndex = log.getCommitIndex();
    if (nextIndex > commitIndex) {
      return false;
    }
    if (reader.hasNext()) {
      return true;
    }
    if (failOnMissingCommittedEntry && isMissingFromLog(nextIndex)) {
      throw missingCommittedEntry(nextIndex, commitIndex);
    }
    return false;
  }

  /**
   * Tells a missing entry apart from a reader that has nothing to read because it was left behind:
   * the segment it reads was compacted away, or the log was reset for a snapshot. Only a new reader
   * shows which one it is, and it must not move this reader, which stays where it is until it is
   * sought again.
   *
   * <p>The new reader stops before the index if the log ends there, and after it if the index is
   * compacted. The log can also be compacted or reset between the calls here, which is why the
   * first index is read last: it is then already past the index. It is read without the journal
   * lock, and is 0 while a reset has removed the segments but not yet added the new first one.
   *
   * <p>A reset may also start the log at the commit index, as a follower does before it appends the
   * entries from there on. The log is then empty at an index that is committed, which only the last
   * index tells apart from an entry that was appended and cannot be read back.
   */
  private boolean isMissingFromLog(final long index) {
    try (final var probe = log.openUncommittedReader()) {
      if (probe.seek(index) > index || probe.hasNext()) {
        return false;
      }
      final long firstIndex = log.getFirstIndex();
      return firstIndex > 0 && firstIndex <= index && index <= log.getLastIndex();
    }
  }

  /**
   * A seek to an ASQN reads on until it finds an entry with a higher one, and takes a missing entry
   * for the end of the log, which leaves this reader at an earlier entry than the one asked for.
   * Reads the same entries again to tell the two apart.
   *
   * <p>The reader that does so is itself left behind when the log is compacted or reset while it
   * reads, so where it stops is looked up once more with a new reader.
   */
  private void failIfSeekStoppedAtMissingEntry(final long asqn, final long commitIndex) {
    try (final var probe = log.openUncommittedReader()) {
      long index = probe.seek(nextIndex);
      while (index <= commitIndex) {
        if (!probe.hasNext()) {
          if (isMissingFromLog(index)) {
            throw missingCommittedEntry(index, commitIndex);
          }
          return;
        }
        final var entry = probe.next();
        if (entry.isApplicationEntry() && entry.getApplicationEntry().lowestPosition() > asqn) {
          return;
        }
        index = entry.index() + 1;
      }
    } catch (final NoSuchElementException e) {
      // compacted or reset between hasNext and next, so nothing is missing
    }
  }

  private static CorruptedJournalException missingCommittedEntry(
      final long index, final long commitIndex) {
    return new CorruptedJournalException(
        "Expected to read the committed entry at index %d (commit index %d), but the log has no entry at that index"
            .formatted(index, commitIndex));
  }

  @Override
  public IndexedRaftLogEntry next() {
    if (!hasNext()) {
      throw new NoSuchElementException();
    }

    final IndexedRaftLogEntry entry = reader.next();

    nextIndex = entry.index() + 1;
    return entry;
  }

  public long reset() {
    nextIndex = reader.reset();
    return nextIndex;
  }

  public long seek(final long index) {
    // allow seeking one past the commit index to simulate being at the end of the log
    final long upperBoundIndex = log.getCommitIndex() + 1;
    final long boundIndex = Math.min(index, upperBoundIndex);

    nextIndex = reader.seek(boundIndex);
    return nextIndex;
  }

  public long seekToLast() {
    seek(log.getCommitIndex());
    return nextIndex;
  }

  public long seekToAsqn(final long asqn) {
    final long commitIndex = log.getCommitIndex();
    nextIndex = reader.seekToAsqn(asqn, commitIndex);
    if (failOnMissingCommittedEntry) {
      failIfSeekStoppedAtMissingEntry(asqn, commitIndex);
    }
    return nextIndex;
  }

  @Override
  public long bytesUntilEnd() {
    return reader.bytesUntilEnd();
  }

  @Override
  public void close() {
    reader.close();
  }
}
