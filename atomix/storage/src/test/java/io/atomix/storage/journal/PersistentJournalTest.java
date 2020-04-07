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
package io.atomix.storage.journal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Persistent journal test base. */
public abstract class PersistentJournalTest extends AbstractJournalTest {
  protected PersistentJournalTest(final int maxSegmentSize, final int cacheSize) {
    super(maxSegmentSize, cacheSize);
  }

  /** Tests reading from a compacted journal. */
  @Test
  public void testCompactAndRecover() throws Exception {

    // Write three segments to the journal.
    JournalWriter<TestEntry> writer = journal.writer();
    for (int i = 0; i < entriesPerSegment * 3; i++) {
      writer.append(ENTRY);
    }

    // Commit the entries and compact the first segment.
    writer.commit(entriesPerSegment * 3);
    journal.compact(entriesPerSegment + 1);

    // Close the journal.
    journal.close();

    // Reopen the journal and create a reader.
    journal = createJournal();
    writer = journal.writer();
    final JournalReader<TestEntry> reader = journal.openReader(1, JournalReader.Mode.COMMITS);
    writer.append(ENTRY);
    writer.append(ENTRY);
    writer.commit(entriesPerSegment * 3);

    // Ensure the reader starts at the first physical index in the journal.
    assertEquals(entriesPerSegment + 1, reader.getNextIndex());
    assertEquals(reader.getFirstIndex(), reader.getNextIndex());
    assertTrue(reader.hasNext());
    assertEquals(entriesPerSegment + 1, reader.getNextIndex());
    assertEquals(reader.getFirstIndex(), reader.getNextIndex());
    assertEquals(entriesPerSegment + 1, reader.next().index());
  }
}
