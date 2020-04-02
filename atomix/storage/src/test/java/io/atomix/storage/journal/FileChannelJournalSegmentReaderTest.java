/*
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

import io.atomix.storage.StorageLevel;
import io.atomix.storage.journal.JournalReader.Mode;
import io.atomix.storage.journal.index.SparseJournalIndex;
import io.atomix.utils.serializer.Namespace;
import java.io.File;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class FileChannelJournalSegmentReaderTest {
  private static final Namespace NAMESPACE = Namespace.builder().register(Integer.class).build();
  private static final Integer ENTRY = 1;
  private static final int ENTRY_SIZE =
      NAMESPACE.serialize(ENTRY).length + Long.BYTES; // padding for checksum;

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  private File directory;

  @Before
  public void setUp() throws IOException {
    directory = temporaryFolder.newFolder();
  }

  @Test
  public void shouldResetBackwardsCorrectlyWhenUsingSameIndex() {
    // given
    final int entriesPerSegment = 7;
    final SparseJournalIndex journalIndex = new SparseJournalIndex(5);
    try (final SegmentedJournal<Integer> journal = createJournal(entriesPerSegment, journalIndex)) {
      final SegmentedJournalWriter<Integer> writer = journal.writer();
      final SegmentedJournalReader<Integer> reader = journal.openReader(1, Mode.ALL);

      for (int i = 1; i <= entriesPerSegment; i++) {
        writer.append(ENTRY);
      }

      writer.append(ENTRY);
      final Indexed<Integer> previousEntry = writer.append(ENTRY);
      final Indexed<Integer> currentEntry = writer.append(ENTRY);

      reader.reset(currentEntry.index());
      assertTrue(reader.hasNext());
      assertEquals(currentEntry.index(), reader.next().index());

      reader.reset(previousEntry.index());
      assertTrue(reader.hasNext());
      assertEquals(previousEntry.index(), reader.next().index());
    }
  }

  private SegmentedJournal<Integer> createJournal(
      final int entriesPerSegment, final SparseJournalIndex journalIndex) {
    final int maxSegmentSize = (entriesPerSegment * ENTRY_SIZE) + JournalSegmentDescriptor.BYTES;
    return SegmentedJournal.<Integer>builder()
        .withName("test")
        .withDirectory(directory)
        .withNamespace(NAMESPACE)
        .withStorageLevel(StorageLevel.DISK)
        .withMaxSegmentSize(maxSegmentSize)
        .withJournalIndexFactory(() -> journalIndex)
        .build();
  }
}
